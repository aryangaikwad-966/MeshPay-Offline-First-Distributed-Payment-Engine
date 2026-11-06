package com.demo.syncpay.saga.repository;

import com.demo.syncpay.saga.model.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Saga State persistence
 * Enables microservices architecture with durable saga state
 */
@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, Long> {

    Optional<SagaState> findBySagaId(String sagaId);

    Optional<SagaState> findByPacketHash(String packetHash);

    java.util.List<SagaState> findTop100ByOrderByUpdatedAtDesc();
    long countByStateNotIn(java.util.Collection<String> states);
    long countByState(String state);

    /** Non-terminal sagas not updated since the given instant (stuck candidates). */
    java.util.List<SagaState> findByStateNotInAndUpdatedAtBefore(
            java.util.Collection<String> states, java.time.Instant updatedBefore);

    void deleteBySagaId(String sagaId);
}
