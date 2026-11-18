package com.pedidos360.pagos.service;

import com.pedidos360.pagos.exception.TransaccionFraudeException;
import com.pedidos360.pagos.model.AlertaFraude;
import com.pedidos360.pagos.repository.AlertaFraudeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas de la regla antifraude: una misma tarjeta no puede repetirse más de
 * 3 veces en 10 segundos (el 4.º intento se bloquea y registra la alerta).
 */
class AntifraudeServiceTest {

    private AlertaFraudeRepository alertaRepository;
    private AntifraudeService antifraudeService;

    private static final String TARJETA = "4111222233334444";

    @BeforeEach
    void setUp() {
        alertaRepository = mock(AlertaFraudeRepository.class);
        antifraudeService = new AntifraudeService(alertaRepository);
    }

    @Test
    void deberiaPermitirHastaTresIntentosDeLaMismaTarjeta() {
        assertDoesNotThrow(() -> antifraudeService.verificarTransaccion(TARJETA, "u1", 1L, 1000.0));
        assertDoesNotThrow(() -> antifraudeService.verificarTransaccion(TARJETA, "u1", 2L, 1000.0));
        assertDoesNotThrow(() -> antifraudeService.verificarTransaccion(TARJETA, "u1", 3L, 1000.0));

        verify(alertaRepository, never()).save(any(AlertaFraude.class));
    }

    @Test
    void deberiaBloquearYRegistrarAlertaEnElCuartoIntento() {
        antifraudeService.verificarTransaccion(TARJETA, "u1", 1L, 1000.0);
        antifraudeService.verificarTransaccion(TARJETA, "u1", 2L, 1000.0);
        antifraudeService.verificarTransaccion(TARJETA, "u1", 3L, 1000.0);

        TransaccionFraudeException error = assertThrows(TransaccionFraudeException.class,
                () -> antifraudeService.verificarTransaccion(TARJETA, "u1", 4L, 1000.0));

        assertTrue(error.getMessage().contains("4444"));
        assertNotNull(error.getAlerta());
        verify(alertaRepository).save(any(AlertaFraude.class));
    }

    @Test
    void tarjetasDistintasNoSeAcumulan() {
        for (int i = 1; i <= 4; i++) {
            final String tarjeta = "411122223333000" + i;
            final Long pedidoId = (long) i;
            assertDoesNotThrow(() -> antifraudeService.verificarTransaccion(tarjeta, "u1", pedidoId, 1000.0));
        }
        verify(alertaRepository, never()).save(any(AlertaFraude.class));
    }

    @Test
    void tarjetaVaciaNoSeEvalua() {
        assertDoesNotThrow(() -> antifraudeService.verificarTransaccion(null, "u1", 1L, 1000.0));
        assertDoesNotThrow(() -> antifraudeService.verificarTransaccion("  ", "u1", 1L, 1000.0));
    }

    @Test
    void enmascararTarjetaDeberiaMostrarSoloLosUltimosCuatroDigitos() {
        assertEquals("**** **** **** 4444", AntifraudeService.enmascararTarjeta(TARJETA));
        assertEquals("****", AntifraudeService.enmascararTarjeta(null));
    }
}
