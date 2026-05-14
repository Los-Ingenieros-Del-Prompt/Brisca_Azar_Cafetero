package com.aguardientes.azarcafetero.infrastructure.websocket;

import com.aguardientes.azarcafetero.application.dto.LeaveGameCommand;
import com.aguardientes.azarcafetero.application.service.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Objects;

@Component
public class WebSocketEventListener {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final GameService gameService;

    public WebSocketEventListener(GameService gameService) {
        this.gameService = Objects.requireNonNull(gameService, "GameService cannot be null");
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes == null) return;

        String playerId = (String) sessionAttributes.get("playerId");
        String playerName = (String) sessionAttributes.get("playerName");
        String gameId = (String) sessionAttributes.get("gameId");

        if (playerId != null && gameId != null) {
            logger.info("Player {} ({}) disconnected from game {}", playerName, playerId, gameId);
            
            LeaveGameCommand command = new LeaveGameCommand(gameId, playerId);
            gameService.leaveGame(command);
        }
    }
}
