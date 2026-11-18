package com.pedidos360.monitoreo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para la creación y administración de colas en RabbitMQ (EP3).
 * Incluye validaciones para evitar nombres vacíos o configuraciones inválidas.
 */
public record ColaDto(
        @NotBlank(message = "El nombre de la cola no puede estar vacío")
        @Size(min = 3, max = 100, message = "El nombre de la cola debe tener entre 3 y 100 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre de la cola solo puede incluir caracteres alfanuméricos, '.', '-' o '_'")
        String nombre,

        boolean durable,
        boolean autoDelete,
        boolean exclusive,
        String deadLetterExchange,
        String deadLetterRoutingKey
) {
    public ColaDto(String nombre, boolean durable) {
        this(nombre, durable, false, false, null, null);
    }
}
