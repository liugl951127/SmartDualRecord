package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.Business;
import com.minimax.dualrecord.domain.EventLog;
import com.minimax.dualrecord.domain.enums.BusinessType;
import com.minimax.dualrecord.domain.enums.Channel;
import com.minimax.dualrecord.domain.enums.RecordingState;
import com.minimax.dualrecord.repository.BusinessRepository;
import com.minimax.dualrecord.repository.EventLogRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 犹豫期 3 次智能回访测试
 *
 * 覆盖: scheduleThreeFollowUps / executeDueFollowUps / customerReplyWantsToCancel
 */
class FollowUpServiceTest {

    @Test
    void testScheduleThreeFollowUps_Writes3Events() {
        BusinessRepository bizRepo = mock(BusinessRepository.class);
        EventLogRepository evtRepo = mock(EventLogRepository.class);
        when(bizRepo.selectOne(any(QueryWrapper.class))).thenReturn(newBusiness());

        FollowUpService svc = new FollowUpService(bizRepo, evtRepo);
        svc.scheduleThreeFollowUps("BNK-001");

        // 应调用 insert 3 次 (D+1 / D+7 / D+14)
        verify(evtRepo, times(3)).insert(any(EventLog.class));
    }

    @Test
    void testSchedule_BusinessNotFound_NoCrash() {
        BusinessRepository bizRepo = mock(BusinessRepository.class);
        EventLogRepository evtRepo = mock(EventLogRepository.class);
        when(bizRepo.selectOne(any(QueryWrapper.class))).thenReturn(null);

        FollowUpService svc = new FollowUpService(bizRepo, evtRepo);
        svc.scheduleThreeFollowUps("BNK-FAKE-0001");
        verify(evtRepo, never()).insert(any(EventLog.class));
    }

    @Test
    void testExecuteDueFollowUps_OnlyFutureSkipped() {
        BusinessRepository bizRepo = mock(BusinessRepository.class);
        EventLogRepository evtRepo = mock(EventLogRepository.class);

        // 1 个已排程事件, 但 created_at 是现在 (D+1 还在未来)
        EventLog sched = new EventLog();
        sched.setBusinessId("BNK-001");
        sched.setEventType("SCHEDULED_FOLLOW_UP");
        sched.setCreatedAt(LocalDateTime.now());  // 现在排程
        sched.setEventData("{\"phase\":\"D+1\",\"day\":1}");

        when(evtRepo.selectList(any(QueryWrapper.class))).thenReturn(List.of(sched));
        when(evtRepo.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        FollowUpService svc = new FollowUpService(bizRepo, evtRepo);
        int executed = svc.executeDueFollowUps();
        assertEquals(0, executed, "D+1 还在未来, 不应执行");
    }

    @Test
    void testExecuteDueFollowUps_Overdue_Executes() {
        BusinessRepository bizRepo = mock(BusinessRepository.class);
        EventLogRepository evtRepo = mock(EventLogRepository.class);

        // 排程 2 天前 → D+1 已到期
        EventLog sched = new EventLog();
        sched.setBusinessId("BNK-001");
        sched.setEventType("SCHEDULED_FOLLOW_UP");
        sched.setCreatedAt(LocalDateTime.now().minusDays(2));
        sched.setEventData("{\"phase\":\"D+1\",\"day\":1}");

        when(evtRepo.selectList(any(QueryWrapper.class))).thenReturn(List.of(sched));
        when(evtRepo.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        FollowUpService svc = new FollowUpService(bizRepo, evtRepo);
        int executed = svc.executeDueFollowUps();
        assertEquals(1, executed, "D+1 已到期, 应执行");
    }

    @Test
    void testCustomerReplyWantsToCancel_LogsEvent() {
        BusinessRepository bizRepo = mock(BusinessRepository.class);
        EventLogRepository evtRepo = mock(EventLogRepository.class);

        FollowUpService svc = new FollowUpService(bizRepo, evtRepo);
        svc.customerReplyWantsToCancel("BNK-001", "我想退保");

        verify(evtRepo, times(1)).insert(any(EventLog.class));
    }

    private Business newBusiness() {
        Business b = new Business();
        b.setBusinessId("BNK-001");
        b.setBusinessType(BusinessType.WEALTH);
        b.setProductId("BNK-FIN-001");
        b.setCustomerIdHash("c1");
        b.setSellerIdHash("s1");
        b.setChannel(Channel.OFFLINE);
        b.setState(RecordingState.ARCHIVED);
        b.setAmount(new BigDecimal("100"));
        b.setProductRiskLevel("R2");
        b.setCreatedAt(LocalDateTime.now());
        b.setUpdatedAt(LocalDateTime.now());
        b.setDeleted(0);
        return b;
    }
}
