package com.pedidos360.pagos.service;

import com.pedidos360.pagos.dto.PagoResultadoEvent;
import com.pedidos360.pagos.dto.SolicitudPago;
import com.pedidos360.pagos.exception.TransaccionFraudeException;
import com.pedidos360.pagos.messaging.PagoEventPublisher;
import com.pedidos360.pagos.model.EstadoPago;
import com.pedidos360.pagos.model.MetodoPago;
import com.pedidos360.pagos.model.Pago;
import com.pedidos360.pagos.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Lógica de negocio de ms-pagos.
 *
 * El microservicio es autónomo: recibe la solicitud DIRECTAMENTE del frontend,
 * decide el resultado, lo persiste y publica el evento en RabbitMQ para que
 * ms-carrito actualice el pedido. No existe ninguna llamada HTTP entre
 * microservicios (sin proxy en el backend).
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);

    private final PagoRepository pagoRepository;
    private final AntifraudeService antifraudeService;
    private final WebpayService webpayService;
    private final PagoEventPublisher eventPublisher;

    public PagoService(PagoRepository pagoRepository,
                       AntifraudeService antifraudeService,
                       WebpayService webpayService,
                       PagoEventPublisher eventPublisher) {
        this.pagoRepository = pagoRepository;
        this.antifraudeService = antifraudeService;
        this.webpayService = webpayService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(noRollbackFor = TransaccionFraudeException.class)
    public Pago registrar(SolicitudPago solicitud) {
        if (solicitud == null || solicitud.pedidoId() == null) {
            throw new IllegalArgumentException("Debe indicar el pedido a pagar");
        }
        if (solicitud.monto() == null || solicitud.monto() <= 0) {
            throw new IllegalArgumentException("El monto a pagar debe ser mayor a 0");
        }

        if (pagoRepository.existsByPedidoId(solicitud.pedidoId())) {
            Pago existente = pagoRepository.findByPedidoId(solicitud.pedidoId()).orElseThrow();
            throw new IllegalStateException(
                    "El pedido " + solicitud.pedidoId() + " ya tiene un pago procesado (estado: "
                            + existente.getEstado() + ")");
        }

        MetodoPago metodoPago = MetodoPago.desde(solicitud.metodoPago());
        String tarjetaLimpia = solicitud.numeroTarjetaLimpio();
        String enmascarada = AntifraudeService.enmascararTarjeta(tarjetaLimpia);

        // --- Verificación de Antifraude (>3 veces en 10 seg) ---
        try {
            if (tarjetaLimpia != null && !tarjetaLimpia.isBlank()) {
                antifraudeService.verificarTransaccion(
                        tarjetaLimpia,
                        solicitud.usuarioId(),
                        solicitud.pedidoId(),
                        solicitud.monto()
                );
            }
        } catch (TransaccionFraudeException e) {
            Pago pagoBloqueado = new Pago(
                    solicitud.pedidoId(),
                    solicitud.usuarioId() == null ? "desconocido" : solicitud.usuarioId(),
                    solicitud.monto(),
                    metodoPago,
                    EstadoPago.BLOQUEADO_POR_FRAUDE,
                    e.getMessage(),
                    enmascarada
            );
            pagoRepository.save(pagoBloqueado);

            if (e.getAlerta() != null) {
                eventPublisher.publicarAlertaFraude(e.getAlerta());
            }
            publicarResultado(solicitud, metodoPago, EstadoPago.BLOQUEADO_POR_FRAUDE, e.getMessage());
            throw e;
        }

        // --- Procesamiento de Pago ---
        EstadoPago estado;
        String motivo = null;

        if (metodoPago == MetodoPago.WEBPAY && solicitud.tokenWebpay() != null) {
            var webpayResult = webpayService.confirmarTransaccion(solicitud.tokenWebpay());
            estado = "AUTHORIZED".equals(webpayResult.status()) ? EstadoPago.APROBADO : EstadoPago.RECHAZADO;
            motivo = estado == EstadoPago.APROBADO
                    ? "Aprobado por Webpay Plus (Código: " + webpayResult.authorizationCode() + ")"
                    : "Rechazado por Webpay";
        } else {
            boolean rechazado = solicitud.esRechazoSimulado();
            estado = rechazado ? EstadoPago.RECHAZADO : EstadoPago.APROBADO;
            motivo = rechazado ? "Pago rechazado por el banco (simulación)" : null;
        }

        Pago pago = new Pago(
                solicitud.pedidoId(),
                solicitud.usuarioId() == null ? "desconocido" : solicitud.usuarioId(),
                solicitud.monto(),
                metodoPago,
                estado,
                motivo,
                enmascarada
        );

        Pago guardado = pagoRepository.save(pago);
        log.info("Pago registrado exitosamente para pedido {} [estado={}]", solicitud.pedidoId(), estado);

        publicarResultado(solicitud, metodoPago, estado, motivo);

        return guardado;
    }

    /**
     * Publica el resultado como evento RabbitMQ (ms-carrito lo consume).
     */
    private void publicarResultado(SolicitudPago solicitud, MetodoPago metodoPago,
                                   EstadoPago estado, String motivo) {
        eventPublisher.publicarResultadoPago(PagoResultadoEvent.desde(
                solicitud.pedidoId(),
                solicitud.usuarioId(),
                solicitud.emailCliente(),
                solicitud.monto(),
                metodoPago.name(),
                estado.name(),
                motivo
        ));
    }

    public Pago obtenerPorPedido(Long pedidoId) {
        if (pedidoId == null) {
            throw new IllegalArgumentException("Debe indicar un pedido");
        }
        return pagoRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new NoSuchElementException("Pago no encontrado para el pedido: " + pedidoId));
    }
}
