package com.demo.meshpay.payment.service;

import com.demo.meshpay.payment.model.PacketAttempt;
import com.demo.meshpay.payment.repository.PacketAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PacketAttemptRecorder {
    private final PacketAttemptRepository repository;
    @Transactional public void record(String hash, String sender, PacketAttempt.Outcome outcome, String reason) {
        repository.save(new PacketAttempt(hash, sender, outcome, reason));
    }
}
