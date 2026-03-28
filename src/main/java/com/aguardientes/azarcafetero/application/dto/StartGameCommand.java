package com.aguardientes.azarcafetero.application.dto;

public record StartGameCommand(
        String gameId
) {
    public StartGameCommand {
        if (gameId == null || gameId.isBlank()) {
            throw new IllegalArgumentException("Game ID cannot be null or empty");
        }
    }
}
