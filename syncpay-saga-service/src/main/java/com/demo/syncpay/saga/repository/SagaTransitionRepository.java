package com.demo.syncpay.saga.repository;

import com.demo.syncpay.saga.model.SagaTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SagaTransitionRepository extends JpaRepository<SagaTransition, Long> {
    List<SagaTransition> findBySagaIdOrderByChangedAtAsc(String sagaId);
}
