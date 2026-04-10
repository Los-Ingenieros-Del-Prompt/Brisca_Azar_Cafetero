package com.aguardientes.azarcafetero.infrastructure.wallet;

public class WalletOperationException extends RuntimeException {
    public WalletOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}