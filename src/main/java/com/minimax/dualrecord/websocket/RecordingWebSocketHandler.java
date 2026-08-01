package com.minimax.dualrecord.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minimax.dualrecord.service.ComplianceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时 WebSocket Handler
 *
 * 路径: /ws/recording/{businessId}
 *
 * 服务端推送事件：
 *  - {"type": "ASR_CHUNK", "text": "...", "timestamp": 12345}
 *  - {"type": "FORBIDDEN_PHRASE_HIT", "phrase": "...", "severity": "HIGH"}
 *  - {"type": "NODE_COMPLETED", "node": "02-disclosure", "duration": 60}
 *  - {"type": "DEEPFAKE_ALERT", "score": 0.96, "verdict": "SUSPECTED"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingWebSocketHandler extends TextWebSocketHandler {

    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String businessId = extractBusinessId(session);
        sessions.computeIfAbsent(businessId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("WebSocket 连接建立: businessId={}, sessionId={}", businessId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String businessId = extractBusinessId(session);
        String text = message.getPayload();
        log.debug("WebSocket 收到消息: businessId={}, text={}", businessId, text);

        // 1. 解析客户端发来的 ASR 文本
        try {
            Map<String, Object> msg = objectMapper.readValue(text, Map.class);
            if ("ASR_CHUNK".equals(msg.get("type"))) {
                String asrText = (String) msg.get("text");
                // 2. 实时禁播词扫描
                List<ComplianceService.Hit> hits = complianceService.scan(asrText);
                if (!hits.isEmpty()) {
                    sendEvent(session, Map.of(
                            "type", "FORBIDDEN_PHRASE_HIT",
                            "hits", hits,
                            "severity", hits.get(0).severity()
                    ));
                }
            }
        } catch (Exception e) {
            log.error("WebSocket 消息解析失败: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String businessId = extractBusinessId(session);
        Set<WebSocketSession> set = sessions.get(businessId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) sessions.remove(businessId);
        }
        log.info("WebSocket 关闭: businessId={}, status={}", businessId, status);
    }

    /**
     * 服务端推送事件给指定业务 (单连接兼容)
     */
    public void sendEvent(String businessId, Object event) {
        Set<WebSocketSession> set = sessions.get(businessId);
        if (set != null) {
            for (WebSocketSession s : set) {
                if (s.isOpen()) sendEvent(s, event);
            }
        }
    }

    /**
     * 广播: 给业务所有连接 (坐席 + 客户) 都发
     */
    public void broadcast(String businessId, Object event) {
        sendEvent(businessId, event);
    }

    private void sendEvent(WebSocketSession session, Object event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("WebSocket 推送失败: {}", e.getMessage());
        }
    }

    private String extractBusinessId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        int idx = path.lastIndexOf('/');
        return idx >= 0 ? path.substring(idx + 1) : "unknown";
    }
}
