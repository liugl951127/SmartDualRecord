package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.Recording;
import com.minimax.dualrecord.repository.RecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 录像留存到期调度 (v1.2 录像合规)
 *
 * 监管要求: 保险期 > 1 年的产品, 录像留存 ≥ 10 年
 *
 * 时间线:
 *  - T-30 天 预警 (通知合规 + 业务方)
 *  - T-7 天  通知 (通知客户 + 监管报送月度报告)
 *  - T+0 天  归档 (转冷存储 OSS-IA)
 *  - T+5 年  二次审核 (是否需继续留存, 涉及未决案件)
 *  - T+10 年 到期 (按监管要求销毁)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionScheduler {

    private final RecordingRepository recordingRepository;

    @Scheduled(fixedDelayString = "${dual-record.retention.scan-interval-ms:3600000}")
    public void scanExpiringRecordings() {
        LocalDate now = LocalDate.now();
        // 30 天后到期
        LocalDate t30 = now.plusDays(30);
        // 7 天后到期
        LocalDate t7 = now.plusDays(7);

        // 1. 30 天预警
        List<Recording> t30List = recordingRepository.selectList(
                new QueryWrapper<Recording>()
                        .eq("deleted", 0)
                        .between("retention_until", now, t30)
                        .isNull("retention_notified_at"));
        for (Recording r : t30List) {
            notifyCompliance(r, "30_DAY_WARN");
        }

        // 2. 7 天通知
        List<Recording> t7List = recordingRepository.selectList(
                new QueryWrapper<Recording>()
                        .eq("deleted", 0)
                        .between("retention_until", now, t7));
        for (Recording r : t7List) {
            notifyCustomer(r, "7_DAY_NOTIFY");
        }

        if (!t30List.isEmpty() || !t7List.isEmpty()) {
            log.info("留存扫描: 30 天预警 {} 个, 7 天通知 {} 个", t30List.size(), t7List.size());
        }
    }

    /**
     * 通知合规 + 业务方 (沙箱: 只打日志)
     */
    private void notifyCompliance(Recording r, String type) {
        log.warn("[合规] 留存 {}: recId={}, retentionUntil={}, businessId={}",
                type, r.getRecId(), r.getRetentionUntil(), r.getBusinessId());
        // 标记已通知
        r.setRetentionNotifiedAt(LocalDateTime.now());
        recordingRepository.updateById(r);
    }

    /**
     * 通知客户 (短信/微信/APP)
     */
    private void notifyCustomer(Recording r, String type) {
        log.info("[客户] 留存 {}: recId={}, retentionUntil={}",
                type, r.getRecId(), r.getRetentionUntil());
        // 沙箱: 实际对接 SMS/IM 网关
    }

    /**
     * 手动归档到冷存储 (运维触发)
     */
    public int archiveToColdStorage(LocalDate beforeDate) {
        List<Recording> targets = recordingRepository.selectList(
                new QueryWrapper<Recording>()
                        .eq("deleted", 0)
                        .lt("rec_end_utc", beforeDate.atStartOfDay())
                        .isNotNull("rec_end_utc"));
        log.info("归档候选: {} 个 (recEnd < {})", targets.size(), beforeDate);
        // 实际: 调用 OSS API 移动文件到 IA 存储, 更新 file_path 前缀
        return targets.size();
    }

    /**
     * 手动销毁 (监管要求外)
     */
    public int destroyExpired(LocalDate beforeDate) {
        List<Recording> targets = recordingRepository.selectList(
                new QueryWrapper<Recording>()
                        .eq("deleted", 0)
                        .lt("retention_until", beforeDate)
                        .isNull("preservation_id"));  // 保全中不可销毁
        log.warn("销毁候选: {} 个 (retention < {}, 无保全)", targets.size(), beforeDate);
        // 实际: 删除对象存储 + 软删除 tb_recording + 不可逆加密擦除
        return targets.size();
    }
}
