package com.pedidos360.monitoreo.admin.service;

import com.pedidos360.monitoreo.admin.dto.BindingDto;
import com.pedidos360.monitoreo.admin.dto.ColaDto;
import com.pedidos360.monitoreo.admin.dto.ExchangeDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Servicio encapsulador de la lógica de administración de RabbitMQ (EP3).
 * Encapsula la interacción con AmqpAdmin y RabbitAdmin.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final RabbitAdmin rabbitAdmin;

    public RabbitAdminService(ConnectionFactory connectionFactory) {
        this.rabbitAdmin = new RabbitAdmin(connectionFactory);
    }

    /**
     * Declara una cola en RabbitMQ con soporte de DLQ y argumentos opcionales.
     */
    public boolean crearCola(ColaDto dto) {
        log.info("Creando cola RabbitMQ: {}", dto.nombre());
        QueueBuilder builder = dto.durable() ? QueueBuilder.durable(dto.nombre()) : QueueBuilder.nonDurable(dto.nombre());

        if (dto.autoDelete()) {
            builder.autoDelete();
        }
        if (dto.exclusive()) {
            builder.exclusive();
        }
        if (dto.deadLetterExchange() != null && !dto.deadLetterExchange().isBlank()) {
            builder.withArgument("x-dead-letter-exchange", dto.deadLetterExchange());
        }
        if (dto.deadLetterRoutingKey() != null && !dto.deadLetterRoutingKey().isBlank()) {
            builder.withArgument("x-dead-letter-routing-key", dto.deadLetterRoutingKey());
        }

        Queue queue = builder.build();
        rabbitAdmin.declareQueue(queue);
        log.info("Cola {} declarada exitosamente en RabbitMQ", dto.nombre());
        return true;
    }

    /**
     * Elimina una cola existente en RabbitMQ.
     */
    public boolean eliminarCola(String nombreCola) {
        log.info("Eliminando cola RabbitMQ: {}", nombreCola);
        return rabbitAdmin.deleteQueue(nombreCola);
    }

    /**
     * Declara un exchange de tipo direct, topic, fanout o headers.
     */
    public boolean crearExchange(ExchangeDto dto) {
        log.info("Creando exchange RabbitMQ: {} de tipo {}", dto.nombre(), dto.tipo());
        Exchange exchange = switch (dto.tipo().toLowerCase()) {
            case "topic" -> new TopicExchange(dto.nombre(), dto.durable(), dto.autoDelete());
            case "fanout" -> new FanoutExchange(dto.nombre(), dto.durable(), dto.autoDelete());
            case "headers" -> new HeadersExchange(dto.nombre(), dto.durable(), dto.autoDelete());
            default -> new DirectExchange(dto.nombre(), dto.durable(), dto.autoDelete());
        };

        rabbitAdmin.declareExchange(exchange);
        log.info("Exchange {} declarado exitosamente", dto.nombre());
        return true;
    }

    /**
     * Elimina un exchange de RabbitMQ.
     */
    public boolean eliminarExchange(String nombreExchange) {
        log.info("Eliminando exchange RabbitMQ: {}", nombreExchange);
        return rabbitAdmin.deleteExchange(nombreExchange);
    }

    /**
     * Crea un binding entre una cola y un exchange.
     */
    public boolean crearBinding(BindingDto dto) {
        log.info("Creando binding: cola {} -> exchange {} con routing key {}",
                dto.cola(), dto.exchange(), dto.routingKey());

        Binding binding = new Binding(
                dto.cola(),
                Binding.DestinationType.QUEUE,
                dto.exchange(),
                dto.routingKey(),
                new HashMap<>()
        );
        rabbitAdmin.declareBinding(binding);
        log.info("Binding creado exitosamente");
        return true;
    }

    /**
     * Elimina un binding específico.
     */
    public void eliminarBinding(String cola, String exchange, String routingKey) {
        log.info("Eliminando binding: cola {} -> exchange {} con routing key {}", cola, exchange, routingKey);
        Binding binding = new Binding(cola, Binding.DestinationType.QUEUE, exchange, routingKey, null);
        rabbitAdmin.removeBinding(binding);
    }

    /**
     * Devuelve el estado general o información de conexión de RabbitMQ.
     */
    public Map<String, Object> obtenerEstado() {
        return Map.of(
                "estado", "CONECTADO",
                "motor", "RabbitMQ 3.13-management",
                "colasPrincipales", new String[]{"pedidos360.emails", "pedidos360.emails.dlq"},
                "exchangesPrincipales", new String[]{"pedidos360.exchange", "pedidos360.exchange.dlx"}
        );
    }
}
