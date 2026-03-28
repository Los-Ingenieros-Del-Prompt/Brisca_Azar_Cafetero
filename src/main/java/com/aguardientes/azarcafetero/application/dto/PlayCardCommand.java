package com.aguardientes.azarcafetero.application.dto;

import com.aguardientes.azarcafetero.domain.model.Rank;
import com.aguardientes.azarcafetero.domain.model.Suit;

public record PlayCardCommand(
        String gameId,
        String playerId,
        Suit suit,
        Rank rank
) {
    public PlayCardCommand {
        if (gameId == null || gameId.isBlank()) {
            throw new IllegalArgumentException("Game ID cannot be null or empty");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty");
        }
        if (suit == null) {
            throw new IllegalArgumentException("Suit cannot be null");
        }
        if (rank == null) {
            throw new IllegalArgumentException("Rank cannot be null");
        }
    }
}
