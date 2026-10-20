package com.demo.meshpay.saga.repository;

import com.demo.meshpay.saga.model.SagaState;
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

    void deleteBySagaId(String sagaId);
}
