package com.aguardientes.azarcafetero.application.dto;

import java.math.BigDecimal;

public record CreateGameCommand(
        String gameId,
        int minPlayers,
        int maxPlayers,
        BigDecimal betAmount
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
        if (betAmount == null || betAmount.compareTo(BigDecimal.ZERO) <= 0) {
            betAmount = BigDecimal.valueOf(100); // apuesta por defecto
        }
    }
}
