package org.example.migrator;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import org.apache.avro.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
            // Scan and load all AVSC files from classpath avro/ directory
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:/avro/*.avsc");

            if (resources == null || resources.length == 0) {
                System.out.println("No AVSC schemas found under classpath:/avro. Nothing to register.");
                return;
            }

            System.out.println("Found " + resources.length + " AVSC schema(s). Registering against subject '" + (topic + "-value") + "'.");

            for (Resource res : resources) {
                String filename = res.getFilename();
                try (InputStream is = res.getInputStream()) {
                    String avsc = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    Schema avroSchema = new Schema.Parser().parse(avsc);
                    AvroSchema confluentAvroSchema = new AvroSchema(avroSchema);

                    String subject = topic + "-value"; // matches TopicNameStrategy default

                    // Try get existing ID; if not found, register
                    int id;
                    try {
                        id = client.getId(subject, confluentAvroSchema);
                        System.out.println("[" + filename + "] Schema already exists for subject=" + subject + ", id=" + id);
                    } catch (RestClientException rce) {
                        id = client.register(subject, confluentAvroSchema);
                        System.out.println("[" + filename + "] Schema registered subject=" + subject + ", id=" + id);
                    }
                } catch (Exception e) {
                    System.err.println("Failed to register schema from resource '" + filename + "': " + e.getMessage());
                }
            }
        }
    }
}
