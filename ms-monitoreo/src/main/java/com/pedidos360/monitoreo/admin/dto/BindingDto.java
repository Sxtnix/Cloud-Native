package com.pedidos360.monitoreo.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para la creación de Bindings entre Colas y Exchanges en RabbitMQ (EP3).
 */
public record BindingDto(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Size(min = 3, max = 100, message = "Longitud de nombre de cola inválida")
        String cola,

        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Size(min = 3, max = 100, message = "Longitud de nombre de exchange inválida")
        String exchange,

        @NotBlank(message = "La routing key es obligatoria")
        String routingKey
) {
}
