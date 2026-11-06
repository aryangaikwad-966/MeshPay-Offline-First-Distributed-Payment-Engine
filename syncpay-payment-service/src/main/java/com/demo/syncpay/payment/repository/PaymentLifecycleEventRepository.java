package com.demo.syncpay.payment.repository;

import com.demo.syncpay.payment.model.PaymentLifecycleEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentLifecycleEventRepository extends JpaRepository<PaymentLifecycleEvent, Long> {
    List<PaymentLifecycleEvent> findByPacketHashOrderByOccurredAtAsc(String packetHash);
}
