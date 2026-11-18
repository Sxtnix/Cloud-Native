package com.pedidos360.monitoreo.analytics.controller;

import com.pedidos360.monitoreo.analytics.model.EventoNavegacion;
import com.pedidos360.monitoreo.analytics.model.RecomendacionDto;
import com.pedidos360.monitoreo.analytics.service.DynamoDBService;
import com.pedidos360.monitoreo.analytics.service.RecomendacionService;
import com.pedidos360.monitoreo.websocket.MetricasWebSocketHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Endpoints REST del Pipeline de Analítica y Recomendaciones (Evaluación 3).
 */
@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final RecomendacionService recomendacionService;
    private final DynamoDBService dynamoDBService;
    private final MetricasWebSocketHandler webSocketHandler;

    public AnalyticsController(RecomendacionService recomendacionService,
                               DynamoDBService dynamoDBService,
                               MetricasWebSocketHandler webSocketHandler) {
        this.recomendacionService = recomendacionService;
        this.dynamoDBService = dynamoDBService;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Devuelve recomendaciones dinámicas de productos basadas en navegación.
     */
    @GetMapping("/recomendaciones")
    public ResponseEntity<List<RecomendacionDto>> obtenerRecomendaciones(
            @RequestParam(required = false) String usuarioId,
            @RequestParam(required = false) String categoria) {
        return ResponseEntity.ok(recomendacionService.obtenerRecomendaciones(usuarioId, categoria));
    }

    /**
     * Ingesta directa de eventos de clickstream (persistidos en DynamoDB NoSQL).
     */
    @PostMapping("/eventos")
    public ResponseEntity<?> registrarEvento(@RequestBody EventoNavegacion evento) {
        dynamoDBService.guardarEventoNavegacion(evento);
        webSocketHandler.registrarClicNavegacion();
        return ResponseEntity.ok(Map.of("estado", "REGISTRADO", "nosql", "DynamoDB"));
    }

    /**
     * Consulta REST de métricas en vivo (respaldo para clientes sin soporte de WebSocket).
     */
    @GetMapping("/metricas-en-vivo")
    public ResponseEntity<Map<String, Object>> obtenerMetricasEnVivo() {
        return ResponseEntity.ok(webSocketHandler.construirPayloadMetricas());
    }
}
