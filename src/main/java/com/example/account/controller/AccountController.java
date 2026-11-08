package com.example.account.controller;

import com.example.account.dto.AccountResponse;
import com.example.account.dto.CreateAccountRequest;
import com.example.account.dto.DepositRequest;
import com.example.account.model.Account;
import com.example.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Handles account creation and returns 201 Created
     * with the URI of the new account.
     * @param request CreateAccountRequest
     * @return AccountResponse
     */
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

        Account account = accountService.createAccount(request);

        AccountResponse response = new AccountResponse(
                account.id(),
                account.ownerName(),
                account.balance()
        );

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable Long id) {

        Account account = accountService.getAccountById(id);

        if (account == null) {
            return ResponseEntity.notFound().build();
        }

        AccountResponse response = new AccountResponse(
                account.id(),
                account.ownerName(),
                account.balance()
        );

        return ResponseEntity.ok().body(response);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {

        List<Account> accounts = accountService.getAllAccounts();

        // Convert each Account into AccountResponse
        List<AccountResponse> responses = accounts.stream()
                .map(account -> new AccountResponse(
                        account.id(),
                        account.ownerName(),
                        account.balance()
                ))
                .toList();

        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/deposits")
    public ResponseEntity<AccountResponse> deposit(
            @PathVariable Long id,
            @Valid @RequestBody DepositRequest request) {

        Account account = accountService.deposit(id, request);

        AccountResponse response = new AccountResponse(
                account.id(),
                account.ownerName(),
                account.balance()
        );

        return ResponseEntity.ok(response);
    }

}
