package com.example.account.controller;

import com.example.account.dto.AccountResponse;
import com.example.account.dto.CreateAccountRequest;
import com.example.account.model.Account;
import com.example.account.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

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
            @RequestBody CreateAccountRequest request) {

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

}
