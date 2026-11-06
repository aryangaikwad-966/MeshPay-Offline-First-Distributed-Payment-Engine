package com.demo.syncpay.payment.repository;

import com.demo.syncpay.payment.model.PacketAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PacketAttemptRepository extends JpaRepository<PacketAttempt, Long> {
    long countByOutcome(PacketAttempt.Outcome outcome);
    List<PacketAttempt> findTop100ByOrderByAttemptedAtDesc();
}
