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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@Service
public class MigrationService {

    private static final Logger logger = LoggerFactory.getLogger(MigrationService.class);
    private static final String SUBJECT_SUFFIX = "-value";

    private static final String STRATEGY_TOPIC_NAME = "TopicNameStrategy";
    private static final String STRATEGY_TOPIC_RECORD_NAME = "TopicRecordNameStrategy";
    private static final String STRATEGY_RECORD_NAME = "RecordNameStrategy";

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

    @Value("${schema.subject.strategy:TopicRecordNameStrategy}")
    private String subjectStrategy;

    private final TopicsConfig topicsConfig;

    public MigrationService(TopicsConfig topicsConfig) {
        this.topicsConfig = topicsConfig;
    }

    public void registerSchema() throws Exception {
        SchemaRegistryClient client = createSchemaRegistryClient();
        // 1) If YAML mapping exists, honor it
        if (topicsConfig != null && topicsConfig.topics() != null && !topicsConfig.topics().isEmpty()) {
            registerFromMappings(client, topicsConfig);
            return;
        }

        // Config-only mode: no fallback discovery
        logger.error("No topics configured. Provide schema.topics mapping in kafka-topic-config.yml (imported via spring.config.import).");
    }

    private void registerFromMappings(SchemaRegistryClient client, TopicsConfig mappings) throws Exception {
        for (TopicsConfig.Topic t : mappings.topics()) {
            if (t.name() == null || t.name().isBlank() || t.directory() == null || t.directory().isBlank()) {
                logger.warn("Skipping invalid topic mapping entry: name='{}', directory='{}'", t.name(), t.directory());
                continue;
            }
            String topicName = t.name().trim();
            String dir = t.directory().trim();

            if (dir.startsWith("classpath:")) {
                String root = dir.substring("classpath:".length());
                PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
                String pattern = "classpath*:" + root + "/*.avsc";
                Resource[] resources = resolver.getResources(pattern);
                if (resources.length == 0) {
                    logger.warn("[{}] No AVSC resources found for pattern '{}'. Skipping.", topicName, pattern);
                    continue;
                }
                registerSchemas(client, topicName, Arrays.asList(resources), src -> readResource((Resource) src));
            } else {
                Path topicDir = Paths.get(dir);
                if (!Files.exists(topicDir)) {
                    logger.warn("[{}] Directory '{}' not found. Skipping.", topicName, topicDir.toAbsolutePath());
                    continue;
                }
                try (Stream<Path> pathStream = Files.list(topicDir)) {
                    List<Path> avscFiles = pathStream
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".avsc"))
                            .toList();
                    if (avscFiles.isEmpty()) {
                        logger.warn("[{}] No AVSC files found in '{}'. Skipping.", topicName, topicDir.toAbsolutePath());
                        continue;
                    }
                    registerSchemas(client, topicName, avscFiles, p -> Files.readString((Path) p, StandardCharsets.UTF_8));
                }
            }
        }
    }

    private SchemaRegistryClient createSchemaRegistryClient() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("bearer.auth.issuer.endpoint.url", issuerEndpointUrl);
        configs.put("bearer.auth.client.id", clientId);
        configs.put("bearer.auth.client.secret", clientSecret);
        if (scope != null && !scope.isBlank()) {
            configs.put("bearer.auth-scope", scope);
        }

        return new CachedSchemaRegistryClient(
                schemaRegistryUrl,
                16,
                configs,
                Map.of("X-Registry-GroupId", registryGroup)
        );
    }

    private String readResource(Resource resource) throws Exception {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void registerSchemas(SchemaRegistryClient client,
                                 String topicName,
                                 List<?> sources,
                                 ContentReader reader) throws Exception {
        for (Object src : sources) {
            String name = getSourceName(src);
            try {
                String avsc = reader.read(src);
                Schema avroSchema = new Schema.Parser().parse(avsc);
                AvroSchema confluentAvroSchema = new AvroSchema(avroSchema);
                String subject = computeSubject(topicName, avroSchema);

                try {
                    int id = client.getId(subject, confluentAvroSchema);
                    logger.info("[{}] Schema already exists for subject={}, id={}", topicName, subject, id);
                } catch (Exception e) {
                    int id = client.register(subject, confluentAvroSchema);
                    logger.info("[{}] Schema registered for subject={}, id={}", topicName, subject, id);
                }
            } catch (Exception e) {
                logger.error("[{}] Failed to register schema '{}': {}", topicName, name, e.getMessage());
            }
        }
    }

    private String computeSubject(String topicName, Schema avroSchema) {
        return switch (subjectStrategy) {
            case STRATEGY_TOPIC_NAME -> topicName + SUBJECT_SUFFIX;
            case STRATEGY_RECORD_NAME -> avroSchema.getFullName();
            case STRATEGY_TOPIC_RECORD_NAME -> topicName + "-" + avroSchema.getFullName();
            default -> topicName + "-" + avroSchema.getFullName();
        };
    }

    private String getSourceName(Object source) {
        if (source instanceof Path path) {
            return path.getFileName().toString();
        } else if (source instanceof Resource resource) {
            return resource.getFilename() != null ? resource.getFilename() : resource.getDescription();
        }
        return "unknown";
    }

    @FunctionalInterface
    private interface ContentReader {
        String read(Object source) throws Exception;
    }
}