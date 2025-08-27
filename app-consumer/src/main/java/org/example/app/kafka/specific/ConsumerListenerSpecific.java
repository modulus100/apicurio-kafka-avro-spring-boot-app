package org.example.app.kafka.specific;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.example.avro.Greeting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.apache.avro.Schema;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "app.examples.specific", name = "enabled", havingValue = "true")
public class ConsumerListenerSpecific {
    private static final Logger log = LoggerFactory.getLogger(ConsumerListenerSpecific.class);

    @Value("${app.kafka.topic:demo-topic}")
    private String topic;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${spring.kafka.properties.bearer.auth.issuer.endpoint.url}")
    private String issuerEndpointUrl;

    @Value("${spring.kafka.properties.bearer.auth.client.id}")
    private String clientId;

    @Value("${spring.kafka.properties.bearer.auth.client.secret}")
    private String clientSecret;

    @Value("${spring.kafka.properties.bearer.auth.scope:}")
    private String scope;

    @KafkaListener(topics = "${app.kafka.topic:demo-topic}", groupId = "${spring.kafka.consumer.group-id:demo-group}")
    public void listen(ConsumerRecord<String, Greeting> record) {
        Greeting value = record.value();
        Headers headers = record.headers();
        String ceId = header(headers, "ce-id");
        String ceSource = header(headers, "ce-source");

//        Integer schemaId = null;
        if (value != null) {
            try {
//                schemaId = resolveSchemaId(topic + "-value", value.getSchema());
            } catch (Exception e) {
                log.warn("[Specific] Failed to resolve schema ID: {}", e.toString());
            }
        }
        if (value != null) {
            log.info("[Specific] Received: message='{}', timestamp='{}', schemaId='{}', ce-id='{}', ce-source='{}' from {}-{}@{}",
                    value.getMessage(), value.getTimestamp(), "none", ceId, ceSource,
                    record.topic(), record.partition(), record.offset());
        } else {
            log.warn("[Specific] Received null record from {}-{}@{}", record.topic(), record.partition(), record.offset());
        }
    }

    private String header(Headers headers, String key) {
        Header h = headers.lastHeader(key);
        return h != null ? new String(h.value(), StandardCharsets.UTF_8) : null;
    }

    private Integer resolveSchemaId(String subject, Schema writerSchema) throws IOException, RestClientException {
        Map<String, Object> configs = new HashMap<>();
        configs.put("bearer.auth.credentials.source", "OAUTHBEARER");
        configs.put("bearer.auth.issuer.endpoint.url", issuerEndpointUrl);
        configs.put("bearer.auth.client.id", clientId);
        configs.put("bearer.auth.client.secret", clientSecret);
        if (scope != null && !scope.isBlank()) {
            configs.put("bearer.auth.scope", scope);
        }
        try (CachedSchemaRegistryClient client = new CachedSchemaRegistryClient(schemaRegistryUrl, 100, configs)) {
            return client.getId(subject, new AvroSchema(writerSchema));
        }
    }
}
