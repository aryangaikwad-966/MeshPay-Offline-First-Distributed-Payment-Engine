package com.demo.meshpay.saga.repository;

import com.demo.meshpay.saga.model.SagaTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SagaTransitionRepository extends JpaRepository<SagaTransition, Long> {
    List<SagaTransition> findBySagaIdOrderByChangedAtAsc(String sagaId);
}
