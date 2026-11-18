package com.pedidos360.carrito.service;

import com.pedidos360.carrito.dto.AgregarItemRequest;
import com.pedidos360.carrito.integration.ProductoClient;
import com.pedidos360.carrito.messaging.MonitorPublisher;
import com.pedidos360.carrito.model.*;
import com.pedidos360.carrito.repository.CarritoRepository;
import com.pedidos360.carrito.repository.PedidoRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Lógica de negocio del carrito, el checkout y los pagos.
 *
 * Reglas clave:
 *  - El precio y el stock SIEMPRE vienen de ms-productos, nunca del frontend.
 *  - El total del pedido se calcula en el servidor a partir de los items.
 *  - El checkout solo CREA el pedido (queda pendiente de pago); el pago se
 *    procesa en ms-pagos (microservicio separado) y aquí solo se aplica el
 *    resultado sobre el pedido.
 *  - Un pedido solo puede ser consultado o pagado por su propietario.
 */
@Service
public class CarritoService {

    private final CarritoRepository carritoRepository;
    private final PedidoRepository pedidoRepository;
    private final ProductoClient productoClient;
    private final MonitorPublisher monitorPublisher;

    public CarritoService(CarritoRepository carritoRepository,
                           PedidoRepository pedidoRepository,
                           ProductoClient productoClient,
                           MonitorPublisher monitorPublisher) {
        this.carritoRepository = carritoRepository;
        this.pedidoRepository = pedidoRepository;
        this.productoClient = productoClient;
        this.monitorPublisher = monitorPublisher;
    }

    /**
     * Obtiene el carrito activo del usuario. Si no existe, se crea uno nuevo vacio.
     */
    @Transactional
    public Carrito obtenerCarritoActivo(String usuarioId) {
        return carritoRepository.findByUsuarioIdAndEstado(usuarioId, EstadoCarrito.ACTIVO)
                .orElseGet(() -> carritoRepository.save(new Carrito(usuarioId)));
    }

    @Transactional
    public Carrito agregarItem(String usuarioId, AgregarItemRequest request) {
        if (request.cantidad() == null || request.cantidad() <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }

        ProductoClient.ProductoInfo producto = productoClient.buscarProducto(request.productoId());

        Carrito carrito = obtenerCarritoActivo(usuarioId);
        ItemCarrito existente = buscarItemPorProducto(carrito, producto.id());

        int cantidadTotal = request.cantidad() + (existente == null ? 0 : existente.getCantidad());
        if (!producto.conStockDisponible(cantidadTotal)) {
            throw new IllegalArgumentException(
                    "Stock insuficiente para \"" + producto.nombre() + "\": quedan " + producto.stock() + " unidades.");
        }

        if (existente != null) {
            // Mismo producto dos veces -> se acumula la cantidad en una sola linea.
            existente.setCantidad(cantidadTotal);
            existente.setImagenUrl(producto.imagenUrl());
        } else {
            // Precio, nombre e imagen tomados del catalogo, no de lo que envie el navegador.
            carrito.agregarItem(new ItemCarrito(
                    producto.id(), producto.nombre(), producto.precio(),
                    request.cantidad(), producto.imagenUrl()));
        }
        Carrito guardado = carritoRepository.save(carrito);

        // Clickstream (EP5): alimenta analitica y recomendaciones en ms-monitoreo.
        monitorPublisher.publicarNavegacion(usuarioId, "AGREGAR_CARRITO", producto.id(),
                producto.nombre(), producto.categoria(), producto.precio());

        return guardado;
    }

    @Transactional
    public Carrito actualizarCantidad(String usuarioId, Long itemId, Integer cantidad) {
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor a 0");
        }

        Carrito carrito = obtenerCarritoActivo(usuarioId);
        ItemCarrito item = buscarItemPorId(carrito, itemId);

        ProductoClient.ProductoInfo producto = productoClient.buscarProducto(item.getProductoId());
        if (!producto.conStockDisponible(cantidad)) {
            throw new IllegalArgumentException(
                    "Stock insuficiente para \"" + producto.nombre() + "\": quedan " + producto.stock() + " unidades.");
        }

