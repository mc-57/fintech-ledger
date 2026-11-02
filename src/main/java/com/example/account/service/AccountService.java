package com.example.account.service;

import com.example.account.dto.CreateAccountRequest;
import com.example.account.model.Account;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
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
}
