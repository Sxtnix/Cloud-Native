package com.pedidos360.monitoreo.admin.controller;

import com.pedidos360.monitoreo.admin.dto.CrearTopicoDto;
import com.pedidos360.monitoreo.admin.service.KafkaAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controlador REST para la administración de Apache Kafka (EP5).
 */
@RestController
@RequestMapping("/admin/kafka")
public class KafkaAdminController {

    private final KafkaAdminService kafkaAdminService;

    public KafkaAdminController(KafkaAdminService kafkaAdminService) {
        this.kafkaAdminService = kafkaAdminService;
    }

    @GetMapping("/topics")
    public ResponseEntity<List<Map<String, Object>>> listarTopicos() {
        return ResponseEntity.ok(kafkaAdminService.listarTopicos());
    }

    @PostMapping("/topics")
    public ResponseEntity<?> crearTopico(@Valid @RequestBody CrearTopicoDto dto) {
        kafkaAdminService.crearTopico(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Tópico Kafka creado exitosamente",
                "topico", dto.nombre(),
                "particiones", dto.particiones(),
                "factorReplicacion", dto.factorReplicacion()
        ));
    }

    @DeleteMapping("/topics/{nombreTopico}")
    public ResponseEntity<?> eliminarTopico(@PathVariable String nombreTopico) {
        kafkaAdminService.eliminarTopico(nombreTopico);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Tópico eliminado exitosamente de Kafka",
                "topico", nombreTopico
        ));
    }

    @GetMapping("/consumer-groups")
    public ResponseEntity<List<Map<String, Object>>> listarGrupos() {
        return ResponseEntity.ok(kafkaAdminService.listarGruposConsumidores());
    }
}
