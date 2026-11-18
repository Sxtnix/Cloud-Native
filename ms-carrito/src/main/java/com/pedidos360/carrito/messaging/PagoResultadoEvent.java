package com.pedidos360.carrito.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * Evento de resultado de pago que publica ms-pagos y consume ms-carrito.
 *
 * Reemplaza la antigua llamada HTTP ms-pagos -> ms-carrito: ahora el
 * resultado viaja por RabbitMQ (sin proxy ni llamadas HTTP entre servicios).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PagoResultadoEvent(
        String tipo,
        Long pedidoId,
        String usuarioId,
        String emailCliente,
        Double monto,
        String metodoPago,
        String estadoPago,
        String motivo,
        Instant fecha,
        String servicio
) {
}
