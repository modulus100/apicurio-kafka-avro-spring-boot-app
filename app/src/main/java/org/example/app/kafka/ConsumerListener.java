package org.example.app.kafka;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.examples.generic", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ConsumerListener {
    private static final Logger log = LoggerFactory.getLogger(ConsumerListener.class);

    @KafkaListener(topics = "${app.kafka.topic:demo-topic}", groupId = "${spring.kafka.consumer.group-id:demo-group}")
    public void listen(ConsumerRecord<String, GenericRecord> record) {
        GenericRecord value = record.value();
        if (value != null) {
            Object message = value.get("message");
            Object timestamp = value.get("timestamp");
            log.info("Received Avro record: message='{}', timestamp='{}' from {}-{}@{}",
                    message, timestamp, record.topic(), record.partition(), record.offset());
        } else {
            log.warn("Received null Avro record from {}-{}@{}", record.topic(), record.partition(), record.offset());
        }
    }
}
