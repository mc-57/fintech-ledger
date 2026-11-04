package com.example.transfer.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transfers")
public class TransferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id")
    private Long id;

    @Column(name = "from_account_id", nullable = false)
    private Long fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private Long toAccountId;

    @Column(name = "transfer_amount", nullable = false,
            precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Required by JPA to instantiate entities.
    protected TransferEntity() {}

    public TransferEntity(
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount,
            Instant createdAt){

        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }

    public Long getFromAccountId() { return fromAccountId; }

    public Long getToAccountId() { return toAccountId; }

    public BigDecimal getAmount() { return amount; }

    public Instant getCreatedAt() { return createdAt; }
}
