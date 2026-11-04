package com.example.account.service;

import com.example.account.dto.CreateAccountRequest;
import com.example.account.dto.DepositRequest;
import com.example.account.entity.AccountEntity;
import com.example.account.model.Account;
import com.example.account.exception.AccountNotFoundException;
import com.example.account.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository){
        this.accountRepository = accountRepository;
    }

    public Account createAccount(CreateAccountRequest request) {

        AccountEntity accountEntity = new AccountEntity(
                request.ownerName(),
                BigDecimal.ZERO
        );

        AccountEntity savedAccount = accountRepository.save(accountEntity);

        return toDomain(savedAccount);
    }

    public Account getAccountById(Long id) {

        AccountEntity accountEntity = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));

        return toDomain(accountEntity);
    }

    public List<Account> getAllAccounts(){

        List<AccountEntity> accountEntities = accountRepository.findAll();

        return accountEntities.stream()
                .map(this::toDomain)
                .sorted(Comparator.comparing(Account::id))
                .toList();
    }

    public Account deposit(Long accountId, DepositRequest request) {

        AccountEntity accountEntity = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        BigDecimal newBalance = accountEntity.getBalance().add(request.amount());

        accountEntity.updateBalance(newBalance);

        // NOTE: With @Transactional, JPA tracks changes to managed entities
        // and persists them automatically through dirty checking.
        // An explicit save() is not required in that case.
        AccountEntity savedAccount = accountRepository.save(accountEntity);

        return toDomain(savedAccount);
    }

    /**
     * Updates account data after a transfer operation.
     * TransferService owns the transfer business logic, while
     * AccountService manages access to account data.
     */
    public void updateAccount(Account account) {

        AccountEntity accountEntity = accountRepository.findById(account.id())
                .orElseThrow(() -> new AccountNotFoundException(account.id()));

        accountEntity.updateBalance(account.balance());

        // NOTE: An explicit save() persists the update in our current design.
        // With a service-level @Transactional, JPA dirty checking can persist
        // changes to managed entities without an explicit save().
        accountRepository.save(accountEntity);
    }

    private Account toDomain(AccountEntity entity) {
        return new Account(
                entity.getId(),
                entity.getOwnerName(),
                entity.getBalance()
        );
    }
}
