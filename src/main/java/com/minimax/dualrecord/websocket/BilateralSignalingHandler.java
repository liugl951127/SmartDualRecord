package com.minimax.dualrecord.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 双边录制信令中继 (WebRTC Signaling)
 *
 * 路径: /ws/bilateral/{businessId}/{role}
 *   role = CUSTOMER (H5 端) / AGENT (PC 坐席)
 *
 * 信令消息透传 (不解析内容, 只做转发):
 *  - OFFER: 客户端发起连接
 *  - ANSWER: 坐席应答
 *  - ICE_CANDIDATE: ICE 候选
 *  - READY: 加入房间
 *  - BYE: 离开
 *  - PEER_JOINED / PEER_LEFT: 房间事件
 *
 * 视频数据不经过服务器 (P2P), 服务器只做信令中继
 * 但录制端 (两端各自) 负责自己的本地 MediaRecorder
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BilateralSignalingHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    // businessId -> role -> Set<session>
    private final Map<String, Map<String, Set<WebSocketSession>>> rooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // /ws/bilateral/{businessId}/{role}
        // parts = ["", "ws", "bilateral", "{businessId}", "{role}"]
        // businessId = parts[3], role = parts[4]
        String businessId = extractPathPart(session, 3);
        String role = extractPathPart(session, 4);
        log.info("双边信令连接: businessId={}, role={}, session={}", businessId, role, session.getId());

        // 加入房间
        rooms.computeIfAbsent(businessId, k -> new ConcurrentHashMap<>())
             .computeIfAbsent(role, k -> ConcurrentHashMap.newKeySet())
             .add(session);

        session.getAttributes().put("businessId", businessId);
        session.getAttributes().put("role", role);

        // 通知同房间其他人: 新人加入
        broadcastToPeers(businessId, role, Map.of(
            "type", "PEER_JOINED",
            "role", role,
            "sessionId", session.getId()
        ), session);

        // 通知自己: 当前房间状态
        try {
            Map<String, Object> status = new java.util.HashMap<>();
            status.put("type", "ROOM_STATUS");
            status.put("businessId", businessId);
            status.put("role", role);
            status.put("peers", getPeersInRoom(businessId, role));
            String json = objectMapper.writeValueAsString(status);
            log.info("发送 ROOM_STATUS 给 {} ({}): {}", role, session.getId(), json);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("发送房间状态失败: {}", e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String businessId = (String) session.getAttributes().get("businessId");
        String role = (String) session.getAttributes().get("role");
        String text = message.getPayload();

        try {
            Map<String, Object> msg = objectMapper.readValue(text, Map.class);
            String type = (String) msg.get("type");

            // 业务级事件 (不转发, 触发服务端逻辑)
            switch (type != null ? type : "") {
                case "ASR_CHUNK" -> {
                    // 禁播词扫描可以加, 这里暂透传
                    broadcastToPeers(businessId, role, msg, session);
                }
                case "NODE_TICK" -> {
                    // 节点进度同步
                    broadcastToPeers(businessId, role, msg, session);
                }
                case "OFFER", "ANSWER", "ICE_CANDIDATE" -> {
                    // WebRTC 信令透传给对端
                    log.debug("信令透传: type={}, from={}", type, role);
                    broadcastToPeers(businessId, role, msg, session);
                }
                case "READY" -> {
                    // 客户/坐席准备就绪, 通知对端可以开始
                    broadcastToPeers(businessId, role, msg, session);
                }
                default -> {
                    // 其他消息直接转发
                    broadcastToPeers(businessId, role, msg, session);
                }
            }
        } catch (Exception e) {
            log.error("信令消息处理失败: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String businessId = (String) session.getAttributes().get("businessId");
        String role = (String) session.getAttributes().get("role");
        log.info("双边信令关闭: businessId={}, role={}, status={}", businessId, role, status);

        if (businessId != null && role != null) {
            Map<String, Set<WebSocketSession>> room = rooms.get(businessId);
            if (room != null) {
                Set<WebSocketSession> set = room.get(role);
                if (set != null) {
                    set.remove(session);
                    if (set.isEmpty()) room.remove(role);
                }
                if (room.isEmpty()) rooms.remove(businessId);
            }
            // 通知对端: 有人离开
            broadcastToPeers(businessId, role, Map.of(
                "type", "PEER_LEFT",
                "role", role,
                "sessionId", session.getId()
            ), null);
        }
    }

    /**
     * 给同房间的其他人发消息 (排除自己)
     */
    private void broadcastToPeers(String businessId, String selfRole, Object msg, WebSocketSession exclude) {
        Map<String, Set<WebSocketSession>> room = rooms.get(businessId);
        if (room == null) return;
        for (Map.Entry<String, Set<WebSocketSession>> entry : room.entrySet()) {
            // 给对端 (非自己)
            if (entry.getKey().equals(selfRole)) continue;
            for (WebSocketSession s : entry.getValue()) {
                if (s == exclude) continue;
                if (s.isOpen()) send(s, msg);
            }
        }
    }

    /**
     * 获取房间内对端信息
     */
    private Map<String, Object> getPeersInRoom(String businessId, String selfRole) {
        Map<String, Set<WebSocketSession>> room = rooms.get(businessId);
        if (room == null) return Map.of();
        return Map.of(
            "selfRole", selfRole,
            "peers", room.keySet().stream().filter(r -> !r.equals(selfRole)).toList()
        );
    }

    private void send(WebSocketSession s, Object msg) {
        try {
            String json = objectMapper.writeValueAsString(msg);
            s.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("双边信令推送失败: {}", e.getMessage());
        }
    }

    private String extractPathPart(WebSocketSession session, int idx) {
        // 直接按位置取: /ws/bilateral/{businessId}/{role}
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String[] parts = path.split("/");
        return (idx >= 0 && idx < parts.length) ? parts[idx] : "unknown";
    }
}
