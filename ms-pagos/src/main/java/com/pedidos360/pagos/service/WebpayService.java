package com.pedidos360.pagos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de integración y simulación de Transbank Webpay Plus (+5 puntos extra).
 * Emula el flujo oficial de Webpay Plus: initTransaction -> redirect -> commitTransaction.
 */
@Service
public class WebpayService {

    private static final Logger log = LoggerFactory.getLogger(WebpayService.class);

    public record WebpayInitResult(String token, String url) {}
    public record WebpayCommitResult(String token, String status, Double amount, String buyOrder, String authorizationCode) {}

    private final Map<String, WebpayInitData> transaccionesActivas = new ConcurrentHashMap<>();

    public record WebpayInitData(Long pedidoId, String buyOrder, String sessionId, Double amount, String returnUrl) {}

    public WebpayInitResult iniciarTransaccion(Long pedidoId, Double monto, String returnUrl) {
        String token = "ws_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        String buyOrder = "OC-" + (pedidoId != null ? pedidoId : System.currentTimeMillis());
        String sessionId = "SESS-" + UUID.randomUUID().toString().substring(0, 8);

        transaccionesActivas.put(token, new WebpayInitData(pedidoId, buyOrder, sessionId, monto, returnUrl));
        log.info("Webpay Plus: Transacción iniciada [token={}, orden={}, monto={}]", token, buyOrder, monto);

        // URL de redirección hacia pasarela Webpay (o vista de pago simulada)
        String redirectUrl = "https://webpay3gint.transbank.cl/webpayserver/initTransaction?token_ws=" + token;
        return new WebpayInitResult(token, redirectUrl);
    }

    public WebpayCommitResult confirmarTransaccion(String token) {
        WebpayInitData data = transaccionesActivas.get(token);
        if (data == null) {
            throw new IllegalArgumentException("Token de Webpay no encontrado o ya procesado: " + token);
        }

        String authCode = String.valueOf((int) (Math.random() * 900000) + 100000);
        log.info("Webpay Plus: Transacción confirmada exitosamente [token={}, authCode={}]", token, authCode);

        transaccionesActivas.remove(token);
        return new WebpayCommitResult(token, "AUTHORIZED", data.amount(), data.buyOrder(), authCode);
    }
}
