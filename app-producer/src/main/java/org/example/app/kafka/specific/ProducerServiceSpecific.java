package org.example.app.kafka.specific;

import org.example.avro.Greeting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.net.URI;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "app.examples.specific", name = "enabled", havingValue = "true")
public class ProducerServiceSpecific {
    private static final Logger log = LoggerFactory.getLogger(ProducerServiceSpecific.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic:demo-topic}")
    private String topic;

    public ProducerServiceSpecific(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    // Send a message every 5 seconds (stagger initial to avoid collision with generic demo)
    @Scheduled(fixedDelay = 5000, initialDelay = 3500)
    public void sendMessage() {
        Greeting greeting = Greeting.newBuilder()
                .setMessage2("Hello from Spring Boot (SpecificRecord) @ " + Instant.now().toEpochMilli())
                .setMessage("Hello from Spring Boot (SpecificRecord) @ " + Instant.now().toEpochMilli())
                .setTimestamp(Instant.now().toEpochMilli())
                .build();

        // CloudEvents binary mode: set only ce-id and ce-source headers
        String ceId = UUID.randomUUID().toString();
        String ceSource = URI.create("urn:example:spring-boot-app").toString();

        Message<Greeting> msg = MessageBuilder.withPayload(greeting)
                .setHeader("ce-id", ceId)
                .setHeader("ce-source", ceSource)
                .setHeader(KafkaHeaders.TOPIC, topic)
                .build();

        kafkaTemplate.send(msg).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send SpecificRecord", ex);
            } else if (result != null && result.getRecordMetadata() != null) {
                log.info("Sent SpecificRecord to {}-{}@{}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.info("Sent SpecificRecord");
            }
        });
    }
}
