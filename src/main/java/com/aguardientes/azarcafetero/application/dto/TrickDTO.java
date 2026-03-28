package com.aguardientes.azarcafetero.application.dto;

import java.util.Map;

public record TrickDTO(
        Map<String, CardDTO> playedCards,
        String leadPlayerId,
        int totalPoints
) {}
