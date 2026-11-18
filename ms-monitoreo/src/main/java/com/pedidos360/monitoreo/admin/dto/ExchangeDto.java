package com.pedidos360.monitoreo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para la creación y administración de Exchanges en RabbitMQ (EP3).
 */
public record ExchangeDto(
        @NotBlank(message = "El nombre del exchange no puede estar vacío")
        @Size(min = 3, max = 100, message = "El nombre del exchange debe tener entre 3 y 100 caracteres")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre solo puede incluir caracteres alfanuméricos, '.', '-' o '_'")
        String nombre,

        @NotBlank(message = "El tipo de exchange es obligatorio")
        @Pattern(regexp = "^(?i)(direct|topic|fanout|headers)$", message = "El tipo de exchange debe ser: direct, topic, fanout o headers")
        String tipo,

        boolean durable,
        boolean autoDelete
) {
    public ExchangeDto(String nombre, String tipo, boolean durable) {
        this(nombre, tipo, durable, false);
    }
}
