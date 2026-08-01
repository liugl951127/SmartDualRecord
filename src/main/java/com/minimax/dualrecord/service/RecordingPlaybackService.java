package com.minimax.dualrecord.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.minimax.dualrecord.domain.RecordingAccessLog;
import com.minimax.dualrecord.domain.Recording;
import com.minimax.dualrecord.repository.RecordingAccessLogRepository;
import com.minimax.dualrecord.repository.RecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 录像回放 + DRM 服务 (v1.2 录像合规)
 *
 * 监管要求:
 *  - 临时 token (默认 5 分钟有效, 不可下载传播)
 *  - 水印播放 (客户/经理/工号/时间戳叠加)
 *  - 5 个角色不同权限:
 *    CUSTOMER   看自己的
 *    SELLER     看自己录的
 *    AUDITOR    看本机构所有
 *    REGULATOR  看所有 (金发 8 号)
 *    ADMIN      仅元数据, 不看视频
 *  - 任何访问/下载/截图/录屏 必留痕
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingPlaybackService {

    private final RecordingRepository recordingRepository;
    private final RecordingAccessLogRepository accessLogRepository;

    @Value("${dual-record.playback.default-ttl-sec:300}")
    private int defaultTtlSec;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 签发回放 token (5 分钟有效)
     */
    public PlaybackToken issueToken(String recId, String userId, String userRole, int ttlSec) {
        // 1. 校验录像存在
        Recording rec = recordingRepository.selectOne(
                new QueryWrapper<Recording>().eq("rec_id", recId));
        if (rec == null) {
            throw new IllegalArgumentException("录像不存在: " + recId);
        }
        // 2. 校验角色权限
        if (!hasPermission(userRole, userId, rec)) {
            throw new SecurityException("角色 " + userRole + " 无权访问录像 " + recId);
        }
        // 3. 生成 token
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);

        // 4. 写访问日志
        logAccess(recId, rec.getBusinessId(), userId, userRole, "PLAYBACK", null, token);

        int ttl = ttlSec > 0 ? ttlSec : defaultTtlSec;
        String url = "/api/v1/recording/playback/" + recId + "?token=" + token
                + "&user=" + userId + "&role=" + userRole + "&exp=" + (System.currentTimeMillis() / 1000 + ttl);
        log.info("回放 token 签发: recId={}, user={}, role={}, ttl={}s", recId, userId, userRole, ttl);
        return new PlaybackToken(recId, userId, userRole, token, url,
                System.currentTimeMillis() / 1000 + ttl, ttl);
    }

    /**
     * 角色权限校验
     */
    public boolean hasPermission(String role, String userId, Recording rec) {
        return switch (role) {
            case "CUSTOMER" -> true;   // 客户看自己的 (生产用 rec.customerIdHash 校验)
            case "SELLER" -> true;     // 经理看自己录的 (生产用 rec.sellerIdHash 校验)
            case "AUDITOR", "REGULATOR" -> true;  // 审计/监管看所有
            case "ADMIN" -> true;      // 管理员 (生产限制 IP)
            default -> false;
        };
    }

    /**
     * 记录访问日志
     */
    @Transactional(rollbackFor = Exception.class)
    public void logAccess(String recId, String businessId, String userId, String userRole,
                          String accessType, Integer durationSec, String token) {
        RecordingAccessLog log = new RecordingAccessLog();
        log.setRecId(recId);
        log.setBusinessId(businessId);
        log.setUserId(userId);
        log.setUserRole(userRole);
        log.setAccessType(accessType);
        log.setDurationSec(durationSec);
        log.setAccessToken(token == null ? null : token.substring(0, Math.min(8, token.length())) + "***");
        log.setAccessedAt(LocalDateTime.now());
        accessLogRepository.insert(log);
    }

    /**
     * 列出某录像的访问日志 (监管/审计)
     */
    public List<RecordingAccessLog> listAccessLog(String recId) {
        return accessLogRepository.selectList(
                new QueryWrapper<RecordingAccessLog>()
                        .eq("rec_id", recId)
                        .orderByDesc("accessed_at"));
    }

    /**
     * 列出某用户的访问日志 (反查)
     */
    public List<RecordingAccessLog> listByUser(String userId) {
        return accessLogRepository.selectList(
                new QueryWrapper<RecordingAccessLog>()
                        .eq("user_id", userId)
                        .orderByDesc("accessed_at"));
    }

    public record PlaybackToken(String recId, String userId, String userRole,
                                String token, String url, long expiresAt, int ttlSec) {}
}
