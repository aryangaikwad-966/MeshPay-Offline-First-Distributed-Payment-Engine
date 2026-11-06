package com.demo.meshpay.saga.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

/** Binds saga/packet correlation identifiers from Kafka payloads to the MDC. */
@Component
public class KafkaCorrelationInterceptor implements RecordInterceptor<String, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ConsumerRecord<String, String> intercept(ConsumerRecord<String, String> record,
                                                    Consumer<String, String> consumer) {
        try {
            JsonNode node = MAPPER.readTree(record.value());
            put("sagaId", node.path("sagaId").asText(null));
            put("packetHash", node.path("packetHash").asText(null));
            put("packetId", node.path("packetId").asText(null));
        } catch (Exception ignored) {
        }
        MDC.put("kafkaTopic", record.topic());
        MDC.put("kafkaPartition", String.valueOf(record.partition()));
        MDC.put("kafkaOffset", String.valueOf(record.offset()));
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
        MDC.clear();
    }

    private void put(String key, String value) {
        if (value != null && !value.isBlank()) MDC.put(key, value);
    }
}
