package com.demo.syncpay.payment.repository;

import com.demo.syncpay.payment.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByPacketHash(String packetHash);
    List<LedgerEntry> findByAccountVpaOrderByCreatedAtDesc(String accountVpa);
}
