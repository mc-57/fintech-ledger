package com.example.transfer.controller;

import com.example.transfer.dto.TransferRequest;
import com.example.transfer.dto.TransferResponse;
import com.example.transfer.exception.TransferNotFoundException;
import com.example.transfer.model.Transfer;
import com.example.transfer.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request) {

        Transfer record = transferService.transfer(request);

        TransferResponse response = new TransferResponse(
                record.id(),
                record.fromAccountId(),
                record.toAccountId(),
                record.amount(),
                record.createdAt()
        );

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransferById(
            @PathVariable Long id) {

        Transfer transfer = transferService.getTransferById(id);

        if (transfer == null) {
            throw new TransferNotFoundException(id);
        }

        TransferResponse response = new TransferResponse(
                transfer.id(),
                transfer.fromAccountId(),
                transfer.toAccountId(),
                transfer.amount(),
                transfer.createdAt()
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<TransferResponse>> getAllTransfers() {

        List<Transfer> transfers = transferService.getAllTransfers();

        List<TransferResponse> responses = transfers.stream()
                .map(transfer -> new TransferResponse(
                        transfer.id(),
                        transfer.fromAccountId(),
                        transfer.toAccountId(),
                        transfer.amount(),
                        transfer.createdAt()
                ))
                .toList();

        return ResponseEntity.ok(responses);
    }

}
