package com.pedidos360.pagos.model;

/**
 * Resultado del pago gestionado por ms-pagos.
 */
public enum EstadoPago {
    APROBADO("Aprobado"),
    RECHAZADO("Rechazado"),
    BLOQUEADO_POR_FRAUDE("Bloqueado por Prevención de Fraude"),
    PENDIENTE_WEBPAY("Pendiente Confirmación Webpay");

    private final String etiqueta;

    EstadoPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static EstadoPago desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Debe indicar un estado de pago");
        }
        try {
            return valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de pago no válido: " + valor);
        }
    }
}
