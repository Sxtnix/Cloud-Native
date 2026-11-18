package com.pedidos360.monitoreo.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configuración de WebSockets para métricas analíticas en tiempo real (+3 puntos extra).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MetricasWebSocketHandler metricasWebSocketHandler;

    public WebSocketConfig(MetricasWebSocketHandler metricasWebSocketHandler) {
        this.metricasWebSocketHandler = metricasWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(metricasWebSocketHandler, "/ws-metricas")
                .setAllowedOrigins("*");
    }
}
