package com.demo.meshpay.payment.repository;

import com.demo.meshpay.payment.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findTop20ByOrderByIdDesc();
    boolean existsByPacketHash(String packetHash);
    boolean existsBySenderVpaAndNonce(String senderVpa, String nonce);
    java.util.Optional<Transaction> findByPacketHash(String packetHash);
    java.util.Optional<Transaction> findBySenderVpaAndNonce(String senderVpa, String nonce);
    java.util.Optional<Transaction> findByPacketId(String packetId);
    java.util.List<Transaction> findTop100ByOrderByIdDesc();
    long countByStatus(Transaction.Status status);
}
