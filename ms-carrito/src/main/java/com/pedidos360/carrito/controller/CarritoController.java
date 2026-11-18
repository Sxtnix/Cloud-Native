package com.pedidos360.carrito.controller;

import com.pedidos360.carrito.dto.*;
import com.pedidos360.carrito.model.Carrito;
import com.pedidos360.carrito.model.EstadoPedido;
import com.pedidos360.carrito.model.MetodoPago;
import com.pedidos360.carrito.model.Pedido;
import com.pedidos360.carrito.messaging.EmailPublisher;
import com.pedidos360.carrito.messaging.MonitorPublisher;
import com.pedidos360.carrito.service.CarritoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * API del carrito, el checkout y el historial de pedidos.
 * Todos los recursos estan ligados al usuario autenticado en el JWT.
 *
 * El pago NO pasa por este microservicio (sin proxy en el backend): el
 * frontend llama directamente a ms-pagos y su resultado llega aqui como
 * evento RabbitMQ (ver PagoResultadoListener).
 *
 * Cada hecho de negocio se publica DOS veces:
 *  - RabbitMQ (EmailPublisher)  -> para ENTREGAR el correo (ms-email).
 *  - Kafka (MonitorPublisher)   -> para OBSERVARLO en monitoreo (ms-monitoreo).
 */
@RestController
public class CarritoController {

    private final CarritoService carritoService;
    private final EmailPublisher emailPublisher;
    private final MonitorPublisher monitorPublisher;

    public CarritoController(CarritoService carritoService,
                             EmailPublisher emailPublisher,
                             MonitorPublisher monitorPublisher) {
        this.carritoService = carritoService;
        this.emailPublisher = emailPublisher;
        this.monitorPublisher = monitorPublisher;
    }

    /**
     * Identificador del usuario: el claim "oid" de Azure AD. Si el token no lo
     * trae (p. ej. cuentas invitadas) se usa el "sub" como respaldo.
     */
    private String obtenerUsuarioId(Jwt jwt) {
        String oid = jwt.getClaimAsString("oid");
        if (oid == null || oid.isBlank()) {
            oid = jwt.getSubject();
        }
        if (oid == null || oid.isBlank()) {
            throw new IllegalStateException("El token no incluye un identificador de usuario (oid)");
        }
        return oid;
    }

    // ---------------------------------------------------------------- carrito

    @GetMapping("/carrito")
    public ResponseEntity<Carrito> obtenerCarrito(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(carritoService.obtenerCarritoActivo(obtenerUsuarioId(jwt)));
    }

    @PostMapping("/carrito/items")
    public ResponseEntity<Carrito> agregarItem(@AuthenticationPrincipal Jwt jwt,
                                                @RequestBody AgregarItemRequest request) {
        Carrito carrito = carritoService.agregarItem(obtenerUsuarioId(jwt), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(carrito);
    }

    @PutMapping("/carrito/items/{itemId}")
    public ResponseEntity<Carrito> actualizarCantidad(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable Long itemId,
                                                      @RequestBody ActualizarCantidadRequest request) {
        return ResponseEntity.ok(carritoService.actualizarCantidad(
                obtenerUsuarioId(jwt), itemId, request.cantidad()));
    }

    @DeleteMapping("/carrito/items/{itemId}")
    public ResponseEntity<Carrito> eliminarItem(@AuthenticationPrincipal Jwt jwt, @PathVariable Long itemId) {
        return ResponseEntity.ok(carritoService.eliminarItem(obtenerUsuarioId(jwt), itemId));
    }

    // --------------------------------------------------------------- checkout

    /**
     * Crea el pedido a partir del carrito. El pedido queda PENDIENTE DE PAGO;
     * el pago lo procesa directamente el frontend contra ms-pagos (puerto 8085)
     * y su resultado se aplica al pedido vía evento RabbitMQ.
     */
    @PostMapping("/carrito/checkout")
    public ResponseEntity<Pedido> checkout(@AuthenticationPrincipal Jwt jwt,
                                           @RequestBody CheckoutRequest request) {
        MetodoPago metodoPago = MetodoPago.desde(request == null ? null : request.metodoPago());
        Pedido pedido = carritoService.checkout(obtenerUsuarioId(jwt), metodoPago);

        // RabbitMQ -> ms-email envia la confirmacion de compra de forma asincrona.
        emailPublisher.publicarPedidoRegistrado(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"),
                pedido);
        // Kafka -> ms-monitoreo registra el evento en el log de monitoreo.
        monitorPublisher.publicarPedidoRegistrado(pedido);

        return ResponseEntity.status(HttpStatus.CREATED).body(pedido);
    }

    // --------------------------------------------------------------- pedidos

    @GetMapping("/pedidos")
    public ResponseEntity<List<Pedido>> listarPedidos(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(carritoService.listarPedidos(obtenerUsuarioId(jwt)));
    }

    @GetMapping("/pedidos/{id}")
    public ResponseEntity<Pedido> obtenerPedido(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(carritoService.obtenerPedido(obtenerUsuarioId(jwt), id));
    }

    /**
     * Avanza un pedido pagado en su seguimiento logistico:
     * CONFIRMADO -> EN_PREPARACION -> ENVIADO -> ENTREGADO.
     * El backend valida la transicion (400 si no esta permitida) y publica
     * el evento CAMBIO_ESTADO para avisar por correo.
     */
    @PutMapping("/pedidos/{id}/estado")
    public ResponseEntity<Pedido> actualizarEstado(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable Long id,
                                                   @RequestBody ActualizarEstadoRequest request) {
        EstadoPedido nuevoEstado = EstadoPedido.desde(request == null ? null : request.estado());
        Pedido pedido = carritoService.actualizarEstado(obtenerUsuarioId(jwt), id, nuevoEstado);

        emailPublisher.publicarCambioEstado(
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("name"),
                pedido);
        monitorPublisher.publicarCambioEstado(pedido);

        return ResponseEntity.ok(pedido);
    }

    // ------------------------------------------------------------- excepciones

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> manejarNoEncontrado(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> manejarSinPermiso(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ex.getMessage());
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<String> manejarSolicitudInvalida(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
