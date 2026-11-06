package com.demo.syncpay.payment.repository;

import com.demo.syncpay.payment.model.ProcessedCommand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedCommandRepository extends JpaRepository<ProcessedCommand, Long> {
    boolean existsByCommandId(String commandId);
}
