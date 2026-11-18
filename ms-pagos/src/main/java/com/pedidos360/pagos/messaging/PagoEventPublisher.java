package com.pedidos360.pagos.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.pagos.dto.PagoResultadoEvent;
import com.pedidos360.pagos.model.AlertaFraude;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publica los eventos de ms-pagos hacia el resto del sistema:
 *
 *  - RabbitMQ (exchange directo / routing key): resultado del pago para que
 *    ms-carrito actualice el pedido. Reemplaza la llamada HTTP de vuelta, por
 *    lo que el backend queda SIN PROXY.
 *  - Kafka (pedidos360.alertas): alertas de fraude para el panel de monitoreo.
 */
@Component
public class PagoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PagoEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange:pedidos360.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.routing-key-pago-resultado:pedidos360.pagos.resultado.routing}")
    private String routingKeyPagoResultado;

    @Value("${app.kafka.topic-alertas:pedidos360.alertas}")
    private String topicoAlertas;

    public PagoEventPublisher(RabbitTemplate rabbitTemplate,
                              KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publica el resultado del pago en RabbitMQ. Si el broker no está
     * disponible solo se registra el error: el pago ya quedó persistido y la
     * respuesta al usuario no depende de la notificación.
     */
    public void publicarResultadoPago(PagoResultadoEvent evento) {
        try {
            rabbitTemplate.convertAndSend(exchangeName, routingKeyPagoResultado, evento);
            log.info("Resultado de pago del pedido {} publicado en {}/{}",
                    evento.pedidoId(), exchangeName, routingKeyPagoResultado);
        } catch (AmqpException e) {
            log.error("No se pudo publicar el resultado del pedido {} en RabbitMQ: {}",
                    evento.pedidoId(), e.getMessage(), e);
        }
    }

    /**
     * Publica la alerta antifraude en el tópico Kafka de alertas.
     */
    public void publicarAlertaFraude(AlertaFraude alerta) {
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("tipo", "ALERTA_FRAUDE");
        evento.put("tarjetaEnmascarada", alerta.getTarjetaEnmascarada());
        evento.put("usuarioId", alerta.getUsuarioId());
        evento.put("pedidoId", alerta.getPedidoId());
        evento.put("monto", alerta.getMonto());
        evento.put("intentos", alerta.getIntentosEn10Segundos());
        evento.put("motivo", alerta.getDescripcion());
        evento.put("fecha", Instant.now().toString());
        evento.put("servicio", "ms-pagos");

        try {
            String json = objectMapper.writeValueAsString(evento);
            kafkaTemplate.send(topicoAlertas, String.valueOf(alerta.getPedidoId()), json)
                    .whenComplete((resultado, error) -> {
                        if (error != null) {
                            log.error("No se pudo publicar la alerta de fraude en Kafka: {}", error.getMessage());
                        } else {
                            log.info("Alerta de fraude publicada en Kafka {} (partición {})",
                                    topicoAlertas, resultado.getRecordMetadata().partition());
                        }
                    });
        } catch (JsonProcessingException e) {
            log.error("No se pudo serializar la alerta de fraude: {}", e.getMessage());
        }
    }
}
