package com.demo.meshpay.payment.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_commands", uniqueConstraints = @UniqueConstraint(name = "uk_processed_command_id", columnNames = "command_id"))
public class ProcessedCommand {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "command_id", nullable = false, length = 255) private String commandId;
    @Column(nullable = false, updatable = false) private Instant processedAt = Instant.now();
    protected ProcessedCommand() {}
    public ProcessedCommand(String commandId) { this.commandId = commandId; }
}
