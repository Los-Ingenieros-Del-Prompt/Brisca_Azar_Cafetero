package com.aguardientes.azarcafetero.application.dto;

public record CreateGameCommand(
        String gameId,
        int minPlayers,
        int maxPlayers
) {
    public CreateGameCommand {
        if (gameId == null || gameId.isBlank()) {
            throw new IllegalArgumentException("Game ID cannot be null or empty");
        }
        if (minPlayers < 2 || minPlayers > 4) {
            throw new IllegalArgumentException("Min players must be between 2 and 4");
        }
        if (maxPlayers < 2 || maxPlayers > 4) {
            throw new IllegalArgumentException("Max players must be between 2 and 4");
        }
        if (minPlayers > maxPlayers) {
            throw new IllegalArgumentException("Min players cannot exceed max players");
        }
    }
}
