package com.aguardientes.azarcafetero.application.dto;

public record GetGameStateCommand(
        String playerId
) {
    public GetGameStateCommand {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("Player ID cannot be null or empty");
        }
    }
}
