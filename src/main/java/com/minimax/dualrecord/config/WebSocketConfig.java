package com.minimax.dualrecord.config;

import com.minimax.dualrecord.websocket.BilateralSignalingHandler;
import com.minimax.dualrecord.websocket.RecordingWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 *
 * 两条通道:
 *  1. /ws/recording/{businessId} - 实时 ASR 推送 + AI 质检告警
 *  2. /ws/bilateral/{businessId}/{role} - 双边录制信令中继 (CUSTOMER ↔ AGENT)
 *
 * 实现 WebSocketConfigurer 接口 (Spring 标准)
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements org.springframework.web.socket.config.annotation.WebSocketConfigurer {

    private final RecordingWebSocketHandler recordingHandler;
    private final BilateralSignalingHandler bilateralHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(recordingHandler, "/ws/recording/{businessId}")
                .setAllowedOriginPatterns("*");
        registry.addHandler(bilateralHandler, "/ws/bilateral/{businessId}/{role}")
                .setAllowedOriginPatterns("*");
    }
}