        item.setCantidad(cantidad);
        return carritoRepository.save(carrito);
    }

    @Transactional
    public Carrito eliminarItem(String usuarioId, Long itemId) {
        Carrito carrito = obtenerCarritoActivo(usuarioId);
        boolean eliminado = carrito.getItems().removeIf(i -> i.getId().equals(itemId));
        if (!eliminado) {
            throw new NoSuchElementException("Item no encontrado en el carrito: " + itemId);
        }
        return carritoRepository.save(carrito);
    }

    /**
     * Convierte el carrito activo en un Pedido PENDIENTE DE PAGO (snapshot de
     * items, precios y total calculado en el servidor) y marca el carrito de
     * origen como FINALIZADO.
     *
     * El pedido NO queda pagado hasta que se procese el pago simulado.
     */
    @Transactional
    public Pedido checkout(String usuarioId, MetodoPago metodoPago) {
        if (metodoPago == null) {
            throw new IllegalArgumentException("Debe seleccionar un método de pago");
        }

        Carrito carrito = obtenerCarritoActivo(usuarioId);
        if (carrito.getItems().isEmpty()) {
            throw new IllegalStateException("No se puede confirmar la compra: el carrito está vacío");
        }

        Pedido pedido = new Pedido(usuarioId);
        carrito.getItems().forEach(item -> pedido.agregarItem(ItemPedido.desdeItemCarrito(item)));
        pedido.setTotal(carrito.calcularTotal());
        pedido.setMetodoPago(metodoPago);
        pedido.setEstado(EstadoPedido.PENDIENTE_PAGO);
        pedido.setEstadoPago(EstadoPago.PENDIENTE);

        Pedido pedidoGuardado = pedidoRepository.save(pedido);

        carrito.setEstado(EstadoCarrito.FINALIZADO);
        carritoRepository.save(carrito);

        return pedidoGuardado;
    }

    /**
     * Aplica al pedido el resultado del pago que publica ms-pagos por RabbitMQ.
     *
     * No hay llamada HTTP entre servicios (sin proxy en el backend): el
     * resultado llega como evento y aquí solo se actualiza el pedido.
     *
     *  - APROBADO: estadoPago=APROBADO y estado=CONFIRMADO (pagado).
     *  - RECHAZADO / BLOQUEADO_POR_FRAUDE: se cancela el pedido y se restaura
     *    el carrito del usuario con los mismos items para que pueda reintentar.
     *
     * Es idempotente: si el evento se entrega dos veces, no vuelve a restaurar
     * el carrito ni duplica items.
     */
    @Transactional
    public Pedido actualizarEstadoPago(Long pedidoId, EstadoPago estadoPago) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado: " + pedidoId));

        if (estadoPago == null) {
            throw new IllegalArgumentException("El estado de pago es obligatorio");
        }

        // Idempotencia: si RabbitMQ reentrega el mismo evento, no se vuelve a aplicar.
        if (estadoPago == pedido.getEstadoPago()) {
            return pedido;
        }

        if (estadoPago == EstadoPago.APROBADO) {
            pedido.setEstadoPago(EstadoPago.APROBADO);
            pedido.setEstado(EstadoPedido.CONFIRMADO);
        } else {
            pedido.setEstadoPago(estadoPago);
            pedido.setEstado(EstadoPedido.CANCELADO);
            restaurarCarrito(pedido.getUsuarioId(), pedido);
        }
        return pedidoRepository.save(pedido);
    }

    public java.util.List<Pedido> listarPedidos(String usuarioId) {
        return pedidoRepository.findByUsuarioIdOrderByFechaDesc(usuarioId);
    }

    /**
     * Transiciones permitidas del ciclo de vida logistico. El alta y el pago
     * del pedido no se hacen por aqui: van por checkout y por el pago simulado.
     */
    private static final Map<EstadoPedido, Set<EstadoPedido>> TRANSICIONES = Map.of(
            EstadoPedido.CONFIRMADO, Set.of(EstadoPedido.EN_PREPARACION),
            EstadoPedido.EN_PREPARACION, Set.of(EstadoPedido.ENVIADO),
            EstadoPedido.ENVIADO, Set.of(EstadoPedido.ENTREGADO)
    );

    /**
     * Avanza un pedido pagado en su seguimiento logistico
     * (pagado -> en preparacion -> enviado -> entregado).
     *
     * Solo el dueno del pedido puede hacerlo y la transicion debe estar
     * permitida; cualquier otro salto devuelve IllegalStateException (400).
     */
    @Transactional
    public Pedido actualizarEstado(String usuarioId, Long pedidoId, EstadoPedido nuevoEstado) {
        if (nuevoEstado == null) {
            throw new IllegalArgumentException("Debe indicar el estado del pedido");
        }

        Pedido pedido = obtenerPedidoPropietario(usuarioId, pedidoId);
        Set<EstadoPedido> permitidos = TRANSICIONES.getOrDefault(pedido.getEstado(), Set.of());

        if (!permitidos.contains(nuevoEstado)) {
            throw new IllegalStateException(
                    "Transicion de estado no permitida: " + pedido.getEstado() + " -> " + nuevoEstado);
        }

        pedido.setEstado(nuevoEstado);
        return pedidoRepository.save(pedido);
    }

    /** Solo el dueno del pedido puede verlo (evita consultas entre usuarios). */
    public Pedido obtenerPedido(String usuarioId, Long id) {
        return obtenerPedidoPropietario(usuarioId, id);
    }

    private Pedido obtenerPedidoPropietario(String usuarioId, Long id) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado: " + id));
        if (!pedido.getUsuarioId().equals(usuarioId)) {
            throw new AccessDeniedException("No tienes permiso para ver este pedido");
        }
        return pedido;
    }

    /** Devuelve los items del pedido rechazado al carrito activo del usuario. */
    private void restaurarCarrito(String usuarioId, Pedido pedido) {
        Carrito carrito = obtenerCarritoActivo(usuarioId);
        for (ItemPedido item : pedido.getItems()) {
            ItemCarrito repetido = buscarItemPorProducto(carrito, item.getProductoId());
            if (repetido != null) {
                repetido.setCantidad(repetido.getCantidad() + item.getCantidad());
            } else {
                carrito.agregarItem(new ItemCarrito(
                        item.getProductoId(), item.getNombreProducto(), item.getPrecioUnitario(),
                        item.getCantidad(), item.getImagenUrl()));
            }
        }
        carritoRepository.save(carrito);
    }

    private ItemCarrito buscarItemPorProducto(Carrito carrito, Long productoId) {
        return carrito.getItems().stream()
                .filter(i -> productoId != null && productoId.equals(i.getProductoId()))
                .findFirst()
                .orElse(null);
    }

    private ItemCarrito buscarItemPorId(Carrito carrito, Long itemId) {
        return carrito.getItems().stream()
                .filter(i -> i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Item no encontrado en el carrito: " + itemId));
    }
}
