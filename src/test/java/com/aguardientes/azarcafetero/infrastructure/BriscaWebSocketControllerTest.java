package com.aguardientes.azarcafetero.infrastructure;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.port.input.*;
import com.aguardientes.azarcafetero.application.service.GameService;
import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.infrastructure.websocket.BriscaWebSocketController;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BriscaWebSocketController")
class BriscaWebSocketControllerTest {

    @Mock CreateGameUseCase createGameUseCase;
    @Mock JoinGameUseCase joinGameUseCase;
    @Mock StartGameUseCase startGameUseCase;
    @Mock PlayCardUseCase playCardUseCase;
    @Mock GetGameStateUseCase getGameStateUseCase;
    @Mock GameService gameService;
    @Mock SimpMessagingTemplate messagingTemplate;

    BriscaWebSocketController controller;

    private static final GameStateDTO FAKE_STATE = new GameStateDTO(
            "G1", GameState.WAITING_FOR_PLAYERS, List.of(), null, null, null, null, 0, List.of()
    );

    @BeforeEach void setUp() {
        controller = new BriscaWebSocketController(
                createGameUseCase, joinGameUseCase, startGameUseCase,
                playCardUseCase, getGameStateUseCase, gameService, messagingTemplate
        );
    }

    @Test void constructor_nullArg_throws() {
        assertThatNullPointerException().isThrownBy(() ->
                new BriscaWebSocketController(null, joinGameUseCase, startGameUseCase,
                        playCardUseCase, getGameStateUseCase, gameService, messagingTemplate));
    }

    @Test void createGame_broadcastsToLobbyAndGame() {
        CreateGameCommand cmd = new CreateGameCommand("G1", 2, 4, BigDecimal.TEN);
        when(createGameUseCase.createGame(cmd)).thenReturn(FAKE_STATE);

        controller.createGame(cmd);

        verify(messagingTemplate).convertAndSend("/topic/lobby", FAKE_STATE);
        verify(messagingTemplate).convertAndSend("/topic/game/G1", FAKE_STATE);
    }

    @Test void joinGame_callsUseCaseAndBroadcasts() {
        JoinGameCommand cmd = new JoinGameCommand("G1", "P1", "Alice");
        when(getGameStateUseCase.getGameStateWithAllHands("G1")).thenReturn(FAKE_STATE);

        SimpMessageHeaderAccessor accessor = mock(SimpMessageHeaderAccessor.class);
        Map<String, Object> attrs = new HashMap<>();
        when(accessor.getSessionAttributes()).thenReturn(attrs);

        controller.joinGame(cmd, "G1", accessor);

        verify(joinGameUseCase).joinGame(cmd);
        assertThat(attrs).containsEntry("playerId", "P1")
                .containsEntry("gameId", "G1");
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1"), eq(FAKE_STATE));
    }

    @Test void leaveGame_callsServiceAndBroadcasts() {
        LeaveGameCommand cmd = new LeaveGameCommand("G1", "P1");
        when(getGameStateUseCase.getGameStateWithAllHands("G1")).thenReturn(FAKE_STATE);

        controller.leaveGame(cmd, "G1");

        verify(gameService).leaveGame(cmd);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1"), eq(FAKE_STATE));
    }

    @Test void startGame_broadcastsState() {
        StartGameCommand cmd = new StartGameCommand("G1");
        when(getGameStateUseCase.getGameStateWithAllHands("G1")).thenReturn(FAKE_STATE);

        controller.startGame(cmd, "G1");

        verify(startGameUseCase).startGame(cmd);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1"), eq(FAKE_STATE));
    }

    @Test void playCard_broadcastsState() {
        PlayCardCommand cmd = new PlayCardCommand("G1", "P1", Suit.OROS, Rank.ACE);
        when(getGameStateUseCase.getGameStateWithAllHands("G1")).thenReturn(FAKE_STATE);

        controller.playCard(cmd, "G1");

        verify(playCardUseCase).playCard(cmd);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1"), eq(FAKE_STATE));
    }

    @Test void getGameState_broadcastsCurrentState() {
        when(getGameStateUseCase.getGameStateWithAllHands("G1")).thenReturn(FAKE_STATE);

        controller.getGameState("G1");

        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1"), eq(FAKE_STATE));
    }

    @Test void addBot_defaultDifficulty_whenRequestIsNull() {
        when(getGameStateUseCase.getGameStateWithAllHands("G1")).thenReturn(FAKE_STATE);

        controller.addBot(null, "G1");

        verify(gameService).addBot(argThat(c -> c.difficulty() == BotDifficulty.MEDIUM));
    }

    @Test void addBot_hardDifficulty_whenSpecified() {
        when(getGameStateUseCase.getGameStateWithAllHands("G1")).thenReturn(FAKE_STATE);
        var req = new BriscaWebSocketController.AddBotRequest(BotDifficulty.HARD);

        controller.addBot(req, "G1");

        verify(gameService).addBot(argThat(c -> c.difficulty() == BotDifficulty.HARD));
    }

    @Test void leaveGame_whenGameDeleted_doesNotThrow() {
        LeaveGameCommand cmd = new LeaveGameCommand("G1", "P1");
        when(getGameStateUseCase.getGameStateWithAllHands("G1"))
                .thenThrow(new com.aguardientes.azarcafetero.domain.exception.GameNotFoundException("G1"));

        assertThatCode(() -> controller.leaveGame(cmd, "G1")).doesNotThrowAnyException();
    }

}