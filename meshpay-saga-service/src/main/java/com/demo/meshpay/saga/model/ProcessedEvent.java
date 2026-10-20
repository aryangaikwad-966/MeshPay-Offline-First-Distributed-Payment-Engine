package com.demo.meshpay.saga.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_events", uniqueConstraints = @UniqueConstraint(name = "uk_processed_event_id", columnNames = "event_id"))
public class ProcessedEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_id", nullable = false, length = 255) private String eventId;
    @Column(nullable = false, length = 100) private String topic;
    @Column(nullable = false, updatable = false) private Instant processedAt = Instant.now();
    protected ProcessedEvent() {}
    public ProcessedEvent(String eventId, String topic) { this.eventId = eventId; this.topic = topic; }
}
