package com.aguardientes.azarcafetero.domain.exception;

public class GameNotFoundException extends RuntimeException {
    public GameNotFoundException(String gameId) {
        super("Game not found: " + gameId);
    }
}
