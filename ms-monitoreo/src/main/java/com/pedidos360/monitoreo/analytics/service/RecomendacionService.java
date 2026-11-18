package com.pedidos360.monitoreo.analytics.service;

import com.pedidos360.monitoreo.analytics.model.EventoNavegacion;
import com.pedidos360.monitoreo.analytics.model.RecomendacionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Motor de Recomendaciones Dinámicas en Tiempo Real (Evaluación 3).
 * Consume los eventos históricos de clickstream de Kafka / DynamoDB para sugerir
 * productos afines al perfil de navegación reciente del usuario.
 */
@Service
public class RecomendacionService {

    private static final Logger log = LoggerFactory.getLogger(RecomendacionService.class);

    private final DynamoDBService dynamoDBService;

    // Catálogo base de referencia para sugerencias
    private static final List<RecomendacionDto> CATALOGO_SISTEMA = List.of(
            new RecomendacionDto(1L, "Cafetera Espresso Pro", "Café", 129990.0,
                    "https://images.unsplash.com/photo-1517668808822-9ebb02f2a0e6?w=600&auto=format&fit=crop&q=80",
                    "Basado en tu interés reciente en Café", 0.95),
            new RecomendacionDto(2L, "Molinillo de Café Cerámico", "Café", 24990.0,
                    "https://images.unsplash.com/photo-1589396575653-c09c794ff6a6?w=600&auto=format&fit=crop&q=80",
                    "Combina perfecto con cafeteras", 0.90),
            new RecomendacionDto(3L, "Taza Térmica Acero 450ml", "Accesorios", 14990.0,
                    "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?w=600&auto=format&fit=crop&q=80",
                    "Accesorio popular entre compradores", 0.82),
            new RecomendacionDto(4L, "Prensa Francesa 1L", "Café", 19990.0,
                    "https://images.unsplash.com/photo-1570968915860-54d5c301fa9f?w=600&auto=format&fit=crop&q=80",
                    "Tendencia en métodos de extracción", 0.88),
            new RecomendacionDto(5L, "Tetera Cuello de Cisne", "Accesorios", 29990.0,
                    "https://images.unsplash.com/photo-1576092768241-dec231879fc3?w=600&auto=format&fit=crop&q=80",
                    "Recomendado para amantes de las infusiones", 0.85),
            new RecomendacionDto(6L, "Balanza Digital de Precisión", "Accesorios", 18990.0,
                    "https://images.unsplash.com/photo-1509785307050-d4066910ec1e?w=600&auto=format&fit=crop&q=80",
                    "Complemento ideal para dosificación exacta", 0.79)
    );

    public RecomendacionService(DynamoDBService dynamoDBService) {
        this.dynamoDBService = dynamoDBService;
    }

    /**
     * Genera recomendaciones hiperpersonalizadas según el clickstream del usuario.
     */
    public List<RecomendacionDto> obtenerRecomendaciones(String usuarioId, String categoriaFiltro) {
        List<EventoNavegacion> historial = dynamoDBService.obtenerHistorialUsuario(usuarioId);

        // Si el usuario navegó por una categoría específica, priorizamos esa categoría
        String categoriaPreferida = categoriaFiltro;
        if ((categoriaPreferida == null || categoriaPreferida.isBlank()) && !historial.isEmpty()) {
            Map<String, Integer> afinidades = new HashMap<>();
            for (EventoNavegacion ev : historial) {
                if (ev.categoria() != null) {
                    afinidades.merge(ev.categoria(), 1, Integer::sum);
                }
            }
            categoriaPreferida = afinidades.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
        }

        if (categoriaPreferida != null && !categoriaPreferida.isBlank()) {
            final String cat = categoriaPreferida;
            List<RecomendacionDto> filtradas = CATALOGO_SISTEMA.stream()
                    .filter(p -> p.categoria().equalsIgnoreCase(cat))
                    .toList();
            if (!filtradas.isEmpty()) {
                log.info("Generadas {} recomendaciones afines para usuario {} en categoría {}",
                        filtradas.size(), usuarioId, cat);
                return filtradas;
            }
        }

        return CATALOGO_SISTEMA.subList(0, Math.min(4, CATALOGO_SISTEMA.size()));
    }
}
