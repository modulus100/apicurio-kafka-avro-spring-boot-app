package org.example.migrator;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class MigrationService {

    @Value("${app.kafka.topic}")
    private String topic;

    @Value("${schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${schema.registry.bearer.issuer-endpoint-url}")
    private String issuerEndpointUrl;

    @Value("${schema.registry.bearer.client-id}")
    private String clientId;

    @Value("${schema.registry.bearer.client-secret}")
    private String clientSecret;

    @Value("${schema.registry.bearer.scope:}")
    private String scope;

    public void registerSchema() throws Exception {
        // Build SR client with OAuth2 (Keycloak) properties
        Map<String, Object> configs = new HashMap<>();
        configs.put("bearer.auth.credentials.source", "OAUTHBEARER");
        configs.put("bearer.auth.issuer.endpoint.url", issuerEndpointUrl);
        configs.put("bearer.auth.client.id", clientId);
        configs.put("bearer.auth.client.secret", clientSecret);
        if (scope != null && !scope.isBlank()) {
            configs.put("bearer.auth.scope", scope);
        }

        try (var client = new CachedSchemaRegistryClient(schemaRegistryUrl, 16, configs)) {
            // Load AVSC from resources
            ClassPathResource cpr = new ClassPathResource("avro/greeting.avsc");
            try (InputStream is = cpr.getInputStream()) {
                String avsc = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Schema avroSchema = new Schema.Parser().parse(avsc);
                AvroSchema confluentAvroSchema = new AvroSchema(avroSchema);

                String subject = topic + "-value"; // matches TopicNameStrategy default

                // Try get existing ID; if not found, register
                int id;
                try {
                    id = client.getId(subject, confluentAvroSchema);
                    System.out.println("Schema already exists for subject=" + subject + ", id=" + id);
                } catch (RestClientException rce) {
                    // 404 or similar -> register
                    id = client.register(subject, confluentAvroSchema);
                    System.out.println("Schema registered subject=" + subject + ", id=" + id);
                }
            }
        }
    }
}
