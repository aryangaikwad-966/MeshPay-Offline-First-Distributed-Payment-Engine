package com.demo.meshpay.payment.repository;

import com.demo.meshpay.payment.model.ProcessedCommand;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedCommandRepository extends JpaRepository<ProcessedCommand, Long> {
    boolean existsByCommandId(String commandId);
}
