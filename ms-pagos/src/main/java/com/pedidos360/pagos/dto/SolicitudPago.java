package com.pedidos360.pagos.dto;

/**
 * Solicitud de procesamiento de pago recibida directamente desde el frontend
 * o clientes autorizados (sin proxy en el backend).
 */
public record SolicitudPago(
        Long pedidoId,
        String usuarioId,
        Double monto,
        String metodoPago,
        Boolean simularRechazo,
        String numeroTarjeta,
        String titular,
        String vencimiento,
        String cvv,
        String tokenWebpay,
        String emailCliente
) {

    /** Constructor corto usado en pruebas y clientes que solo simulan el pago. */
    public SolicitudPago(Long pedidoId, String usuarioId, Double monto,
                         String metodoPago, Boolean simularRechazo) {
        this(pedidoId, usuarioId, monto, metodoPago, simularRechazo,
                null, null, null, null, null, null);
    }

    public boolean esRechazoSimulado() {
        return Boolean.TRUE.equals(simularRechazo);
    }

    public String numeroTarjetaLimpio() {
        if (numeroTarjeta == null) return null;
        return numeroTarjeta.replaceAll("[\\s-]", "").trim();
    }
}
