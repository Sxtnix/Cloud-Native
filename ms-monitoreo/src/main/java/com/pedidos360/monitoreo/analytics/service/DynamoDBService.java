package com.pedidos360.monitoreo.analytics.service;

import com.pedidos360.monitoreo.analytics.model.EventoNavegacion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persistencia NoSQL real en Amazon DynamoDB (+2 puntos extra).
 *
 * Usa el SDK oficial de AWS (software.amazon.awssdk:dynamodb). En local apunta
 * a DynamoDB Local (contenedor `dynamodb-local`, puerto 8000); en AWS usa el
 * endpoint y las credenciales del entorno (variables AWS_* / rol IAM).
 *
 * Tabla: pedidos360_navegacion
 *   PK usuarioId (S) + SK eventoId (S) -> historial de clickstream por usuario.
 *
 * Resiliencia: si DynamoDB no está disponible, se mantiene una copia en
 * memoria para que el dashboard y las recomendaciones sigan funcionando
 * (el servicio nunca se cae por la base NoSQL).
 */
@Service
public class DynamoDBService {

    private static final Logger log = LoggerFactory.getLogger(DynamoDBService.class);

    private static final String TABLA = "pedidos360_navegacion";
    private static final String ATTR_USUARIO = "usuarioId";
    private static final String ATTR_EVENTO = "eventoId";

    @Value("${app.dynamodb.endpoint:http://localhost:8000}")
    private String dynamoDbEndpoint;

    @Value("${app.dynamodb.region:us-east-1}")
    private String region;

    @Value("${app.dynamodb.table:pedidos360_navegacion}")
    private String tabla;

    @Value("${app.dynamodb.enabled:true}")
    private boolean habilitado;

    /** Copia en memoria (fallback / caché de lectura). */
    private final Map<String, List<EventoNavegacion>> eventosPorUsuario = new ConcurrentHashMap<>();

    private volatile DynamoDbClient cliente;
    private volatile boolean tablaLista = false;
    private volatile boolean disponible = false;

    // ---------------------------------------------------------------- escritura

