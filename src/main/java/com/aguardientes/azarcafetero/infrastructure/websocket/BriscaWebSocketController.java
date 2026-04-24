package com.aguardientes.azarcafetero.infrastructure.websocket;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.port.input.*;
import com.aguardientes.azarcafetero.application.service.GameService;
import com.aguardientes.azarcafetero.domain.model.BotDifficulty;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Objects;

/**
 * Controlador WebSocket de Brisca.
 *
 * Nuevo mensaje respecto a la versión anterior:
 *   SEND /app/game/{gameId}/addBot
 *   Body: { "difficulty": "EASY" | "MEDIUM" | "HARD" }
 */
@Controller
public class BriscaWebSocketController {

    private final CreateGameUseCase createGameUseCase;
    private final JoinGameUseCase joinGameUseCase;
    private final StartGameUseCase startGameUseCase;
    private final PlayCardUseCase playCardUseCase;
    private final GetGameStateUseCase getGameStateUseCase;
    private final GameService gameService;          // ← acceso directo para addBot
    private final SimpMessagingTemplate messagingTemplate;

    public BriscaWebSocketController(
            CreateGameUseCase createGameUseCase,
            JoinGameUseCase joinGameUseCase,
            StartGameUseCase startGameUseCase,
            PlayCardUseCase playCardUseCase,
            GetGameStateUseCase getGameStateUseCase,
            GameService gameService,
            SimpMessagingTemplate messagingTemplate) {
        this.createGameUseCase  = Objects.requireNonNull(createGameUseCase);
        this.joinGameUseCase    = Objects.requireNonNull(joinGameUseCase);
        this.startGameUseCase   = Objects.requireNonNull(startGameUseCase);
        this.playCardUseCase    = Objects.requireNonNull(playCardUseCase);
        this.getGameStateUseCase = Objects.requireNonNull(getGameStateUseCase);
        this.gameService        = Objects.requireNonNull(gameService);
        this.messagingTemplate  = Objects.requireNonNull(messagingTemplate);
    }

    // ─── Mensajes existentes ──────────────────────────────────────────────────

    @MessageMapping("/game/create")
    public void createGame(CreateGameCommand command) {
        GameStateDTO state = createGameUseCase.createGame(command);
        messagingTemplate.convertAndSend("/topic/lobby", state);
        messagingTemplate.convertAndSend("/topic/game/" + command.gameId(), state);
    }

    @MessageMapping("/game/{gameId}/join")
    public void joinGame(JoinGameCommand command, @DestinationVariable String gameId) {
        joinGameUseCase.joinGame(command);
        broadcast(gameId);
    }

    @MessageMapping("/game/{gameId}/start")
    public void startGame(StartGameCommand command, @DestinationVariable String gameId) {
        startGameUseCase.startGame(command);
        broadcast(gameId);
    }

    @MessageMapping("/game/{gameId}/play")
    public void playCard(PlayCardCommand command, @DestinationVariable String gameId) {
        playCardUseCase.playCard(command);
        broadcast(gameId);
    }

    @MessageMapping("/game/{gameId}/state")
    public void getGameState(@DestinationVariable String gameId) {
        broadcast(gameId);
    }

    // ─── NUEVO: agregar bot ───────────────────────────────────────────────────

    /**
     * Agrega un bot a la partida.
     *
     * Mensaje cliente:
     *   stompClient.send('/app/game/GAME_123/addBot', {}, JSON.stringify({
     *       difficulty: 'MEDIUM'   // opcional, default MEDIUM
     *   }));
     *
     * Respuesta broadcast en /topic/game/{gameId} con el estado actualizado.
     */
    @MessageMapping("/game/{gameId}/addBot")
    public void addBot(AddBotRequest request, @DestinationVariable String gameId) {
        BotDifficulty difficulty = request != null && request.difficulty() != null
                ? request.difficulty()
                : BotDifficulty.MEDIUM;

        AddBotCommand command = new AddBotCommand(gameId, difficulty);
        gameService.addBot(command);
        broadcast(gameId);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private void broadcast(String gameId) {
        GameStateDTO state = getGameStateUseCase.getGameStateWithAllHands(gameId);
        messagingTemplate.convertAndSend("/topic/game/" + gameId, state);
    }

    // ─── DTO de entrada para addBot ───────────────────────────────────────────

    /**
     * Payload del mensaje addBot.
     * Se define como inner record para no crear un archivo extra.
     */
    public record AddBotRequest(BotDifficulty difficulty) {}
}