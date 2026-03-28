package com.aguardientes.azarcafetero.application.dto;

import java.util.List;

public record PlayerDTO(
        String id,
        String name,
        int score,
        List<CardDTO> hand,
        int handSize
) {}
