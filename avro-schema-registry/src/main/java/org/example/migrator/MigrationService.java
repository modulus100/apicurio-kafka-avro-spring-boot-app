package org.example.migrator;

import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MigrationService {

    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class);

    @Value("${app.kafka.topics:}")
    private String topics;

    @Value("${schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${schema.registry.group}")
    private String registryGroup;

    @Value("${schema.registry.bearer.issuer-endpoint-url}")
    private String issuerEndpointUrl;

    @Value("${schema.registry.bearer.client-id}")
    private String clientId;

    @Value("${schema.registry.bearer.client-secret}")
    private String clientSecret;

    @Value("${schema.registry.bearer.scope:}")
    private String scope;

    @Value("${schema.io.registerRoot:classpath:avro/kafka-topic}")
    private String registerRoot;

    public void registerSchema() throws Exception {
        List<String> topicList = resolveTopics();
        if (topicList.isEmpty()) {
            logger.warn("No topics configured or discovered. Set 'app.kafka.topics' or ensure schemas exist under '{}'.", registerRoot);
            return;
        }

        SchemaRegistryClient client = createSchemaRegistryClient();
        for (String topic : topicList) {
            registerSchemasFromClasspath(client, topic);
        }
    }

    private SchemaRegistryClient createSchemaRegistryClient() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("bearer.auth.credentials.source", "OAUTHBEARER");
        configs.put("bearer.auth.issuer.endpoint.url", issuerEndpointUrl);
        configs.put("bearer.auth.client.id", clientId);
        configs.put("bearer.auth.client.secret", clientSecret);
        if (!scope.isBlank()) {
            configs.put("bearer.auth-scope", scope);
        }

        return new CachedSchemaRegistryClient(
                schemaRegistryUrl,
                16,
                configs,
                Map.of("X-Registry-GroupId", registryGroup)
        );
    }

    private void registerSchemasFromClasspath(SchemaRegistryClient client, String topic) throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String root = registerRoot.substring("classpath:".length());
        String pattern = String.format("classpath*:%s/%s/*.avsc", root, topic);
        Resource[] resources = resolver.getResources(pattern);

        if (resources.length == 0) {
            logger.warn("[{}] No AVSC files found for pattern '{}'.", topic, pattern);
            return;
        }

        for (Resource resource : resources) {
            String fileName = resource.getFilename() != null ? resource.getFilename() : "unknown";
            try {
                String avsc = readResource(resource);
                Schema avroSchema = new Schema.Parser().parse(avsc);
                AvroSchema confluentAvroSchema = new AvroSchema(avroSchema);
                String subject = topic + "-" + avroSchema.getFullName(); // TopicRecordNameStrategy

                try {
                    int id = client.getId(subject, confluentAvroSchema);
                    logger.info("[{}] Schema already exists for subject={}, id={}", topic, subject, id);
                } catch (Exception e) {
                    int id = client.register(subject, confluentAvroSchema);
                    logger.info("[{}] Schema registered for subject={}, id={}", topic, subject, id);
                }
            } catch (Exception e) {
                logger.error("[{}] Failed to register schema '{}': {}", topic, fileName, e.getMessage());
            }
        }
    }

    private String readResource(Resource resource) throws Exception {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> resolveTopics() throws Exception {
        // Check for explicitly configured topics
        if (!topics.isBlank()) {
            return Arrays.stream(topics.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .sorted()
                    .collect(Collectors.toList());
        }

        // Auto-discover topics from classpath
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        String root = registerRoot.substring("classpath:".length());
        Resource[] resources = resolver.getResources("classpath*:" + root + "/*/*.avsc");
        return Arrays.stream(resources)
                .map(this::parentDirName)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private String parentDirName(Resource res) {
        try {
            String url = res.getURL().toString();
            int slash = url.lastIndexOf('/');
            if (slash < 0) return null;
            String parentPath = url.substring(0, slash);
            int parentSlash = parentPath.lastIndexOf('/');
            if (parentSlash < 0) return null;
            return parentPath.substring(parentSlash + 1);
        } catch (Exception e) {
            return null;
        }
    }
}