package com.example.transfer.service;

import com.example.account.exception.AccountNotFoundException;
import com.example.account.model.Account;
import com.example.account.service.AccountService;
import com.example.transfer.dto.TransferRequest;
import com.example.transfer.exception.InsufficientFundsException;
import com.example.transfer.exception.InvalidAccountTransferException;
import com.example.transfer.model.Transfer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles transfers between ledger accounts.
 * Uses AccountService as the single source of account data
 * while storage is in memory.
 */
@Service
public class TransferService {

    private final AccountService accountService;
    // TransferService owns the transfer records, while AccountService owns the accounts.
    private final Map<Long, Transfer> transfers = new HashMap<>();
    private final AtomicLong nextTransferId = new AtomicLong(1);

    public TransferService(AccountService accountService) {
        this.accountService = accountService;
    }

    public Transfer transfer(TransferRequest request) {

        Account sender = accountService.getAccountById(request.fromAccountId());
        Account recipient = accountService.getAccountById(request.toAccountId());

        validateTransfer(sender, recipient, request);

        updateBalances(sender, recipient, request.amount());

        return createTransferRecord(request);

    }

    private void validateTransfer(Account sender, Account recipient, TransferRequest request) {

        if (sender == null) {
            throw new AccountNotFoundException(request.fromAccountId());
        }

        if (recipient == null) {
            throw new AccountNotFoundException(request.toAccountId());
        }

        if (sender.id().equals(recipient.id())) {
            throw new InvalidAccountTransferException(
                    "Sender and recipient accounts must be different"
            );
        }

        if (sender.balance().compareTo(request.amount()) < 0) {
            throw new InsufficientFundsException(
                    "Account " + sender.id() + " has insufficient funds"
            );
        }

    }

    private void updateBalances(Account sender, Account recipient, BigDecimal amount){

        BigDecimal senderBalance = sender.balance().subtract(amount);
        BigDecimal recipientBalance = recipient.balance().add(amount);

        Account updatedSender = new Account(
                sender.id(),
                sender.ownerName(),
                senderBalance
        );

        Account updatedRecipient = new Account(
                recipient.id(),
                recipient.ownerName(),
                recipientBalance
        );

        // TODO(DB): Wrap debit, credit, and transfer recording in one
        // database transaction to prevent partial transfers.
        accountService.updateAccount(updatedSender);
        accountService.updateAccount(updatedRecipient);
    };

    private Transfer createTransferRecord(TransferRequest request){

        Long transferId = nextTransferId.getAndIncrement();

        Transfer record = new Transfer(
                transferId,
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                Instant.now()
        );

        transfers.put(transferId, record);

        return record;
    };

    public Transfer getTransferById(Long id){
        return transfers.get(id);
    }

    public List<Transfer> getAllTransfers(){

        return transfers.values()
                .stream()
                .sorted(Comparator.comparing(Transfer::id))
                .toList();
    }


}
