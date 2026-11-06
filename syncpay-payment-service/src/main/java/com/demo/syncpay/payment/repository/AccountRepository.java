package com.demo.syncpay.payment.repository;

import com.demo.syncpay.payment.model.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByVpa(String vpa);

    /**
     * Pessimistic row lock for financial mutations. Concurrent settlements
     * against the same account serialize here instead of relying on
     * optimistic-lock retries.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.vpa = :vpa")
    Optional<Account> findByVpaForUpdate(@Param("vpa") String vpa);
}
