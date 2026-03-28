package com.aguardientes.azarcafetero.domain.exception;

public class GameFullException extends RuntimeException {
    public GameFullException(String gameId) {
        super("Game is full: " + gameId);
    }
}
