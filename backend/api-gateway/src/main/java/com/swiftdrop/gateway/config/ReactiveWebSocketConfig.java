package com.swiftdrop.gateway.config;

import com.swiftdrop.gateway.trackinghub.controller.LiveTrackingStreamController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;

import java.util.Map;

@Configuration
public class ReactiveWebSocketConfig {
    @Bean
    public HandlerMapping trackingWebSocketMapping(LiveTrackingStreamController controller) {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setOrder(-1);
        Map<String, Object> urlMap = Map.of("/ws/tracking", controller);
        mapping.setUrlMap(urlMap);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }
}
