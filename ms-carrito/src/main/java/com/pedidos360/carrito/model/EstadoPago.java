package com.pedidos360.carrito.model;

/**
 * Estado del pago de un pedido.
 */
public enum EstadoPago {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    BLOQUEADO_POR_FRAUDE,
    PENDIENTE_WEBPAY;

    public static EstadoPago desde(String valor) {
        if (valor == null || valor.isBlank()) {
            return RECHAZADO;
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return RECHAZADO;
        }
    }
}
