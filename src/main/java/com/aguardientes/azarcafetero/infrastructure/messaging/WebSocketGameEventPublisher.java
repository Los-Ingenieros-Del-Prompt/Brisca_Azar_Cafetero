package com.aguardientes.azarcafetero.infrastructure.messaging;

import com.aguardientes.azarcafetero.application.dto.GameStateDTO;
import com.aguardientes.azarcafetero.application.port.output.GameEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class WebSocketGameEventPublisher implements GameEventPublisher {
    
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketGameEventPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate, "MessagingTemplate cannot be null");
    }

    @Override
    public void publishGameCreated(String gameId) {
        sendGameEvent(gameId, "GAME_CREATED", Map.of("gameId", gameId));
    }

    @Override
    public void publishPlayerJoined(String gameId, String playerId) {
        sendGameEvent(gameId, "PLAYER_JOINED", Map.of(
                "gameId", gameId,
                "playerId", playerId
        ));
    }

    @Override
    public void publishGameStarted(String gameId) {
        sendGameEvent(gameId, "GAME_STARTED", Map.of("gameId", gameId));
    }

    @Override
    public void publishCardPlayed(String gameId, String playerId, String cardInfo) {
        sendGameEvent(gameId, "CARD_PLAYED", Map.of(
                "gameId", gameId,
                "playerId", playerId,
                "card", cardInfo
        ));
    }

    @Override
    public void publishTrickCompleted(String gameId, String winnerId, int points) {
        sendGameEvent(gameId, "TRICK_COMPLETED", Map.of(
                "gameId", gameId,
                "winnerId", winnerId,
                "points", points
        ));
    }

    @Override
    public void publishGameStateUpdated(GameStateDTO gameState) {
        String destination = "/topic/game/" + gameState.gameId();
        messagingTemplate.convertAndSend(destination, gameState);
    }

    @Override
    public void publishGameFinished(String gameId, String winnerId) {
        sendGameEvent(gameId, "GAME_FINISHED", Map.of(
                "gameId", gameId,
                "winnerId", winnerId != null ? winnerId : "DRAW"
        ));
    }

    private void sendGameEvent(String gameId, String eventType, Map<String, Object> data) {
        String destination = "/topic/game/" + gameId + "/events";
        
        Map<String, Object> event = new HashMap<>();
        event.put("type", eventType);
        event.put("data", data);
        event.put("timestamp", System.currentTimeMillis());
        
        messagingTemplate.convertAndSend(destination, (Object) event);
    }
}
