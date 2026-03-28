package com.aguardientes.azarcafetero.domain.exception;

public class GameNotStartedException extends RuntimeException {
    public GameNotStartedException(String gameId) {
        super("Game has not started yet: " + gameId);
    }
}
