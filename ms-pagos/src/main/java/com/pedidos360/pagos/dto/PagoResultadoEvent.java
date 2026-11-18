package com.pedidos360.pagos.dto;

import java.time.Instant;

/**
 * Evento con el resultado del pago. Lo publica ms-pagos en RabbitMQ y lo
 * consume ms-carrito para actualizar el pedido (sin proxy HTTP).
 */
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
    public static PagoResultadoEvent desde(Long pedidoId, String usuarioId, String emailCliente,
                                           Double monto, String metodoPago, String estadoPago,
                                           String motivo) {
        return new PagoResultadoEvent(
                "PAGO_PROCESADO",
                pedidoId,
                usuarioId,
                emailCliente,
                monto,
                metodoPago,
                estadoPago,
                motivo,
                Instant.now(),
                "ms-pagos");
    }
}
