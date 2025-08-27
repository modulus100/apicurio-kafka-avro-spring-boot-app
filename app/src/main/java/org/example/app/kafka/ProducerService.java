package org.example.app.kafka;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "app.examples.generic", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ProducerService {
    private static final Logger log = LoggerFactory.getLogger(ProducerService.class);

    private final Schema greetingSchema;

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic:demo-topic}")
    private String topic;

    public ProducerService(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topic:demo-topic}") String topic,
            @Value("${spring.kafka.properties.schema.registry.url}") String schemaRegistryUrl,
            @Value("${spring.kafka.properties.bearer.auth.issuer.endpoint.url}") String issuerEndpointUrl,
            @Value("${spring.kafka.properties.bearer.auth.client.id}") String clientId,
            @Value("${spring.kafka.properties.bearer.auth.client.secret}") String clientSecret,
            @Value("${spring.kafka.properties.bearer.auth.scope:}") String scope
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic; // ensure available for subject computation
        this.greetingSchema = fetchSchemaFromRegistry(
                schemaRegistryUrl,
                issuerEndpointUrl,
                clientId,
                clientSecret,
                scope
        );
    }

    private Schema fetchSchemaFromRegistry(String schemaRegistryUrl,
                                           String issuerEndpointUrl,
                                           String clientId,
                                           String clientSecret,
                                           String scope) {
        String subject = topic + "-value"; // matches TopicNameStrategy default

        Map<String, Object> configs = new HashMap<>();
        configs.put("bearer.auth.credentials.source", "OAUTHBEARER");
        configs.put("bearer.auth.issuer.endpoint.url", issuerEndpointUrl);
        configs.put("bearer.auth.client.id", clientId);
        configs.put("bearer.auth.client.secret", clientSecret);
        if (scope != null && !scope.isBlank()) {
            configs.put("bearer.auth.scope", scope);
        }

        try (CachedSchemaRegistryClient client = new CachedSchemaRegistryClient(schemaRegistryUrl, 16, configs)) {
            // Fetch latest schema metadata for subject
            var metadata = client.getLatestSchemaMetadata(subject);
            String schemaString = metadata.getSchema();
            // If references are used, you might need to resolve them here; not needed for this PoC
            return new Schema.Parser().parse(schemaString);
        } catch (RestClientException | RuntimeException | java.io.IOException e) {
            throw new IllegalStateException("Failed to fetch Avro schema from SR for subject: " + subject, e);
        }
    }

    // Send a message every 5 seconds
    @Scheduled(fixedDelay = 5000, initialDelay = 3000)
    public void sendMessage() {
        GenericRecord record = new GenericData.Record(greetingSchema);
        String message = "Hello from Spring Boot (Avro)";
        long ts = Instant.now().toEpochMilli();
        record.put("message", message + " @ " + ts);
        record.put("timestamp", ts);

        kafkaTemplate.send(topic, record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send Avro record", ex);
            } else if (result != null && result.getRecordMetadata() != null) {
                log.info("Sent Avro record to {}-{}@{}", 
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.info("Sent Avro record");
            }
        });
    }
}
