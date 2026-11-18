package com.pedidos360.productos.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * Productor de eventos de navegación (clickstream) en Apache Kafka (EP5 y Eval 3).
 * Desacoplamiento operativo: publica asíncronamente en "pedidos360.navegacion"
 * sin comprometer el rendimiento de las consultas transaccionales de productos.
 */
@Component
public class NavegacionPublisher {

    private static final Logger log = LoggerFactory.getLogger(NavegacionPublisher.class);

    public static final String TOPICO = "pedidos360.navegacion";

    @Value("${app.kafka.topic-navegacion:pedidos360.navegacion}")
    private String topicoNavegacion;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public NavegacionPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publicarVistaProducto(Long productoId, String nombre, String categoria, Double precio, String usuarioId) {
        try {
            Map<String, Object> payload = Map.of(
                    "usuarioId", usuarioId != null ? usuarioId : "anonimo",
                    "tipoEvento", "VER_PRODUCTO",
                    "productoId", productoId,
                    "nombreProducto", nombre != null ? nombre : "Producto #" + productoId,
                    "categoria", categoria != null ? categoria : "General",
                    "precio", precio != null ? precio : 0.0,
                    "timestamp", Instant.now().toString()
            );

            String json = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(topicoNavegacion, String.valueOf(productoId), json);
            log.info("[Kafka Clickstream] Evento VER_PRODUCTO publicado para producto {}", productoId);
        } catch (Exception e) {
            log.warn("[Kafka Clickstream] No se pudo publicar evento de navegación (broker no disponible): {}", e.getMessage());
        }
    }
}
