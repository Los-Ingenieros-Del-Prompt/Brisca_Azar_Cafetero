package com.aguardientes.azarcafetero.domain.exception;

public class PlayerNotInGameException extends RuntimeException {
    public PlayerNotInGameException(String playerId, String gameId) {
        super("Player " + playerId + " is not in game " + gameId);
    }
}
