package com.example.account.service;

import com.example.account.dto.CreateAccountRequest;
import com.example.account.dto.DepositRequest;
import com.example.account.model.Account;
import com.example.account.exception.AccountNotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AccountService {

    private final Map<Long, Account> accounts = new HashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    // Creates an account with a generated ID and zero balance,
    // then stores it in memory.
    public Account createAccount(CreateAccountRequest request) {

        Long id = nextId.getAndIncrement();

        Account account = new Account(
                id,
                request.ownerName(),
                BigDecimal.ZERO
        );

        accounts.put(id, account);

        return account;
    }

    public Account getAccountById(Long id) {
        return accounts.get(id);
    }

    public List<Account> getAllAccounts(){
        List<Account> accountList = accounts.values()
                .stream()
                .sorted(Comparator.comparing(Account::id))
                .toList();

        return accountList;
    }

    public Account deposit(Long accountId, DepositRequest request) {

        // Retrieve account
        Account account = accounts.get(accountId);

        if (account == null) {
            throw new AccountNotFoundException(accountId);
        }

        // Calculate new balance
        BigDecimal newBalance = account.balance().add(request.amount());
        // Create updated account w new balance
        Account updatedAccount = new Account(
                account.id(),
                account.ownerName(),
                newBalance
        );
        // Replace old account
        accounts.put(accountId, updatedAccount);

        return updatedAccount;
    }
}
