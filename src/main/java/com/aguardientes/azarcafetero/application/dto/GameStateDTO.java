package com.aguardientes.azarcafetero.application.dto;

import com.aguardientes.azarcafetero.domain.model.GameState;
import com.aguardientes.azarcafetero.domain.model.Suit;

import java.util.List;

public record GameStateDTO(
        String gameId,
        GameState state,
        List<PlayerDTO> players,
        String currentPlayerId,
        TrickDTO currentTrick,
        CardDTO trumpCard,
        Suit trumpSuit,
        int remainingCards,
        PlayerDTO winner
) {}
