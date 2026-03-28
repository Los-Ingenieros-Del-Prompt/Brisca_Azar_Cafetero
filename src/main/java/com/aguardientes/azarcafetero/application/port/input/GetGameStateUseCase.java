package com.aguardientes.azarcafetero.application.port.input;

import com.aguardientes.azarcafetero.application.dto.GameStateDTO;

public interface GetGameStateUseCase {
    GameStateDTO getGameState(String gameId);
    GameStateDTO getGameState(String gameId, String playerId);
    GameStateDTO getGameStateWithAllHands(String gameId);
}
