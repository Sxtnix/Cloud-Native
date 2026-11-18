package com.pedidos360.monitoreo.admin.controller;

import com.pedidos360.monitoreo.admin.dto.BindingDto;
import com.pedidos360.monitoreo.admin.dto.ColaDto;
import com.pedidos360.monitoreo.admin.dto.ExchangeDto;
import com.pedidos360.monitoreo.admin.service.RabbitAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST para la administración de RabbitMQ (EP3).
 * Rutas estandarizadas: /admin/rabbitmq/* y atajos REST /queues, /exchanges, /bindings.
 */
@RestController
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    public RabbitAdminController(RabbitAdminService rabbitAdminService) {
        this.rabbitAdminService = rabbitAdminService;
    }

    @PostMapping(value = {"/admin/rabbitmq/queues", "/queues"})
    public ResponseEntity<?> crearCola(@Valid @RequestBody ColaDto dto) {
        rabbitAdminService.crearCola(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Cola creada exitosamente en RabbitMQ",
                "cola", dto.nombre(),
                "durable", dto.durable()
        ));
    }

    @DeleteMapping(value = {"/admin/rabbitmq/queues/{nombre}", "/queues/{nombre}"})
    public ResponseEntity<?> eliminarCola(@PathVariable String nombre) {
        boolean eliminada = rabbitAdminService.eliminarCola(nombre);
        return ResponseEntity.ok(Map.of(
                "mensaje", eliminada ? "Cola eliminada correctamente" : "La cola no existía",
                "cola", nombre
        ));
    }

    @PostMapping(value = {"/admin/rabbitmq/exchanges", "/exchanges"})
    public ResponseEntity<?> crearExchange(@Valid @RequestBody ExchangeDto dto) {
        rabbitAdminService.crearExchange(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Exchange creado exitosamente en RabbitMQ",
                "exchange", dto.nombre(),
                "tipo", dto.tipo()
        ));
    }

    @DeleteMapping(value = {"/admin/rabbitmq/exchanges/{nombre}", "/exchanges/{nombre}"})
    public ResponseEntity<?> eliminarExchange(@PathVariable String nombre) {
        boolean eliminado = rabbitAdminService.eliminarExchange(nombre);
        return ResponseEntity.ok(Map.of(
                "mensaje", eliminado ? "Exchange eliminado correctamente" : "El exchange no existía",
                "exchange", nombre
        ));
    }

    @PostMapping(value = {"/admin/rabbitmq/bindings", "/bindings"})
    public ResponseEntity<?> crearBinding(@Valid @RequestBody BindingDto dto) {
        rabbitAdminService.crearBinding(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Binding creado exitosamente en RabbitMQ",
                "cola", dto.cola(),
                "exchange", dto.exchange(),
                "routingKey", dto.routingKey()
        ));
    }

    @DeleteMapping(value = {"/admin/rabbitmq/bindings", "/bindings"})
    public ResponseEntity<?> eliminarBinding(@RequestParam String cola,
                                             @RequestParam String exchange,
                                             @RequestParam String routingKey) {
        rabbitAdminService.eliminarBinding(cola, exchange, routingKey);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Binding eliminado correctamente"
        ));
    }

    @GetMapping(value = {"/admin/rabbitmq/estado", "/queues"})
    public ResponseEntity<?> obtenerEstado() {
        return ResponseEntity.ok(rabbitAdminService.obtenerEstado());
    }
}
