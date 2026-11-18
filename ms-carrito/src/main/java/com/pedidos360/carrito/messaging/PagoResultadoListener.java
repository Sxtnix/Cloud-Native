package com.pedidos360.carrito.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.carrito.model.EstadoPago;
import com.pedidos360.carrito.model.Pedido;
import com.pedidos360.carrito.service.CarritoService;
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
 * Consumidor del resultado de pago con confirmación manual (EP3).
 *
 * Flujo sin proxy en el backend:
 *   Frontend --POST /pagos/procesar--> ms-pagos --evento RabbitMQ--> ms-carrito
 *
 * ms-pagos publica el resultado (aprobado / rechazado / bloqueado por fraude)
 * y este listener aplica el estado al pedido, envía el correo y registra el
 * evento de monitoreo. Si algo falla se hace NACK sin requeue y el mensaje
 * se deriva a la DLQ, donde queda disponible para inspección.
 */
@Component
public class PagoResultadoListener {

    private static final Logger log = LoggerFactory.getLogger(PagoResultadoListener.class);

    private final ObjectMapper objectMapper;
    private final CarritoService carritoService;
    private final EmailPublisher emailPublisher;
    private final MonitorPublisher monitorPublisher;

    public PagoResultadoListener(ObjectMapper objectMapper,
                                 CarritoService carritoService,
                                 EmailPublisher emailPublisher,
                                 MonitorPublisher monitorPublisher) {
        this.objectMapper = objectMapper;
        this.carritoService = carritoService;
        this.emailPublisher = emailPublisher;
        this.monitorPublisher = monitorPublisher;
    }

    @RabbitListener(queues = "${app.rabbitmq.queue-pago-resultado:pedidos360.pagos.resultado}")
    public void recibirResultadoPago(Message message,
                                     Channel channel,
                                     @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            PagoResultadoEvent evento = objectMapper.readValue(message.getBody(), PagoResultadoEvent.class);

            if (evento.pedidoId() == null) {
                throw new IllegalArgumentException("El evento de pago no incluye pedidoId");
            }

            EstadoPago estadoPago = EstadoPago.desde(evento.estadoPago());
            Pedido pedido = carritoService.actualizarEstadoPago(evento.pedidoId(), estadoPago);

            String destino = destinoEmail(evento, pedido);
            if (estadoPago == EstadoPago.APROBADO) {
                emailPublisher.publicarPagoAprobado(destino, "Cliente", pedido);
                monitorPublisher.publicarPagoAprobado(pedido);
            } else {
                emailPublisher.publicarPagoRechazado(destino, "Cliente", pedido);
                monitorPublisher.publicarPagoRechazado(pedido);
            }

            channel.basicAck(deliveryTag, false);
            log.info("Resultado de pago {} aplicado al pedido {} (ACK tag={})",
                    estadoPago, evento.pedidoId(), deliveryTag);

        } catch (Exception e) {
            log.error("Fallo al procesar el resultado de pago (tag={}): {}. Se deriva a la DLQ.",
                    deliveryTag, e.getMessage(), e);
            // requeue=false => RabbitMQ lo mueve a pedidos360.pagos.resultado.dlq
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private String destinoEmail(PagoResultadoEvent evento, Pedido pedido) {
        if (evento.emailCliente() != null && evento.emailCliente().contains("@")) {
            return evento.emailCliente();
        }
        if (pedido.getUsuarioId() != null && pedido.getUsuarioId().contains("@")) {
            return pedido.getUsuarioId();
        }
        return "cliente@pedidos360.cl";
    }
}
