package com.pedidos360.carrito.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración centralizada de RabbitMQ (EP3) para ms-carrito.
 *
 * Nombres en application.yml (sección app.rabbitmq) y beans explícitos de
 * Queue, DirectExchange y Binding para cada caso de uso:
 *
 *  1. Correos     : pedidos360.emails  (pedidos360.exchange + routing key)
 *  2. Pago        : pedidos360.pagos.resultado (evento que publica ms-pagos)
 *
 * Ambas colas tienen Dead Letter Queue: si el consumidor falla de forma
 * definitiva (NACK sin requeue) el mensaje termina en la cola .dlq.
 */
@Configuration
public class RabbitConfig {

    @Value("${app.rabbitmq.exchange:pedidos360.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.queue-email:pedidos360.emails}")
    private String emailQueueName;

    @Value("${app.rabbitmq.queue-email-dlq:pedidos360.emails.dlq}")
    private String emailDlqName;

    @Value("${app.rabbitmq.routing-key-email:pedidos360.emails.routing}")
    private String emailRoutingKey;

    @Value("${app.rabbitmq.queue-pago-resultado:pedidos360.pagos.resultado}")
    private String pagoResultadoQueueName;

    @Value("${app.rabbitmq.queue-pago-resultado-dlq:pedidos360.pagos.resultado.dlq}")
    private String pagoResultadoDlqName;

    @Value("${app.rabbitmq.routing-key-pago-resultado:pedidos360.pagos.resultado.routing}")
    private String pagoResultadoRoutingKey;

    /** Exchange principal (direct) de Pedidos360. */
    @Bean
    public DirectExchange pedidosExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

    /** Cola principal de correos (lado productor). */
    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(emailQueueName)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", emailDlqName)
                .build();
    }

    @Bean
    public Queue emailDeadLetterQueue() {
        return QueueBuilder.durable(emailDlqName).build();
    }

    @Bean
    public Binding emailBinding(Queue emailQueue, DirectExchange pedidosExchange) {
        return BindingBuilder.bind(emailQueue).to(pedidosExchange).with(emailRoutingKey);
    }

    /**
     * Cola de resultados de pago: la publica ms-pagos y la consume ms-carrito.
     * Así el pago no necesita ninguna llamada HTTP de vuelta (sin proxy).
     */
    @Bean
    public Queue pagoResultadoQueue() {
        return QueueBuilder.durable(pagoResultadoQueueName)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", pagoResultadoDlqName)
                .build();
    }

    @Bean
    public Queue pagoResultadoDeadLetterQueue() {
        return QueueBuilder.durable(pagoResultadoDlqName).build();
    }

    @Bean
    public Binding pagoResultadoBinding(Queue pagoResultadoQueue, DirectExchange pedidosExchange) {
        return BindingBuilder.bind(pagoResultadoQueue).to(pedidosExchange).with(pagoResultadoRoutingKey);
    }

    /** Serializa/deserializa los mensajes como JSON. */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
