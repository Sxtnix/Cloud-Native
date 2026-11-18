package com.pedidos360.pagos.controller;

import com.pedidos360.pagos.dto.SolicitudPago;
import com.pedidos360.pagos.exception.TransaccionFraudeException;
import com.pedidos360.pagos.model.AlertaFraude;
import com.pedidos360.pagos.model.Pago;
import com.pedidos360.pagos.service.AntifraudeService;
import com.pedidos360.pagos.service.PagoService;
import com.pedidos360.pagos.service.WebpayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * API REST de ms-pagos.
 * Desacoplado: el frontend y servicios externos interactúan directamente con este controlador.
 * No requiere proxy en backend ni API Gateway intermediario.
 */
@RestController
public class PagoController {

    private final PagoService pagoService;
    private final AntifraudeService antifraudeService;
    private final WebpayService webpayService;

    public PagoController(PagoService pagoService,
                          AntifraudeService antifraudeService,
                          WebpayService webpayService) {
        this.pagoService = pagoService;
        this.antifraudeService = antifraudeService;
        this.webpayService = webpayService;
    }

    /**
     * Procesa y registra un pago directamente (lo invoca el frontend).
     */
    @PostMapping(value = {"/pagos", "/pagos/procesar"})
    public ResponseEntity<Pago> pagar(@RequestBody SolicitudPago solicitud) {
        Pago pago = pagoService.registrar(solicitud);
        return ResponseEntity.status(HttpStatus.CREATED).body(pago);
    }

    /**
     * Consulta el pago de un pedido específico.
     */
    @GetMapping("/pagos/pedido/{pedidoId}")
    public ResponseEntity<Pago> obtenerPorPedido(@PathVariable Long pedidoId) {
        return ResponseEntity.ok(pagoService.obtenerPorPedido(pedidoId));
    }

    /**
     * Lista alertas de fraude detectadas (para el Dashboard y Monitoreo).
     */
    @GetMapping("/pagos/alertas-fraude")
    public ResponseEntity<List<AlertaFraude>> listarAlertasFraude() {
        return ResponseEntity.ok(antifraudeService.listarAlertas());
    }

    /**
     * Inicia transacción Webpay Plus (+5 puntos extra).
     */
    @PostMapping("/pagos/webpay/iniciar")
    public ResponseEntity<?> iniciarWebpay(@RequestBody Map<String, Object> payload) {
        Long pedidoId = payload.get("pedidoId") != null ? Long.valueOf(payload.get("pedidoId").toString()) : null;
        Double monto = payload.get("monto") != null ? Double.valueOf(payload.get("monto").toString()) : 0.0;
        String returnUrl = payload.get("returnUrl") != null ? payload.get("returnUrl").toString() : "http://localhost:4200/checkout";

        var res = webpayService.iniciarTransaccion(pedidoId, monto, returnUrl);
        return ResponseEntity.ok(res);
    }

    /**
     * Confirma transacción Webpay Plus (+5 puntos extra).
     */
    @PostMapping("/pagos/webpay/confirmar")
    public ResponseEntity<?> confirmarWebpay(@RequestBody Map<String, String> payload) {
        String token = payload.get("token");
        var res = webpayService.confirmarTransaccion(token);
        return ResponseEntity.ok(res);
    }

    // ------------------------------------------------------------- excepciones

    @ExceptionHandler(TransaccionFraudeException.class)
    public ResponseEntity<?> manejarFraude(TransaccionFraudeException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "error", "FRAUDE_DETECTADO",
                "mensaje", ex.getMessage(),
                "bloqueado", true
        ));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> manejarNoEncontrado(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> manejarSolicitudInvalida(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
