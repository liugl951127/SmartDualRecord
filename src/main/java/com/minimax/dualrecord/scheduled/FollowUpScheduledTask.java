package com.minimax.dualrecord.scheduled;

import com.minimax.dualrecord.service.FollowUpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 犹豫期 3 次回访调度器
 *
 * 每小时扫描一次"已到回访日"的任务并执行
 * 配置项: dual-record.followup.scan-interval-ms (默认 3600000 = 1 小时)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowUpScheduledTask {

    private final FollowUpService followUpService;

    @Scheduled(fixedDelayString = "${dual-record.followup.scan-interval-ms:3600000}")
    public void scanAndExecute() {
        int executed = followUpService.executeDueFollowUps();
        if (executed > 0) {
            log.info("FollowUpScheduledTask: 已执行 {} 个回访", executed);
        }
    }
}
