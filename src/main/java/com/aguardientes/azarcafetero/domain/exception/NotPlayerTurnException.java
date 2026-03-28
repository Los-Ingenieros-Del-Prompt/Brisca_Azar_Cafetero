package com.aguardientes.azarcafetero.domain.exception;

public class NotPlayerTurnException extends RuntimeException {
    public NotPlayerTurnException(String playerId) {
        super("It is not player's turn: " + playerId);
    }
}
