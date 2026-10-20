package com.demo.meshpay.saga.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "saga_command_outbox", indexes = {
        @Index(name = "idx_saga_outbox_ready", columnList = "state,next_attempt_at,created_at")
})
public class SagaCommandOutbox {
    @Id
    @Column(name = "command_id", nullable = false, length = 36)
    private String commandId;
    @Column(name = "saga_id", nullable = false, length = 80)
    private String sagaId;
    @Column(nullable = false, length = 120)
    private String topic;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(nullable = false, length = 20)
    private String state = "PENDING";
    @Column(nullable = false)
    private int attempts;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt = Instant.now();
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "last_error", length = 500)
    private String lastError;

    protected SagaCommandOutbox() {}
    public SagaCommandOutbox(String commandId, String sagaId, String topic, String payload) {
        this.commandId = commandId; this.sagaId = sagaId; this.topic = topic; this.payload = payload;
    }
    public String getCommandId() { return commandId; }
    public String getSagaId() { return sagaId; }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public String getState() { return state; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
    public void published() { state = "PUBLISHED"; publishedAt = Instant.now(); lastError = null; }
    public void failed(Throwable error) {
        attempts++;
        state = attempts >= 10 ? "DEAD" : "PENDING";
        long delaySeconds = Math.min(300, 1L << Math.min(attempts, 8));
        nextAttemptAt = Instant.now().plusSeconds(delaySeconds);
        String message = error == null ? "Unknown publication failure" : error.getClass().getSimpleName() + ": " + error.getMessage();
        lastError = message.substring(0, Math.min(message.length(), 500));
    }
}
