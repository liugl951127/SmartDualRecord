package com.minimax.dualrecord.scheduled;

import com.minimax.dualrecord.domain.Business;
import com.minimax.dualrecord.domain.enums.RecordingState;
import com.minimax.dualrecord.repository.BusinessRepository;
import com.minimax.dualrecord.repository.EventLogRepository;
import com.minimax.dualrecord.domain.EventLog;
import com.minimax.dualrecord.statemachine.RecordingStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 孤儿业务探测器 · 替代 Saga 补偿的兜底机制
 *
 * <h3>为什么需要这个</h3>
 * 之前用 Saga 时，失败会显式回滚到 ROLLED_BACK 状态。
 * 现在用 @Transactional，事务回滚后业务停留在"上一个成功状态"。
 * 如果系统在 RECORDING 状态挂了 / 客户断电 / 录像中断，业务会永远卡在 RECORDING。
 *
 * <h3>职责</h3>
 * 定期扫描超过阈值时间停留在 RECORDING 状态的业务，自动标 FAILED。
 * 走正常的状态机转换（private transitionState），写事件日志留痕。
 *
 * <h3>触发频率</h3>
 * 默认每 5 分钟扫一次，30 分钟以上的孤儿业务标 FAILED。
 * 可通过 application.yml 调整。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleBusinessDetector {

    private final BusinessRepository businessRepository;
    private final EventLogRepository eventLogRepository;

    @Value("${dual-record.scheduler.stale-threshold-minutes:30}")
    private int staleThresholdMinutes;

    @Value("${dual-record.scheduler.enabled:true}")
    private boolean enabled;

    /**
     * 每 5 分钟扫一次（可配置）
     */
    @Scheduled(fixedDelayString = "${dual-record.scheduler.scan-interval-ms:300000}")
    public void scanStaleBusinesses() {
        if (!enabled) {
            return;
        }
        try {
            int failed = doScan();
            if (failed > 0) {
                log.warn("孤儿业务扫描完成: 共处理 {} 个超 {} 分钟停留在 RECORDING 的业务",
                        failed, staleThresholdMinutes);
            } else {
                log.debug("孤儿业务扫描: 无异常");
            }
        } catch (Exception e) {
            log.error("孤儿业务扫描失败（不影响主流程）", e);
        }
    }

    @Transactional
    public int doScan() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(staleThresholdMinutes);

        // 找停留在 RECORDING 状态超过阈值的业务
        List<Business> stale = businessRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Business>()
                        .eq("state", RecordingState.RECORDING.name())
                        .eq("deleted", 0)
                        .lt("updated_at", threshold));

        int processed = 0;
        for (Business business : stale) {
            try {
                markAsFailed(business, "STALE_RECORDING: exceeded " + staleThresholdMinutes + "min");
                processed++;
            } catch (Exception e) {
                log.error("标 FAILED 失败: businessId={}", business.getBusinessId(), e);
            }
        }
        return processed;
    }

    /**
     * 把一个业务标 FAILED（走状态机校验 + 写事件）
     */
    private void markAsFailed(Business business, String reason) {
        RecordingState fromState = business.getState();
        // 状态机校验：RECORDING → FAILED 是合法的
        RecordingStateMachine.transition(fromState, RecordingState.FAILED);

        business.setState(RecordingState.FAILED);
        business.setUpdatedAt(LocalDateTime.now());
        businessRepository.updateById(business);

        EventLog event = new EventLog();
        event.setBusinessId(business.getBusinessId());
        event.setEventType("STALE_DETECTION");
        event.setFromState(fromState.name());
        event.setToState(RecordingState.FAILED.name());
        event.setActorId("SYSTEM");
        event.setActorType("SYSTEM");
        event.setEventData(String.format("{\"reason\":\"%s\"}", reason));
        event.setCreatedAt(LocalDateTime.now());
        eventLogRepository.insert(event);

        log.warn("孤儿业务已标 FAILED: businessId={}, updated_at={}",
                business.getBusinessId(), business.getUpdatedAt());
    }
}
