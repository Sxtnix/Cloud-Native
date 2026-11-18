package com.pedidos360.monitoreo.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.monitoreo.analytics.model.EventoNavegacion;
import com.pedidos360.monitoreo.analytics.service.DynamoDBService;
import com.pedidos360.monitoreo.model.EventoMonitor;
import com.pedidos360.monitoreo.store.EventoStore;
import com.pedidos360.monitoreo.websocket.MetricasWebSocketHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor multicanal de eventos de Apache Kafka (EP5).
 * Procesa:
 *  1. pedidos360.monitoreo: Eventos de compras, pagos y transiciones de estado.
 *  2. pedidos360.navegacion: Eventos de navegación (clickstream) para DynamoDB NoSQL y recomendaciones.
 *  3. pedidos360.alertas: Alertas de seguridad y antifraude.
 */
@Component
public class EventoConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventoConsumer.class);

    public static final String TOPICO_MONITOREO = "pedidos360.monitoreo";
    public static final String TOPICO_NAVEGACION = "pedidos360.navegacion";
    public static final String TOPICO_ALERTAS = "pedidos360.alertas";

    private final EventoStore store;
    private final ObjectMapper objectMapper;
    private final DynamoDBService dynamoDBService;
    private final MetricasWebSocketHandler webSocketHandler;

    public EventoConsumer(EventoStore store,
                          ObjectMapper objectMapper,
                          DynamoDBService dynamoDBService,
                          MetricasWebSocketHandler webSocketHandler) {
        this.store = store;
        this.objectMapper = objectMapper;
        this.dynamoDBService = dynamoDBService;
        this.webSocketHandler = webSocketHandler;
    }

    @KafkaListener(topics = "${app.kafka.topic-monitoreo:pedidos360.monitoreo}", groupId = "ms-monitoreo-group")
    public void escucharMonitoreo(ConsumerRecord<String, String> record) {
        try {
            EventoMonitor evento = objectMapper
                    .readValue(record.value(), EventoMonitor.class)
                    .enPosicion(record.topic(), record.partition(), record.offset());

            store.registrar(evento);
            webSocketHandler.registrarClicNavegacion();
            log.info("[Kafka Monitoreo] Evento {} (pedido {}) en {}[{}] offset={}",
                    evento.tipo(), evento.pedidoId(),
                    record.topic(), record.partition(), record.offset());
        } catch (JsonProcessingException e) {
            log.error("Mensaje ilegible en Kafka {}[{}] offset={}: {}",
                    record.topic(), record.partition(), record.offset(), record.value());
        }
    }

    @KafkaListener(topics = "${app.kafka.topic-navegacion:pedidos360.navegacion}", groupId = "ms-analytics-group")
    public void escucharNavegacion(ConsumerRecord<String, String> record) {
        try {
            EventoNavegacion evento = objectMapper.readValue(record.value(), EventoNavegacion.class);
            dynamoDBService.guardarEventoNavegacion(evento);
            webSocketHandler.registrarClicNavegacion();
            log.info("[Kafka Clickstream] Navegación capturada: usuario={}, acción={}, producto={}",
                    evento.usuarioId(), evento.tipoEvento(), evento.nombreProducto());
        } catch (Exception e) {
            log.error("Error al procesar evento de navegación de Kafka: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "${app.kafka.topic-alertas:pedidos360.alertas}", groupId = "ms-alertas-group")
    public void escucharAlertas(ConsumerRecord<String, String> record) {
        log.warn("🚨 [Kafka Alertas Seguridad] Alerta recibida: {}", record.value());
    }
}
