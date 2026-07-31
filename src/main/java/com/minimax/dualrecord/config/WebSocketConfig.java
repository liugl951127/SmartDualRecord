package com.minimax.dualrecord.config;

import com.minimax.dualrecord.websocket.RecordingWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置 · 用于实时 ASR 转写推送 + AI 实时质检告警
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final RecordingWebSocketHandler recordingHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(recordingHandler, "/ws/recording/{businessId}")
                .setAllowedOriginPatterns("*");
    }
}
