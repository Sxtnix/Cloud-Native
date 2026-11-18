package com.pedidos360.pagos.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Registro de alerta de seguridad por detección de patrones fraudulentos.
 * Regla: Mismo número de tarjeta repetido más de 3 veces en 10 segundos.
 */
@Entity
@Table(name = "alertas_fraude")
public class AlertaFraude {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tarjetaEnmascarada;

    @Column(nullable = false)
    private String usuarioId;

    private Long pedidoId;

    private Double monto;

    @Column(nullable = false)
    private int intentosEn10Segundos;

    @Column(nullable = false)
    private Instant fecha;

    @Column(length = 500)
    private String descripcion;

    public AlertaFraude() {
    }

    public AlertaFraude(String tarjetaEnmascarada, String usuarioId, Long pedidoId,
                        Double monto, int intentosEn10Segundos, String descripcion) {
        this.tarjetaEnmascarada = tarjetaEnmascarada;
        this.usuarioId = usuarioId != null ? usuarioId : "desconocido";
        this.pedidoId = pedidoId;
        this.monto = monto;
        this.intentosEn10Segundos = intentosEn10Segundos;
        this.fecha = Instant.now();
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public String getTarjetaEnmascarada() {
        return tarjetaEnmascarada;
    }

    public String getUsuarioId() {
        return usuarioId;
    }

    public Long getPedidoId() {
        return pedidoId;
    }

    public Double getMonto() {
        return monto;
    }

    public int getIntentosEn10Segundos() {
        return intentosEn10Segundos;
    }

    public Instant getFecha() {
        return fecha;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
