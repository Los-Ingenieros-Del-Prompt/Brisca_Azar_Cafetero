package com.aguardientes.azarcafetero.application.dto;

public record JoinGameCommand(
        String gameId,
        String playerId,
        String playerName
) {
    public JoinGameCommand {
        if (gameId == null || gameId.isBlank()) {
            throw new IllegalArgumentException("Game ID cannot be null or empty");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty");
        }
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("Player name cannot be null or empty");
        }
    }
}
