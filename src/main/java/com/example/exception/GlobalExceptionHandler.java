package com.example.exception;

import com.example.account.exception.AccountNotFoundException;
import com.example.transfer.exception.IdempotencyConflictException;
import com.example.transfer.exception.InsufficientFundsException;
import com.example.transfer.exception.InvalidAccountTransferException;
import com.example.transfer.exception.TransferNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(
            MissingRequestHeaderException ex) {

        ApiError error = new ApiError(
                400,
                "MISSING_HEADER",
                "Required header is missing: " + ex.getHeaderName()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(
            HandlerMethodValidationException ex) {

        String message = ex.getAllErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid request parameter or header");

        ApiError response = new ApiError(
                400,
                "VALIDATION_ERROR",
                message
        );

        return ResponseEntity.badRequest().body(response);
    }

    /*
    * MethodArgumentNotValidException = The request DTO failed validation.
    * HandlerMethodValidationException = A controller method parameter failed validation.
    */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException ex) {

        // Extract the first validation error message
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("Invalid Request");

        ApiError response = new ApiError(
                400,
                "VALIDATION_ERROR",
                message
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> handleAccountNoFound(
            AccountNotFoundException ex) {

        ApiError response = new ApiError(
                404,
                "ACCOUNT_NOT_FOUND",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(InvalidAccountTransferException.class)
    public ResponseEntity<ApiError> handleInvalidAccountTransfer(
            InvalidAccountTransferException ex) {

        ApiError response = new ApiError(
                400,
                "INVALID_ACCOUNT_TRANSFER",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFund(
            InsufficientFundsException ex) {

        ApiError response = new ApiError(
                409,
                "INSUFFICIENT_FUNDS",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(TransferNotFoundException.class)
    public ResponseEntity<ApiError> handleTransferNotFound(
            TransferNotFoundException ex) {

        ApiError response = new ApiError(
                404,
                "TRANSFER_RECORD_NOT_FOUND",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyConflict(
            IdempotencyConflictException ex) {

        ApiError response = new ApiError(
                409,
                "IDEMPOTENCY_CONFLICT",
                ex.getMessage()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
}
