package com.pedidos360.monitoreo.analytics.model;

import java.time.Instant;

/**
 * Evento de interacción del usuario (clickstream) capturado por Kafka.
 */
public record EventoNavegacion(
        String usuarioId,
        String tipoEvento, // VER_PRODUCTO, BUSQUEDA, AGREGAR_CARRITO, APLICAR_CUPON
        Long productoId,
        String nombreProducto,
        String categoria,
        Double precio,
        Instant timestamp
) {
    public EventoNavegacion {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
