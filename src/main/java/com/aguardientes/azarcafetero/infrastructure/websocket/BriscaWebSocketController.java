package com.aguardientes.azarcafetero.infrastructure.websocket;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.port.input.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.Objects;

@Controller
public class BriscaWebSocketController {
    
    private final CreateGameUseCase createGameUseCase;
    private final JoinGameUseCase joinGameUseCase;
    private final StartGameUseCase startGameUseCase;
    private final PlayCardUseCase playCardUseCase;
    private final GetGameStateUseCase getGameStateUseCase;

    public BriscaWebSocketController(
            CreateGameUseCase createGameUseCase,
            JoinGameUseCase joinGameUseCase,
            StartGameUseCase startGameUseCase,
            PlayCardUseCase playCardUseCase,
            GetGameStateUseCase getGameStateUseCase) {
        this.createGameUseCase = Objects.requireNonNull(createGameUseCase, "CreateGameUseCase cannot be null");
        this.joinGameUseCase = Objects.requireNonNull(joinGameUseCase, "JoinGameUseCase cannot be null");
        this.startGameUseCase = Objects.requireNonNull(startGameUseCase, "StartGameUseCase cannot be null");
        this.playCardUseCase = Objects.requireNonNull(playCardUseCase, "PlayCardUseCase cannot be null");
        this.getGameStateUseCase = Objects.requireNonNull(getGameStateUseCase, "GetGameStateUseCase cannot be null");
    }

    @MessageMapping("/game/create")
    @SendTo("/topic/lobby")
    public GameStateDTO createGame(CreateGameCommand command) {
        return createGameUseCase.createGame(command);
    }

    @MessageMapping("/game/{gameId}/join")
    @SendTo("/topic/game/{gameId}")
    public GameStateDTO joinGame(
            JoinGameCommand command,
            @DestinationVariable String gameId) {
        return joinGameUseCase.joinGame(command);
    }

    @MessageMapping("/game/{gameId}/start")
    @SendTo("/topic/game/{gameId}")
    public GameStateDTO startGame(
            StartGameCommand command,
            @DestinationVariable String gameId) {
        return startGameUseCase.startGame(command);
    }

    @MessageMapping("/game/{gameId}/play")
    @SendTo("/topic/game/{gameId}")
    public GameStateDTO playCard(
            PlayCardCommand command,
            @DestinationVariable String gameId) {
        return playCardUseCase.playCard(command);
    }

    @MessageMapping("/game/{gameId}/state")
    public GameStateDTO getGameState(@DestinationVariable String gameId) {
        return getGameStateUseCase.getGameState(gameId);
    }
}
