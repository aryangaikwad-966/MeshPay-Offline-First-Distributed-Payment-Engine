package com.demo.meshpay.saga.repository;

import com.demo.meshpay.saga.model.SagaCommandOutbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SagaCommandOutboxRepository extends JpaRepository<SagaCommandOutbox, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SagaCommandOutbox o where o.state = 'PENDING' and o.nextAttemptAt <= :now order by o.createdAt")
    List<SagaCommandOutbox> lockReady(@Param("now") Instant now, Pageable pageable);
    long countByState(String state);
    List<SagaCommandOutbox> findTop100ByOrderByCreatedAtDesc();
}
