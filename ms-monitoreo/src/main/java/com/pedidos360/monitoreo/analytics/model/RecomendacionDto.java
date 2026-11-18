package com.pedidos360.monitoreo.analytics.model;

/**
 * Recomendación dinámica de producto generada a partir del análisis en tiempo real.
 */
public record RecomendacionDto(
        Long productoId,
        String nombre,
        String categoria,
        Double precio,
        String imagenUrl,
        String motivo,
        double afinidad
) {
}
