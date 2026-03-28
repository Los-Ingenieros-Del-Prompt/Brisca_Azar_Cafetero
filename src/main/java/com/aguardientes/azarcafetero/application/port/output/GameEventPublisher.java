package com.aguardientes.azarcafetero.application.port.output;

import com.aguardientes.azarcafetero.application.dto.GameStateDTO;

public interface GameEventPublisher {
    
    void publishGameCreated(String gameId);
    
    void publishPlayerJoined(String gameId, String playerId);
    
    void publishGameStarted(String gameId);
    
    void publishCardPlayed(String gameId, String playerId, String cardInfo);
    
    void publishTrickCompleted(String gameId, String winnerId, int points);
    
    void publishGameStateUpdated(GameStateDTO gameState);
    
    void publishGameFinished(String gameId, String winnerId);
}
