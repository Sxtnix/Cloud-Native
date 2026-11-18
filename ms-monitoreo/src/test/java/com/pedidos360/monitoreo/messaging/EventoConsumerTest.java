package com.pedidos360.monitoreo.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.monitoreo.analytics.service.DynamoDBService;
import com.pedidos360.monitoreo.model.EventoMonitor;
import com.pedidos360.monitoreo.store.EventoStore;
import com.pedidos360.monitoreo.websocket.MetricasWebSocketHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EventoConsumerTest {

    private EventoStore store;
    private EventoConsumer consumer;

    private static final String JSON_EVENTO = """
            {
              "tipo": "PAGO_APROBADO",
              "pedidoId": 7,
              "usuarioId": "oid-1",
              "monto": 899980.0,
              "estadoPedido": "CONFIRMADO",
              "estadoPago": "APROBADO",
              "metodoPago": "TARJETA_CREDITO",
              "detalle": "Pago aprobado por ms-pagos",
              "fecha": "2026-10-05T10:00:00Z",
              "servicio": "ms-carrito"
            }
            """;

    @BeforeEach
    void configurar() {
        store = new EventoStore();
        consumer = new EventoConsumer(
                store,
                new ObjectMapper(),
                mock(DynamoDBService.class),
                mock(MetricasWebSocketHandler.class));
    }

    @Test
    void escucharMonitoreo_deberiaGuardarElEventoConSuParticionYOffset() {
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>(EventoConsumer.TOPICO_MONITOREO, 0, 42L, "7", JSON_EVENTO);

        consumer.escucharMonitoreo(record);

        EventoStore.Estadisticas stats = store.estadisticas();
        assertEquals(1, stats.totalRecibidos());

        EventoMonitor evento = store.listar(null).get(0);
        assertEquals("PAGO_APROBADO", evento.tipo());
        assertEquals(7L, evento.pedidoId());
        assertEquals("oid-1", evento.usuarioId());
        assertEquals(EventoConsumer.TOPICO_MONITOREO, evento.topico());
        assertEquals(0, evento.particion());
        assertEquals(42L, evento.offset());
    }

    @Test
    void escucharMonitoreo_deberiaIgnorarElMensajeIlegibleSinTumbarse() {
        ConsumerRecord<String, String> invalido =
                new ConsumerRecord<>(EventoConsumer.TOPICO_MONITOREO, 0, 1L, "7", "esto no es json {{");

        assertDoesNotThrow(() -> consumer.escucharMonitoreo(invalido));
        assertTrue(store.listar(null).isEmpty());
        assertEquals(0, store.estadisticas().totalRecibidos());
    }

    @Test
    void escucharNavegacion_deberiaTolerarPayloadsInvalidos() {
        ConsumerRecord<String, String> invalido =
                new ConsumerRecord<>(EventoConsumer.TOPICO_NAVEGACION, 0, 2L, "9", "{invalido");

        assertDoesNotThrow(() -> consumer.escucharNavegacion(invalido));
    }

    @Test
    void escucharAlertas_deberiaProcesarElMensajeSinErrores() {
        ConsumerRecord<String, String> alerta =
                new ConsumerRecord<>(EventoConsumer.TOPICO_ALERTAS, 0, 3L, "7",
                        "{\"tipo\":\"ALERTA_FRAUDE\",\"pedidoId\":7}");

        assertDoesNotThrow(() -> consumer.escucharAlertas(alerta));
    }
}
