package com.aguardientes.azarcafetero.application.dto;

import com.aguardientes.azarcafetero.domain.model.BotDifficulty;

public record AddBotCommand(
        String gameId,
        BotDifficulty difficulty
) {
    public AddBotCommand {
        if (gameId == null || gameId.isBlank()) {
            throw new IllegalArgumentException("Game ID cannot be null or empty");
        }
        if (difficulty == null) {
            difficulty = BotDifficulty.MEDIUM;
        }
    }

    /**
     * Genera un ID único para el bot con el patrón BOT_{DIFFICULTY}_{uuid-corto}.
     * El prefijo BOT_ permite identificarlo en cualquier capa sin campos extra.
     */
    public static String generateBotId(BotDifficulty difficulty) {
        String shortUuid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "BOT_" + difficulty.name() + "_" + shortUuid;
    }

    /** Devuelve true si el ID corresponde a un jugador bot. */
    public static boolean isBot(String playerId) {
        return playerId != null && playerId.startsWith("BOT_");
    }

    /** Extrae la dificultad del ID del bot (ej: "BOT_HARD_abc123" → HARD). */
    public static BotDifficulty difficultyFromId(String botId) {
        try {
            String[] parts = botId.split("_", 3);
            return BotDifficulty.valueOf(parts[1]);
        } catch (Exception e) {
            return BotDifficulty.MEDIUM;
        }
    }
}
