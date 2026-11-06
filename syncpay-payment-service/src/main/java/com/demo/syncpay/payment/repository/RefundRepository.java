package com.demo.syncpay.payment.repository;

import com.demo.syncpay.payment.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {
    Optional<Refund> findByPacketHash(String packetHash);
    List<Refund> findTop100ByOrderByCreatedAtDesc();
}
