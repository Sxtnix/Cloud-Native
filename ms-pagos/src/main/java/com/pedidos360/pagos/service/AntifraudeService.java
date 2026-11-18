package com.pedidos360.pagos.service;

import com.pedidos360.pagos.exception.TransaccionFraudeException;
import com.pedidos360.pagos.model.AlertaFraude;
import com.pedidos360.pagos.repository.AlertaFraudeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Servicio de Seguridad y Prevención de Fraude.
 * Regla Evaluativa: "detectar si una misma tarjeta de crédito se repite más de 3 veces
 * en un intervalo de 10 segundos, bloqueando la transacción de forma preventiva para
 * evitar robos masivos y registrando la alerta correspondiente en el sistema."
 */
@Service
public class AntifraudeService {

    private static final Logger log = LoggerFactory.getLogger(AntifraudeService.class);

    private final AlertaFraudeRepository alertaRepository;

    // Mapa en memoria para ventana deslizante: Tarjeta Normalizada -> Lista de Instantes
    private final Map<String, List<Instant>> intentosPorTarjeta = new ConcurrentHashMap<>();

    public AntifraudeService(AlertaFraudeRepository alertaRepository) {
        this.alertaRepository = alertaRepository;
    }

    /**
     * Evalúa si la tarjeta presenta comportamiento sospechoso (más de 3 intentos en 10 seg).
     * @throws TransaccionFraudeException si se detecta fraude masivo.
     */
    public void verificarTransaccion(String numeroTarjeta, String usuarioId, Long pedidoId, Double monto) {
        if (numeroTarjeta == null || numeroTarjeta.isBlank()) {
            return;
        }

        String normalizada = numeroTarjeta.replaceAll("[\\s-]", "").trim();
        if (normalizada.length() < 6) {
            return;
        }

        Instant ahora = Instant.now();
        Instant ventanaLimite = ahora.minusSeconds(10);

        List<Instant> intentos = intentosPorTarjeta.compute(normalizada, (tarjeta, lista) -> {
            if (lista == null) {
                lista = new CopyOnWriteArrayList<>();
            }
            // Eliminar intentos fuera de la ventana de 10 segundos
            lista.removeIf(ts -> ts.isBefore(ventanaLimite));
            lista.add(ahora);
            return lista;
        });

        int cantidadIntentos = intentos.size();

        // Si la tarjeta se repite más de 3 veces en 10 segundos (es decir, a partir del 4to intento)
        if (cantidadIntentos > 3) {
            String enmascarada = enmascararTarjeta(normalizada);
            String motivo = String.format(
                    "Alerta Antifraude: La tarjeta %s se repitió %d veces en un intervalo de 10 segundos. " +
                    "Transacción bloqueada preventivamente para evitar robos masivos.",
                    enmascarada, cantidadIntentos);

            AlertaFraude alerta = new AlertaFraude(
                    enmascarada,
                    usuarioId != null ? usuarioId : "anonimo",
                    pedidoId,
                    monto,
                    cantidadIntentos,
                    motivo
            );
            alertaRepository.save(alerta);

            log.warn("🚨 [ANTIFRAUDE ACTIVADO] {}", motivo);

            throw new TransaccionFraudeException(motivo, alerta);
        }
    }

    public List<AlertaFraude> listarAlertas() {
        return alertaRepository.findTop50ByOrderByFechaDesc();
    }

    public static String enmascararTarjeta(String normalizada) {
        if (normalizada == null || normalizada.length() < 4) {
            return "****";
        }
        String ultimos4 = normalizada.substring(normalizada.length() - 4);
        return "**** **** **** " + ultimos4;
    }
}
