package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.Business;
import com.minimax.dualrecord.domain.EventLog;
import com.minimax.dualrecord.repository.BusinessRepository;
import com.minimax.dualrecord.repository.EventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 犹豫期 3 次智能回访服务 (D+1 / D+7 / D+14)
 *
 * <h3>合规依据</h3>
 *  - 《保险销售行为可回溯管理暂行办法》第九条：犹豫期 15 天
 *  - 《金融机构产品适当性管理办法》第十七条：回访记录至少保存 10 年
 *  - 中保协自律规范：犹豫期内 3 次主动触达
 *
 * <h3>3 次回访内容</h3>
 *  - D+1：保单摘要推送 + 答疑入口（短信 / 微信 / APP 推送）
 *  - D+7：疑问询问 + 客户调研（"看不懂/想退保"自动转人工）
 *  - D+14：到期提醒 + 最后答疑（犹豫期最后 1 天，确认意向）
 *
 * <h3>触发方式</h3>
 *  - signAndArchive() 完成后立即入排
 *  - @Scheduled 定时任务每小时扫描一次"已到回访日"的任务
 *  - 客户"想退保"自动转人工 + 标记需要干预
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpService {

    private final BusinessRepository businessRepository;
    private final EventLogRepository eventLogRepository;

    /** 3 次回访节点：天数 → 触达类型 + 模板 */
    private static final Map<Integer, FollowUpTemplate> TEMPLATES = Map.of(
            1, new FollowUpTemplate("D+1", "保单摘要推送",
                    "您昨日购买的保险产品已进入 15 天犹豫期, 摘要已推送到您 APP, 有任何疑问可一键联系您的理财经理或我行客服。"),
            7, new FollowUpTemplate("D+7", "疑问询问",
                    "您购买的保险产品已过 7 天, 对条款有任何疑问吗? 若想退保请回复 1, 我行 24h 内联系您。"),
            14, new FollowUpTemplate("D+14", "到期提醒",
                    "您的犹豫期将于明天结束(还剩 1 天), 确认继续持有本保单? 若想退保, 明天 24:00 前可办, 工本费不超过 10 元。")
    );

    /**
     * 创建 3 个回访任务
     * 在 signAndArchive() 完成后调用
     */
    @Transactional(rollbackFor = Exception.class)
    public void scheduleThreeFollowUps(String businessId) {
        Business business = businessRepository.selectOne(
                new QueryWrapper<Business>().eq("business_id", businessId));
        if (business == null) {
            log.warn("业务不存在, 跳过回访排程: {}", businessId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<Integer, FollowUpTemplate> entry : TEMPLATES.entrySet()) {
            int day = entry.getKey();
            FollowUpTemplate tpl = entry.getValue();

            // 写一个 SCHEDULED_FOLLOW_UP 事件 (作为排程凭据)
            EventLog event = new EventLog();
            event.setBusinessId(businessId);
            event.setEventType("SCHEDULED_FOLLOW_UP");
            event.setFromState("ARCHIVED");
            event.setToState("ARCHIVED");
            event.setActorId("SYSTEM");
            event.setActorType("SYSTEM");
            event.setEventData(String.format(
                    "{\"phase\":\"%s\",\"day\":%d,\"scheduled_at\":\"%s\",\"template\":\"%s\"}",
                    tpl.phase(), day, now.plusDays(day), tpl.title()));
            event.setCreatedAt(now);
            eventLogRepository.insert(event);
        }

        log.info("已为业务 {} 排程 3 次智能回访 (D+1/D+7/D+14)", businessId);
    }

    /**
     * 扫描已到回访日的任务并执行
     * @return 实际触达的客户数
     */
    @Transactional(rollbackFor = Exception.class)
    public int executeDueFollowUps() {
        LocalDateTime now = LocalDateTime.now();
        int executed = 0;

        // 查所有 SCHEDULED_FOLLOW_UP 事件
        List<EventLog> scheduled = eventLogRepository.selectList(
                new QueryWrapper<EventLog>()
                        .eq("event_type", "SCHEDULED_FOLLOW_UP")
                        .orderByAsc("created_at"));

        for (EventLog evt : scheduled) {
            // 解析 scheduled_at 字段
            String data = evt.getEventData();
            int day = extractIntField(data, "day");
            if (day == 0) continue;

            // 排程时 now + day 天, 到了吗?
            LocalDateTime scheduledAt = evt.getCreatedAt().plusDays(day);
            if (now.isBefore(scheduledAt)) continue;

            // 检查是否已执行过
            boolean alreadyDone = eventLogRepository.selectCount(
                    new QueryWrapper<EventLog>()
                            .eq("business_id", evt.getBusinessId())
                            .eq("event_type", "FOLLOW_UP_EXECUTED")
                            .like("event_data", "\"phase\":\"" + extractStringField(data, "phase") + "\"")) > 0;
            if (alreadyDone) continue;

            // 执行触达 (生产: 调短信网关 / 微信模板 / APP 推送)
            executeOneFollowUp(evt.getBusinessId(), day, data);

            // 写执行事件
            EventLog done = new EventLog();
            done.setBusinessId(evt.getBusinessId());
            done.setEventType("FOLLOW_UP_EXECUTED");
            done.setFromState("ARCHIVED");
            done.setToState("ARCHIVED");
            done.setActorId("SYSTEM");
            done.setActorType("SYSTEM");
            done.setEventData(String.format(
                    "{\"phase\":\"%s\",\"day\":%d,\"executed_at\":\"%s\",\"channel\":\"%s\"}",
                    extractStringField(data, "phase"), day, now, evt.getBusinessId().startsWith("BNK") ? "WECHAT" : "SMS"));
            done.setCreatedAt(now);
            eventLogRepository.insert(done);

            executed++;
        }

        if (executed > 0) {
            log.info("执行了 {} 次智能回访", executed);
        }
        return executed;
    }

    /**
     * 客户主动回复"想退保" → 转人工 + 标记需干预
     */
    @Transactional(rollbackFor = Exception.class)
    public void customerReplyWantsToCancel(String businessId, String replyContent) {
        log.warn("客户想退保: businessId={}, reply={}", businessId, replyContent);
        EventLog event = new EventLog();
        event.setBusinessId(businessId);
        event.setEventType("CUSTOMER_WANTS_TO_CANCEL");
        event.setFromState("ARCHIVED");
        event.setToState("ARCHIVED");
        event.setActorId("CUSTOMER");
        event.setActorType("CUSTOMER");
        event.setEventData(String.format("{\"reply\":\"%s\",\"escalate_to\":\"HUMAN_AGENT\"}", replyContent));
        event.setCreatedAt(LocalDateTime.now());
        eventLogRepository.insert(event);
    }

    // ====================================================================
    // 内部方法
    // ====================================================================
    private void executeOneFollowUp(String businessId, int day, String scheduledData) {
        FollowUpTemplate tpl = TEMPLATES.get(day);
        if (tpl == null) return;
        // 沙箱: 写日志；生产: 调 SMS / 微信 / Push 网关
        log.info("[D+{} 回访] businessId={}, title={}, content={}",
                day, businessId, tpl.title(), tpl.content());
    }

    private int extractIntField(String json, String field) {
        try {
            String marker = "\"" + field + "\":";
            int idx = json.indexOf(marker);
            if (idx < 0) return 0;
            int end = json.indexOf(",", idx);
            if (end < 0) end = json.indexOf("}", idx);
            return Integer.parseInt(json.substring(idx + marker.length(), end).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private String extractStringField(String json, String field) {
        try {
            String marker = "\"" + field + "\":\"";
            int idx = json.indexOf(marker);
            if (idx < 0) return "";
            int end = json.indexOf("\"", idx + marker.length());
            return json.substring(idx + marker.length(), end);
        } catch (Exception e) {
            return "";
        }
    }

    private record FollowUpTemplate(String phase, String title, String content) {}
}
