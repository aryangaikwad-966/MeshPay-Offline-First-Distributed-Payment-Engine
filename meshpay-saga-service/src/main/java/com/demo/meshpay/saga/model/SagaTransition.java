package com.demo.meshpay.saga.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "saga_transitions", indexes = @Index(name = "idx_saga_transition_saga", columnList = "saga_id,changed_at"))
public class SagaTransition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "saga_id", nullable = false, length = 80) private String sagaId;
    @Column(name = "from_state", length = 80) private String fromState;
    @Column(name = "to_state", nullable = false, length = 80) private String toState;
    @Column(length = 512) private String reason;
    @Column(name = "changed_at", nullable = false, updatable = false) private Instant changedAt = Instant.now();
    protected SagaTransition() {}
    public SagaTransition(String sagaId, String fromState, String toState, String reason) {
        this.sagaId = sagaId; this.fromState = fromState; this.toState = toState; this.reason = reason;
    }
    public Long getId() { return id; }
    public String getSagaId() { return sagaId; }
    public String getFromState() { return fromState; }
    public String getToState() { return toState; }
    public String getReason() { return reason; }
    public Instant getChangedAt() { return changedAt; }
}
