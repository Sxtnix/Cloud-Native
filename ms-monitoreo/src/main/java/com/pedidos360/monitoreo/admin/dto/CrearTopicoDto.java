package com.pedidos360.monitoreo.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/**
 * DTO para la creación y configuración de tópicos en Apache Kafka (EP5).
 * Permite definir particiones, factor de replicación, niveles de retención y cleanup policy.
 */
public record CrearTopicoDto(
        @NotBlank(message = "El nombre del tópico no puede estar vacío")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre del tópico solo puede contener caracteres alfanuméricos, '.', '-' o '_'")
        String nombre,

        @Min(value = 1, message = "Debe tener al menos 1 partición")
        int particiones,

        short factorReplicacion,

        Long retencionMs,

        String cleanupPolicy,

        Map<String, String> configuracionesAdicionales
) {
    public CrearTopicoDto(String nombre, int particiones, short factorReplicacion) {
        this(nombre, particiones, factorReplicacion, 604800000L, "delete", null);
    }
}
