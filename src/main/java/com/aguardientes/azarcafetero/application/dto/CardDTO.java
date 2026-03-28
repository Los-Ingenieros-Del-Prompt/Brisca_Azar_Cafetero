package com.aguardientes.azarcafetero.application.dto;

import com.aguardientes.azarcafetero.domain.model.Rank;
import com.aguardientes.azarcafetero.domain.model.Suit;

public record CardDTO(
        Suit suit,
        Rank rank,
        int points
) {}
