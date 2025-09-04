package org.example.app.kafka;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Configuration
@ConditionalOnProperty(prefix = "app.consumer.custom-sr-client", name = "enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConsumerConfig {

    // Reuse one SR client across the app and let the deserializer use it.
    @Bean
    @ConditionalOnMissingBean(SchemaRegistryClient.class)
    public SchemaRegistryClient schemaRegistryClient(KafkaProperties kafkaProperties) {
        Map<String, Object> allProps = new HashMap<>(kafkaProperties.getProperties());
        Object url = allProps.get("schema.registry.url");
        if (url == null) {
            // Also check consumer map just in case
            url = kafkaProperties.buildConsumerProperties(null).get("schema.registry.url");
        }
        String schemaRegistryUrl = Objects.toString(url, null);
        if (schemaRegistryUrl == null || schemaRegistryUrl.isBlank()) {
            throw new IllegalStateException("schema.registry.url must be configured under spring.kafka.properties.schema.registry.url");
        }
        // Confluent client supports extra configs like bearer.* for OAuth
        return new CachedSchemaRegistryClient(schemaRegistryUrl, 100, allProps);
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory(KafkaProperties kafkaProperties,
                                                           SchemaRegistryClient schemaRegistryClient) {
        Map<String, Object> consumerProps = new HashMap<>(kafkaProperties.buildConsumerProperties(null));
        // We provide deserializer instances, so class entries are not required, but harmless if present.
        // Ensure specific reader is preserved if configured.
        KafkaAvroDeserializer valueDeserializer = new KafkaAvroDeserializer(schemaRegistryClient, consumerProps);
        StringDeserializer keyDeserializer = new StringDeserializer();
        return new DefaultKafkaConsumerFactory<>(consumerProps, keyDeserializer, valueDeserializer);
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
