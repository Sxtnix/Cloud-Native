package com.pedidos360.email.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.email.config.RabbitConfig;
import com.pedidos360.email.model.MensajeEmail;
import com.pedidos360.email.service.EmailService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor de correos asíncronos con RabbitMQ (EP3).
 * Implementa confirmación manual explícita (ACK/NACK) y desvío a DLQ ante fallas.
 * Aislado completamente de la lógica de configuración y de negocio.
 */
@Component
public class EmailListener {

    private static final Logger log = LoggerFactory.getLogger(EmailListener.class);

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public EmailListener(ObjectMapper objectMapper, EmailService emailService) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    /**
     * Procesa mensajes de la cola con ACK/NACK explícito.
     */
    @RabbitListener(queues = "${app.rabbitmq.queue-email:pedidos360.emails}")
    public void recibirMensaje(Message message,
                               Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        MensajeEmail mensaje = null;
        try {
            mensaje = objectMapper.readValue(message.getBody(), MensajeEmail.class);
            log.info("Procesando mensaje RabbitMQ [tag={}, tipo={}, destino={}]",
                    deliveryTag, mensaje.tipo(), mensaje.destino());

            // Enviar correo (HTML o Texto)
            if (EmailService.esHtml(mensaje.cuerpo())) {
                emailService.enviarHtml(mensaje.destino(), mensaje.asunto(), mensaje.cuerpo());
            } else {
                emailService.enviarCorreo(mensaje.destino(), mensaje.asunto(), mensaje.cuerpo());
            }

            // Confirmación explícita (ACK)
            channel.basicAck(deliveryTag, false);
            log.info("ACK explícito enviado exitosamente para deliveryTag={}", deliveryTag);

        } catch (Exception e) {
            log.error("Fallo al procesar correo (deliveryTag={}): {}. Enviando NACK y derivando a DLQ.",
                    deliveryTag, e.getMessage(), e);

            // NACK explícito: requeue=false provoca que RabbitMQ transfiera el mensaje a la DLQ
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
