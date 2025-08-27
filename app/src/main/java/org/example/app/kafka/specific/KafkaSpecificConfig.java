package org.example.app.kafka.specific;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.avro.Greeting;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
@ConditionalOnProperty(prefix = "app.examples.specific", name = "enabled", havingValue = "true")
public class KafkaSpecificConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaSpecificConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ConsumerFactory<String, Greeting> consumerFactorySpecific() {
        Map<String, Object> props = new HashMap<>(kafkaProperties.buildConsumerProperties());
        // Ensure String key deserializer and Confluent Avro for value
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // The value.deserializer is already configured via application.yml to KafkaAvroDeserializer
        // Force SpecificRecord usage
        props.put("specific.avro.reader", true);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean(name = "specificKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Greeting> specificKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Greeting> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactorySpecific());
        return factory;
    }
}
