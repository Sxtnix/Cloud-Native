package com.pedidos360.email.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración centralizada de RabbitMQ para ms-email (EP3).
 * Declara de forma explícita beans de Queue, Exchange y Binding tanto para
 * el flujo principal de notificaciones como para la Dead Letter Queue (DLQ).
 */
@Configuration
public class RabbitConfig {

    public static final String EMAIL_QUEUE = "pedidos360.emails";
    public static final String EMAIL_DEAD_LETTER_QUEUE = "pedidos360.emails.dlq";

    @Value("${app.rabbitmq.exchange:pedidos360.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.queue-email:pedidos360.emails}")
    private String queueName;

    @Value("${app.rabbitmq.queue-dlq:pedidos360.emails.dlq}")
    private String dlqQueueName;

    @Value("${app.rabbitmq.routing-key-email:pedidos360.emails.routing}")
    private String routingKey;

    /** Exchange principal para eventos de pedidos360 */
    @Bean
    public DirectExchange emailExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    /**
     * Cola principal de correos con configuración explícita de DLQ.
     */
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(queueName)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", dlqQueueName)
                .build();
    }

    /** Cola de mensajes no entregables (Dead Letter Queue) */
    @Bean
    public Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(dlqQueueName).build();
    }

    /** Binding que une la cola principal con el exchange mediante routing key */
    @Bean
    public Binding emailBinding(Queue emailQueue, DirectExchange emailExchange) {
        return BindingBuilder.bind(emailQueue).to(emailExchange).with(routingKey);
    }
}
