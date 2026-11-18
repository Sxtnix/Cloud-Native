package com.pedidos360.carrito.model;

import java.util.Arrays;

/**
 * Métodos de pago ofrecidos en el checkout de Pedidos360.
 */
public enum MetodoPago {
    TARJETA_CREDITO("Tarjeta de crédito"),
    TARJETA_DEBITO("Tarjeta de débito"),
    TRANSFERENCIA("Transferencia bancaria"),
    WEBPAY("Webpay Plus");

    private final String etiqueta;

    MetodoPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static MetodoPago desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Debe seleccionar un método de pago");
        }
        String normalizado = valor.trim().toUpperCase().replace('-', '_');
        return Arrays.stream(values())
                .filter(m -> m.name().equals(normalizado) || m.etiqueta.equalsIgnoreCase(valor.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Método de pago no válido: " + valor));
    }
}
