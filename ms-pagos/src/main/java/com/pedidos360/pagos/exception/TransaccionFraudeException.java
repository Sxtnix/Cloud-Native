package com.pedidos360.pagos.exception;

import com.pedidos360.pagos.model.AlertaFraude;

/**
 * Se lanza cuando la regla antifraude bloquea una transacción.
 * Lleva la alerta registrada para que la capa de servicio pueda publicarla
 * en Kafka sin mezclar mensajería dentro de la lógica de detección.
 */
public class TransaccionFraudeException extends RuntimeException {

    private final transient AlertaFraude alerta;

    public TransaccionFraudeException(String message) {
        this(message, null);
    }

    public TransaccionFraudeException(String message, AlertaFraude alerta) {
        super(message);
        this.alerta = alerta;
    }

    public AlertaFraude getAlerta() {
        return alerta;
    }
}
