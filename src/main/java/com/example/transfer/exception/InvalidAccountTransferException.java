package com.example.transfer.exception;

public class InvalidAccountTransferException extends RuntimeException{

    public InvalidAccountTransferException(String message) {
        super(message);
    }
}
