package com.aguardientes.azarcafetero.domain.exception;

public class InvalidMoveException extends RuntimeException {
    public InvalidMoveException(String message) {
        super(message);
    }
}
