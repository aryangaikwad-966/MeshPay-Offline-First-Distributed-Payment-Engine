package com.demo.syncpay.payment.service;

import com.demo.syncpay.payment.model.Account;
import com.demo.syncpay.payment.model.LedgerEntry;
import com.demo.syncpay.payment.model.Transaction;
import com.demo.syncpay.payment.repository.AccountRepository;
import com.demo.syncpay.payment.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Simulated-money movement and immutable ledger posting.
 *
 * Balance strategy: Account.balance is maintained transactionally alongside
 * append-only ledger_entries. Both happen inside the caller's transaction, so
 * a rollback restores balances and prevents orphaned entries. The ledger's
 * unique (packet_hash, account_vpa, direction, kind) constraint is the
 * database-level exactly-once barrier; account row locks serialize concurrent
 * mutations on the same account.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accounts;
    private final LedgerEntryRepository ledger;

    /** Thrown when a payment cannot be settled for financial reasons. */
    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) { super(message); }
    }

    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(String vpa) { super("Account not found: " + vpa); }
    }

    /**
     * Debit sender, credit receiver, and post settlement ledger entries.
     * Must be called inside a transaction; locks both account rows.
     */
    public void postSettlement(Transaction transaction) {
        Account sender = accounts.findByVpaForUpdate(transaction.getSenderVpa())
                .orElseThrow(() -> new AccountNotFoundException(transaction.getSenderVpa()));
        Account receiver = accounts.findByVpaForUpdate(transaction.getReceiverVpa())
                .orElseThrow(() -> new AccountNotFoundException(transaction.getReceiverVpa()));

        BigDecimal amount = transaction.getAmount();
        if (amount == null || amount.signum() <= 0)
            throw new InsufficientFundsException("Payment amount must be positive");
        if (sender.getBalance() == null || sender.getBalance().compareTo(amount) < 0)
            throw new InsufficientFundsException("Insufficient balance for " + transaction.getSenderVpa());

        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accounts.save(sender);
        accounts.save(receiver);

        ledger.save(new LedgerEntry(transaction.getPacketHash(), sender.getVpa(),
                LedgerEntry.Direction.DEBIT, LedgerEntry.Kind.SETTLEMENT, amount, transaction.getCurrency()));
        ledger.save(new LedgerEntry(transaction.getPacketHash(), receiver.getVpa(),
                LedgerEntry.Direction.CREDIT, LedgerEntry.Kind.SETTLEMENT, amount, transaction.getCurrency()));
    }

    /**
     * Compensating refund movement: debit the receiver (reversing the credit)
     * and credit the sender (reversing the debit). Original SETTLEMENT entries
     * are never modified.
     */
    public void postRefund(Transaction transaction) {
        Account sender = accounts.findByVpaForUpdate(transaction.getSenderVpa())
                .orElseThrow(() -> new AccountNotFoundException(transaction.getSenderVpa()));
        Account receiver = accounts.findByVpaForUpdate(transaction.getReceiverVpa())
                .orElseThrow(() -> new AccountNotFoundException(transaction.getReceiverVpa()));

        BigDecimal amount = transaction.getAmount();
        if (receiver.getBalance() == null || receiver.getBalance().compareTo(amount) < 0)
            throw new InsufficientFundsException("Receiver balance cannot cover refund for " + transaction.getPacketHash());

        receiver.setBalance(receiver.getBalance().subtract(amount));
        sender.setBalance(sender.getBalance().add(amount));
        accounts.save(sender);
        accounts.save(receiver);

        ledger.save(new LedgerEntry(transaction.getPacketHash(), receiver.getVpa(),
                LedgerEntry.Direction.DEBIT, LedgerEntry.Kind.REFUND, amount, transaction.getCurrency()));
        ledger.save(new LedgerEntry(transaction.getPacketHash(), sender.getVpa(),
                LedgerEntry.Direction.CREDIT, LedgerEntry.Kind.REFUND, amount, transaction.getCurrency()));
    }
}
