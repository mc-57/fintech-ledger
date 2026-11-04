package com.example.transfer.service;

import com.example.account.exception.AccountNotFoundException;
import com.example.account.model.Account;
import com.example.account.service.AccountService;
import com.example.transfer.dto.TransferRequest;
import com.example.transfer.entity.TransferEntity;
import com.example.transfer.exception.InsufficientFundsException;
import com.example.transfer.exception.InvalidAccountTransferException;
import com.example.transfer.exception.TransferNotFoundException;
import com.example.transfer.model.Transfer;
import com.example.transfer.repository.TransferRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Handles money transfers between ledger accounts.
 * Uses AccountService for account access and balance updates,
 * and TransferRepository for persistent transfer records.
 */
@Service
public class TransferService {

    private final AccountService accountService;
    private final TransferRepository transferRepository;

    public TransferService(AccountService accountService, TransferRepository transferRepository) {
        this.accountService = accountService;
        this.transferRepository = transferRepository;
    }

    public Transfer transfer(TransferRequest request) {

        Account sender = accountService.getAccountById(request.fromAccountId());
        Account recipient = accountService.getAccountById(request.toAccountId());

        validateTransfer(sender, recipient, request);

        // TODO(DB-06): Make debit, credit, and transfer recording atomic.
        // Currently, separate database operations can commit independently.
        updateBalances(sender, recipient, request.amount());

        return createTransferRecord(request);

    }

    private void validateTransfer(Account sender, Account recipient, TransferRequest request) {

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

        accountService.updateAccount(updatedSender);
        accountService.updateAccount(updatedRecipient);
    }

    private Transfer createTransferRecord(TransferRequest request){

        TransferEntity entity = new TransferEntity(
                request.fromAccountId(),
                request.toAccountId(),
                request.amount(),
                Instant.now()
        );

        TransferEntity savedTransfer = transferRepository.save(entity);

        return toDomain(savedTransfer);
    }

    public Transfer getTransferById(Long id){

        TransferEntity entity = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));

        return toDomain(entity);
    }

    public List<Transfer> getAllTransfers(){

        return transferRepository.findAll()
                .stream()
                .map(this::toDomain)
                .sorted(Comparator.comparing(Transfer::id))
                .toList();
    }

    private Transfer toDomain(TransferEntity entity) {
        return new Transfer(
                entity.getId(),
                entity.getFromAccountId(),
                entity.getToAccountId(),
                entity.getAmount(),
                entity.getCreatedAt()
        );
    }


}
