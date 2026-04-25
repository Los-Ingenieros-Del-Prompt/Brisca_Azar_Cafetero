package com.aguardientes.azarcafetero.domain.model;

/**
 * Niveles de dificultad del bot de Brisca.
 *
 * EASY   → jugadas aleatorias, nunca usa estrategia de triunfo
 * MEDIUM → descarta lo más barato primero; gana la baza con la carta mínima necesaria
 * HARD   → maximiza puntos; recuerda cartas jugadas; estrategia defensiva cuando va ganando
 */
public enum BotDifficulty {
    EASY,
    MEDIUM,
    HARD
}