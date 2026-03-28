package com.aguardientes.azarcafetero.infrastructure.websocket;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.port.input.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Objects;

@Controller
public class BriscaWebSocketController {
    
    private final CreateGameUseCase createGameUseCase;
    private final JoinGameUseCase joinGameUseCase;
    private final StartGameUseCase startGameUseCase;
    private final PlayCardUseCase playCardUseCase;
    private final GetGameStateUseCase getGameStateUseCase;
    private final SimpMessagingTemplate messagingTemplate;

    public BriscaWebSocketController(
            CreateGameUseCase createGameUseCase,
            JoinGameUseCase joinGameUseCase,
            StartGameUseCase startGameUseCase,
            PlayCardUseCase playCardUseCase,
            GetGameStateUseCase getGameStateUseCase,
            SimpMessagingTemplate messagingTemplate) {
        this.createGameUseCase = Objects.requireNonNull(createGameUseCase, "CreateGameUseCase cannot be null");
        this.joinGameUseCase = Objects.requireNonNull(joinGameUseCase, "JoinGameUseCase cannot be null");
        this.startGameUseCase = Objects.requireNonNull(startGameUseCase, "StartGameUseCase cannot be null");
        this.playCardUseCase = Objects.requireNonNull(playCardUseCase, "PlayCardUseCase cannot be null");
        this.getGameStateUseCase = Objects.requireNonNull(getGameStateUseCase, "GetGameStateUseCase cannot be null");
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate, "SimpMessagingTemplate cannot be null");
    }

    @MessageMapping("/game/create")
    public void createGame(CreateGameCommand command) {
        GameStateDTO state = createGameUseCase.createGame(command);
        messagingTemplate.convertAndSend("/topic/lobby", state);
        messagingTemplate.convertAndSend("/topic/game/" + command.gameId(), state);
    }

    @MessageMapping("/game/{gameId}/join")
    public void joinGame(
            JoinGameCommand command,
            @DestinationVariable String gameId) {
        GameStateDTO state = joinGameUseCase.joinGame(command);
        // Broadcast full state with all hands visible (for testing)
        GameStateDTO fullState = getGameStateUseCase.getGameStateWithAllHands(gameId);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, fullState);
    }

    @MessageMapping("/game/{gameId}/start")
    public void startGame(
            StartGameCommand command,
            @DestinationVariable String gameId) {
        startGameUseCase.startGame(command);
        // Broadcast full state with all hands visible
        GameStateDTO fullState = getGameStateUseCase.getGameStateWithAllHands(gameId);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, fullState);
    }

    @MessageMapping("/game/{gameId}/play")
    public void playCard(
            PlayCardCommand command,
            @DestinationVariable String gameId) {
        playCardUseCase.playCard(command);
        // Broadcast full state with all hands visible
        GameStateDTO fullState = getGameStateUseCase.getGameStateWithAllHands(gameId);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, fullState);
    }

    @MessageMapping("/game/{gameId}/state")
    public void getGameState(@DestinationVariable String gameId) {
        GameStateDTO state = getGameStateUseCase.getGameStateWithAllHands(gameId);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, state);
    }
}
