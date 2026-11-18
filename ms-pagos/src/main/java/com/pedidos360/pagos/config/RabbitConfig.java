package com.pedidos360.pagos.config;

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
 * Configuración centralizada de RabbitMQ para ms-pagos (EP3).
 *
 * ms-pagos actúa como PRODUCTOR del resultado del pago: publica en el
 * exchange directo y ms-carrito lo consume desde la cola
 * pedidos360.pagos.resultado. No hay llamadas HTTP entre los dos servicios.
 */
@Configuration
public class RabbitConfig {

    @Value("${app.rabbitmq.exchange:pedidos360.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.queue-pago-resultado:pedidos360.pagos.resultado}")
    private String pagoResultadoQueueName;

    @Value("${app.rabbitmq.queue-pago-resultado-dlq:pedidos360.pagos.resultado.dlq}")
    private String pagoResultadoDlqName;

    @Value("${app.rabbitmq.routing-key-pago-resultado:pedidos360.pagos.resultado.routing}")
    private String pagoResultadoRoutingKey;

    @Bean
    public DirectExchange pedidosExchange() {
        return new DirectExchange(exchangeName, true, false);
    }

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

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