    public void guardarEventoNavegacion(EventoNavegacion evento) {
        if (evento == null) return;
        String uid = usuarioId(evento);
        eventosPorUsuario.computeIfAbsent(uid, k -> new CopyOnWriteArrayList<>()).add(evento);

        DynamoDbClient db = cliente();
        if (db == null) {
            log.info("[DynamoDB] Evento guardado solo en memoria para usuario {}: {} - {}",
                    uid, evento.tipoEvento(), evento.nombreProducto());
            return;
        }

        try {
            asegurarTabla(db);
            db.putItem(PutItemRequest.builder()
                    .tableName(tabla)
                    .item(aItem(evento, uid))
                    .build());
            log.info("[DynamoDB NoSQL] Evento persistido para usuario {}: {} - {}",
                    uid, evento.tipoEvento(), evento.nombreProducto());
        } catch (Exception e) {
            disponible = false;
            log.warn("[DynamoDB] No se pudo persistir el evento (se conserva en memoria): {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ lectura

    public List<EventoNavegacion> obtenerHistorialUsuario(String usuarioId) {
        if (usuarioId == null) return Collections.emptyList();

        List<EventoNavegacion> memoria = eventosPorUsuario.getOrDefault(usuarioId, Collections.emptyList());
        DynamoDbClient db = cliente();
        if (db == null) return memoria;

        try {
            asegurarTabla(db);
            var respuesta = db.query(QueryRequest.builder()
                    .tableName(tabla)
                    .keyConditionExpression(ATTR_USUARIO + " = :u")
                    .expressionAttributeValues(Map.of(":u", AttributeValue.fromS(usuarioId)))
                    .scanIndexForward(false) // más reciente primero
                    .limit(50)
                    .build());

            List<EventoNavegacion> eventos = new ArrayList<>();
            for (Map<String, AttributeValue> item : respuesta.items()) {
                eventos.add(aEvento(item));
            }
            return eventos.isEmpty() ? memoria : eventos;
        } catch (Exception e) {
            log.warn("[DynamoDB] No se pudo consultar el historial de {}: {}", usuarioId, e.getMessage());
            return memoria;
        }
    }

    public Map<String, Integer> obtenerConteoPorCategoria() {
        Map<String, Integer> conteo = new HashMap<>();
        for (List<EventoNavegacion> lista : eventosPorUsuario.values()) {
            for (EventoNavegacion ev : lista) {
                if (ev.categoria() != null && !ev.categoria().isBlank()) {
                    conteo.merge(ev.categoria(), 1, Integer::sum);
                }
            }
        }

        DynamoDbClient db = cliente();
        if (db == null) return conteo;

        try {
            asegurarTabla(db);
            var respuesta = db.scan(ScanRequest.builder()
                    .tableName(tabla)
                    .projectionExpression("categoria")
                    .build());

            Map<String, Integer> desdeDynamo = new HashMap<>();
            for (Map<String, AttributeValue> item : respuesta.items()) {
                AttributeValue cat = item.get("categoria");
                if (cat != null && cat.s() != null && !cat.s().isBlank()) {
                    desdeDynamo.merge(cat.s(), 1, Integer::sum);
                }
            }
            return desdeDynamo.isEmpty() ? conteo : desdeDynamo;
        } catch (Exception e) {
            log.warn("[DynamoDB] No se pudo contar por categoría: {}", e.getMessage());
            return conteo;
        }
    }

    public int totalEventosGuardados() {
        DynamoDbClient db = cliente();
        if (db != null) {
            try {
                asegurarTabla(db);
                var respuesta = db.scan(ScanRequest.builder()
                        .tableName(tabla)
                        .select(Select.COUNT)
                        .build());
                return Math.toIntExact(respuesta.count() == null ? 0 : respuesta.count());
            } catch (Exception e) {
                log.warn("[DynamoDB] No se pudo contar los eventos: {}", e.getMessage());
            }
        }
        return eventosPorUsuario.values().stream().mapToInt(List::size).sum();
    }

    // ------------------------------------------------------- cliente y tabla

    private DynamoDbClient cliente() {
        if (!habilitado) return null;
        if (cliente != null) return cliente;

        synchronized (this) {
            if (cliente != null) return cliente;
            try {
                var builder = DynamoDbClient.builder()
                        .region(Region.of(region))
                        .overrideConfiguration(ClientOverrideConfiguration.builder()
                                .apiCallTimeout(Duration.ofSeconds(3))
                                .apiCallAttemptTimeout(Duration.ofSeconds(2))
                                .retryPolicy(RetryPolicy.none())
                                .build());

                if (dynamoDbEndpoint != null && !dynamoDbEndpoint.isBlank()) {
                    builder.endpointOverride(URI.create(dynamoDbEndpoint))
                            .credentialsProvider(StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create("local", "local")));
                }

                cliente = builder.build();
                log.info("[DynamoDB] Cliente inicializado (endpoint={}, region={}, tabla={})",
                        dynamoDbEndpoint, region, tabla);
                return cliente;
            } catch (Exception e) {
                log.warn("[DynamoDB] No se pudo crear el cliente, se usa memoria: {}", e.getMessage());
                return null;
            }
        }
    }

    private void asegurarTabla(DynamoDbClient db) {
        if (tablaLista) return;

        synchronized (this) {
            if (tablaLista) return;
            try {
                db.describeTable(DescribeTableRequest.builder().tableName(tabla).build());
                tablaLista = true;
                disponible = true;
                return;
            } catch (ResourceNotFoundException e) {
                // se crea abajo
            } catch (Exception e) {
                throw new IllegalStateException("DynamoDB no disponible: " + e.getMessage(), e);
            }

            db.createTable(CreateTableRequest.builder()
                    .tableName(tabla)
                    .provisionedThroughput(
                            software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput.builder()
                                    .readCapacityUnits(5L)
                                    .writeCapacityUnits(5L)
                                    .build())
                    .attributeDefinitions(
                            software.amazon.awssdk.services.dynamodb.model.AttributeDefinition.builder()
                                    .attributeName(ATTR_USUARIO)
                                    .attributeType(ScalarAttributeType.S)
                                    .build(),
                            software.amazon.awssdk.services.dynamodb.model.AttributeDefinition.builder()
                                    .attributeName(ATTR_EVENTO)
                                    .attributeType(ScalarAttributeType.S)
                                    .build())
                    .keySchema(
                            KeySchemaElement.builder().attributeName(ATTR_USUARIO).keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName(ATTR_EVENTO).keyType(KeyType.RANGE).build())
                    .build());

            tablaLista = true;
            disponible = true;
            log.info("[DynamoDB] Tabla {} creada con PK {} + SK {}", tabla, ATTR_USUARIO, ATTR_EVENTO);
        }
    }

    /** Elimina la tabla (útil para reiniciar la demo). */
    public void reiniciarTabla() {
        DynamoDbClient db = cliente();
        eventosPorUsuario.clear();
        if (db == null) return;
        try {
            db.deleteTable(DeleteTableRequest.builder().tableName(tabla).build());
            tablaLista = false;
            log.info("[DynamoDB] Tabla {} eliminada", tabla);
        } catch (Exception e) {
            log.warn("[DynamoDB] No se pudo eliminar la tabla: {}", e.getMessage());
        }
    }

    public boolean isDisponible() {
        return disponible;
    }

    // -------------------------------------------------------------- mapeos

    private String usuarioId(EventoNavegacion evento) {
        return evento.usuarioId() == null || evento.usuarioId().isBlank() ? "anonimo" : evento.usuarioId();
    }

    private Map<String, AttributeValue> aItem(EventoNavegacion evento, String uid) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(ATTR_USUARIO, AttributeValue.fromS(uid));
        item.put(ATTR_EVENTO, AttributeValue.fromS(
                uid + "#" + (evento.timestamp() == null ? Instant.now() : evento.timestamp()) + "#"
                        + UUID.randomUUID().toString().substring(0, 8)));
        item.put("tipoEvento", AttributeValue.fromS(String.valueOf(evento.tipoEvento())));
        item.put("timestamp", AttributeValue.fromS(
                (evento.timestamp() == null ? Instant.now() : evento.timestamp()).toString()));
        if (evento.productoId() != null) {
            item.put("productoId", AttributeValue.fromN(evento.productoId().toString()));
        }
        if (evento.nombreProducto() != null) {
            item.put("nombreProducto", AttributeValue.fromS(evento.nombreProducto()));
        }
        if (evento.categoria() != null) {
            item.put("categoria", AttributeValue.fromS(evento.categoria()));
        }
        if (evento.precio() != null) {
            item.put("precio", AttributeValue.fromN(evento.precio().toString()));
        }
        return item;
    }

    private EventoNavegacion aEvento(Map<String, AttributeValue> item) {
        return new EventoNavegacion(
                texto(item, ATTR_USUARIO),
                texto(item, "tipoEvento"),
                numeroLong(item, "productoId"),
                texto(item, "nombreProducto"),
                texto(item, "categoria"),
                numeroDouble(item, "precio"),
                fecha(item, "timestamp")
        );
    }

    private String texto(Map<String, AttributeValue> item, String clave) {
        AttributeValue valor = item.get(clave);
        return valor == null ? null : valor.s();
    }

    private Long numeroLong(Map<String, AttributeValue> item, String clave) {
        AttributeValue valor = item.get(clave);
        return valor == null || valor.n() == null ? null : Long.valueOf(valor.n());
    }

    private Double numeroDouble(Map<String, AttributeValue> item, String clave) {
        AttributeValue valor = item.get(clave);
        return valor == null || valor.n() == null ? null : Double.valueOf(valor.n());
    }

    private Instant fecha(Map<String, AttributeValue> item, String clave) {
        AttributeValue valor = item.get(clave);
        if (valor == null || valor.s() == null) return Instant.now();
        try {
            return Instant.parse(valor.s());
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
