package com.pedidos360.email.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.email.service.EmailService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifica la confirmación manual (ACK/NACK) del consumidor de correos (EP3):
 * - Procesamiento correcto -> basicAck.
 * - Error -> basicNack(requeue=false) para derivar el mensaje a la DLQ.
 */
class EmailListenerTest {

    private static final long DELIVERY_TAG = 42L;

    private EmailService emailService;
    private Channel channel;
    private EmailListener emailListener;

    @BeforeEach
    void setUp() {
        emailService = mock(EmailService.class);
        channel = mock(Channel.class);
        emailListener = new EmailListener(new ObjectMapper(), emailService);
    }

    private Message mensajeConJson(String json) {
        return new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }

    @Test
    void deberiaEnviarCorreoDeTextoYConfirmarConAck() throws Exception {
        String json = """
                {"destino":"cliente@pedidos360.cl",
                 "asunto":"Pedidos360 - Pedido #1 registrado",
                 "cuerpo":"Tu pedido fue registrado.",
                 "tipo":"PEDIDO_REGISTRADO"}
                """;

        emailListener.recibirMensaje(mensajeConJson(json), channel, DELIVERY_TAG);

        verify(emailService).enviarCorreo(
                "cliente@pedidos360.cl",
                "Pedidos360 - Pedido #1 registrado",
                "Tu pedido fue registrado.");
        verify(emailService, never()).enviarHtml(anyString(), anyString(), anyString());
        verify(channel).basicAck(DELIVERY_TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void deberiaEnviarHtmlYConfirmarConAck() throws Exception {
        String json = """
                {"destino":"cliente@pedidos360.cl",
                 "asunto":"Pedidos360 - Pago aprobado",
                 "cuerpo":"<!doctype html><html><body>Hola</body></html>",
                 "tipo":"PAGO_APROBADO"}
                """;

        emailListener.recibirMensaje(mensajeConJson(json), channel, DELIVERY_TAG);

        verify(emailService).enviarHtml(
                "cliente@pedidos360.cl",
                "Pedidos360 - Pago aprobado",
                "<!doctype html><html><body>Hola</body></html>");
        verify(emailService, never()).enviarCorreo(anyString(), anyString(), anyString());
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void deberiaHacerNackSinRequeue_siElJsonEsInvalido() throws Exception {
        emailListener.recibirMensaje(mensajeConJson("{esto no es json"), channel, DELIVERY_TAG);

        verifyNoInteractions(emailService);
        verify(channel).basicNack(eq(DELIVERY_TAG), eq(false), eq(false));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void deberiaHacerNackSinRequeue_siElEnvioFalla() throws Exception {
        doThrow(new RuntimeException("SMTP caido"))
                .when(emailService).enviarCorreo(anyString(), anyString(), anyString());
        String json = """
                {"destino":"cliente@pedidos360.cl","asunto":"Hola","cuerpo":"Texto","tipo":"PEDIDO_REGISTRADO"}
                """;

        emailListener.recibirMensaje(mensajeConJson(json), channel, DELIVERY_TAG);

        verify(channel).basicNack(eq(DELIVERY_TAG), eq(false), eq(false));
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
