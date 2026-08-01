package com.minimax.dualrecord.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimax.dualrecord.domain.AdvisorSession;
import com.minimax.dualrecord.dto.TransferToAdvisorRequest;
import com.minimax.dualrecord.exception.BusinessException;
import com.minimax.dualrecord.repository.AdvisorSessionRepository;
import com.minimax.dualrecord.websocket.RecordingWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 理财经理转接服务 (v1.5 H5 → PC)
 *
 * 业务流:
 *   1. H5 客户请求转接 → 选理财经理/填原因 → POST /request
 *   2. 服务端落库 (status=PENDING) + WebSocket 推送给该理财经理
 *   3. 理财经理在 PC 端看到请求 → 接单 / 拒绝
 *   4. 接单 → 状态=ACTIVE + WebSocket 推送给客户 (理财经理信息)
 *   5. 双方建立实时通道 (CHAT / RECORDING_CONTROL)
 *   6. 一方结束 → 状态=ENDED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisorService {

    private final AdvisorSessionRepository sessionRepository;
    private final RecordingWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 客户请求转接理财经理
     */
    @Transactional(rollbackFor = Exception.class)
    public AdvisorSession requestTransfer(TransferToAdvisorRequest req) {
        if (req.getBusinessId() == null) {
            throw new BusinessException("INVALID_BUSINESS_ID", "业务 ID 不能为空");
        }
        AdvisorSession session = new AdvisorSession();
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setBusinessId(req.getBusinessId());
        session.setCustomerName(req.getCustomerName() != null ? req.getCustomerName() : "客户");
        session.setCustomerMobile(req.getCustomerMobile());
        session.setAdvisorId(req.getPreferredAdvisorId() != null ? req.getPreferredAdvisorId() : "teller-wang-001");
        session.setAdvisorName(req.getPreferredAdvisorId() != null ? "指定理财经理" : "王经理 (默认)");
        session.setAdvisorBranch("北京朝阳支行");
        session.setReason(req.getReason() != null ? req.getReason() : "OTHER");
        session.setDescription(req.getDescription());
        session.setStatus("PENDING");
        session.setCreatedAt(LocalDateTime.now());
        session.setDeleted(0);
        sessionRepository.insert(session);

        // WebSocket 推送: 通知所有 PC 端理财经理有新请求
        pushToAdvisors(session, "TRANSFER_REQUEST");
        log.info("H5 客户请求转接理财经理: sessionId={}, advisor={}", session.getSessionId(), session.getAdvisorId());
        return session;
    }

    /**
     * 理财经理接单
     */
    @Transactional(rollbackFor = Exception.class)
    public AdvisorSession acceptTransfer(String sessionId, String advisorId, String advisorName) {
        AdvisorSession session = getById(sessionId);
        if (!"PENDING".equals(session.getStatus())) {
            throw new BusinessException("INVALID_STATE", "该请求已处理: " + session.getStatus());
        }
        session.setAdvisorId(advisorId);
        session.setAdvisorName(advisorName);
        session.setStatus("ACTIVE");
        session.setAcceptedAt(LocalDateTime.now());
        sessionRepository.updateById(session);

        // 推送: 通知客户
        pushToClient(session, "TRANSFER_ACCEPTED");
        log.info("理财经理接单: sessionId={}, advisor={}", sessionId, advisorName);
        return session;
    }

    /**
     * 理财经理拒绝
     */
    @Transactional(rollbackFor = Exception.class)
    public AdvisorSession declineTransfer(String sessionId, String reason) {
        AdvisorSession session = getById(sessionId);
        if (!"PENDING".equals(session.getStatus())) {
            throw new BusinessException("INVALID_STATE", "该请求已处理: " + session.getStatus());
        }
        session.setStatus("DECLINED");
        session.setEndedAt(LocalDateTime.now());
        session.setEndReason("ADVISOR_DECLINED: " + reason);
        sessionRepository.updateById(session);

        // 推送: 通知客户
        pushToClient(session, "TRANSFER_DECLINED");
        return session;
    }

    /**
     * 结束会话
     */
    @Transactional(rollbackFor = Exception.class)
    public AdvisorSession endSession(String sessionId, String endReason) {
        AdvisorSession session = getById(sessionId);
        if ("ENDED".equals(session.getStatus()) || "DECLINED".equals(session.getStatus())) {
            return session;  // 已结束
        }
        session.setStatus("ENDED");
        session.setEndedAt(LocalDateTime.now());
        session.setEndReason(endReason);
        sessionRepository.updateById(session);

        // 推送: 通知双方
        pushToAdvisors(session, "SESSION_ENDED");
        pushToClient(session, "SESSION_ENDED");
        log.info("会话结束: sessionId={}, reason={}", sessionId, endReason);
        return session;
    }

    /**
     * 理财经理查看待处理请求
     */
    public List<AdvisorSession> listPending(String advisorId) {
        return sessionRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AdvisorSession>()
                        .eq("status", "PENDING")
                        .and(w -> w.eq("advisor_id", advisorId).or().eq("advisor_id", "teller-wang-001"))
                        .orderByDesc("created_at"));
    }

    /**
     * 理财经理查看自己的活跃会话
     */
    public List<AdvisorSession> listActive(String advisorId) {
        return sessionRepository.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AdvisorSession>()
                        .eq("advisor_id", advisorId)
                        .in("status", "PENDING", "ACCEPTED", "ACTIVE")
                        .orderByDesc("created_at"));
    }

    /**
     * 客户查看自己的活跃会话
     */
    public AdvisorSession getActiveByBusiness(String businessId) {
        return sessionRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AdvisorSession>()
                        .eq("business_id", businessId)
                        .in("status", "PENDING", "ACCEPTED", "ACTIVE")
                        .orderByDesc("created_at")
                        .last("LIMIT 1"));
    }

    /**
     * 可选理财经理列表 (H5 客户选择)
     */
    public List<Map<String, Object>> listAvailableAdvisors() {
        // 实际生产中从 DB 取
        return List.of(
                Map.of(
                        "advisorId", "teller-wang-001",
                        "name", "王经理",
                        "branch", "北京朝阳支行",
                        "avatar", "👨‍💼",
                        "online", true,
                        "rating", 4.9,
                        "yearsOfExp", 8,
                        "specialties", List.of("稳健理财", "基金")
                ),
                Map.of(
                        "advisorId", "teller-li-002",
                        "name", "李经理",
                        "branch", "北京海淀支行",
                        "avatar", "👩‍💼",
                        "online", true,
                        "rating", 4.8,
                        "yearsOfExp", 5,
                        "specialties", List.of("保险", "投连险")
                ),
                Map.of(
                        "advisorId", "teller-zhang-003",
                        "name", "张经理",
                        "branch", "北京西城支行",
                        "avatar", "👨‍💼",
                        "online", false,
                        "rating", 4.7,
                        "yearsOfExp", 3,
                        "specialties", List.of("基金", "私募")
                )
        );
    }

    private AdvisorSession getById(String sessionId) {
        AdvisorSession session = sessionRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AdvisorSession>()
                        .eq("session_id", sessionId));
        if (session == null) {
            throw new BusinessException("SESSION_NOT_FOUND", "会话不存在: " + sessionId);
        }
        return session;
    }

    private void pushToClient(AdvisorSession session, String type) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", type);
            msg.put("sessionId", session.getSessionId());
            msg.put("businessId", session.getBusinessId());
            msg.put("advisorId", session.getAdvisorId());
            msg.put("advisorName", session.getAdvisorName());
            msg.put("advisorBranch", session.getAdvisorBranch());
            msg.put("status", session.getStatus());
            String json = objectMapper.writeValueAsString(msg);
            webSocketHandler.broadcast(session.getBusinessId(), json);
        } catch (Exception e) {
            log.warn("WebSocket 推送客户失败: {}", e.getMessage());
        }
    }

    private void pushToAdvisors(AdvisorSession session, String type) {
        try {
            Map<String, Object> msg = new HashMap<>();
            msg.put("type", type);
            msg.put("sessionId", session.getSessionId());
            msg.put("businessId", session.getBusinessId());
            msg.put("customerName", session.getCustomerName());
            msg.put("customerMobile", session.getCustomerMobile());
            msg.put("reason", session.getReason());
            msg.put("description", session.getDescription());
            msg.put("status", session.getStatus());
            msg.put("createdAt", session.getCreatedAt() != null ? session.getCreatedAt().toString() : null);
            String json = objectMapper.writeValueAsString(msg);
            // 推送给所有连接 (实际生产中按 advisorId 路由)
            webSocketHandler.broadcast("ADVISOR_" + session.getAdvisorId(), json);
        } catch (Exception e) {
            log.warn("WebSocket 推送理财经理失败: {}", e.getMessage());
        }
    }
}
