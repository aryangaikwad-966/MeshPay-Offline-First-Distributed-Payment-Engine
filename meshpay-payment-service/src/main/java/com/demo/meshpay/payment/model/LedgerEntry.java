package com.demo.meshpay.payment.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable financial ledger entry. Entries are append-only: refunds create
 * compensating entries rather than mutating history. The unique constraint on
 * (packet_hash, account_vpa, direction, kind) guarantees exactly-once posting.
 */
@Entity
@Table(name = "ledger_entries",
        uniqueConstraints = @UniqueConstraint(name = "uk_ledger_entry",
                columnNames = {"packet_hash", "account_vpa", "direction", "kind"}))
public class LedgerEntry {

    public enum Direction { DEBIT, CREDIT }
    public enum Kind { SETTLEMENT, REFUND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "packet_hash", nullable = false, length = 64)
    private String packetHash;

    @Column(name = "account_vpa", nullable = false)
    private String accountVpa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public LedgerEntry() {}

    public LedgerEntry(String packetHash, String accountVpa, Direction direction,
                       Kind kind, BigDecimal amount, String currency) {
        this.packetHash = packetHash;
        this.accountVpa = accountVpa;
        this.direction = direction;
        this.kind = kind;
        this.amount = amount;
        this.currency = currency;
    }

    public Long getId() { return id; }
    public String getPacketHash() { return packetHash; }
    public String getAccountVpa() { return accountVpa; }
    public Direction getDirection() { return direction; }
    public Kind getKind() { return kind; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
