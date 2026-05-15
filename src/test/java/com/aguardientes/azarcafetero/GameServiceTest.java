package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.service.*;
import com.aguardientes.azarcafetero.application.port.output.*;
import com.aguardientes.azarcafetero.domain.exception.GameNotFoundException;
import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GameService")
class GameServiceTest {

    @Mock GameRepository gameRepository;
    @Mock GameEventPublisher eventPublisher;
    @Mock WalletClient walletClient;

    GameService service;

    @BeforeEach void setUp() {
        service = new GameService(
                gameRepository, eventPublisher,
                new GameRules(), new TrickResolver(),
                new ScoreCalculator(), new GameMapper(),
                walletClient, new BriscaBotDecisionService()
        );
    }

    // ── createGame ────────────────────────────────────────────────────────────

    @Test void createGame_newGame_savesAndPublishes() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        GameStateDTO result = service.createGame(cmd("G1"));
        assertThat(result.gameId()).isEqualTo("G1");
        verify(gameRepository).save(any());
        verify(eventPublisher).publishGameCreated("G1");
    }

    @Test void createGame_existingGame_returnsExisting() {
        Game existing = new Game("G1", 2, 4, BigDecimal.TEN);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(existing));
        GameStateDTO result = service.createGame(cmd("G1"));
        assertThat(result.gameId()).isEqualTo("G1");
        verify(gameRepository, never()).save(any());
    }

    // ── joinGame ──────────────────────────────────────────────────────────────

    @Test void joinGame_newPlayer_addsAndPublishes() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.joinGame(new JoinGameCommand("G1", "P1", "Alice"));
        assertThat(game.getPlayerById("P1")).isNotNull();
        verify(eventPublisher).publishPlayerJoined("G1", "P1");
    }

    @Test void joinGame_alreadyJoined_doesNotDuplicate() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.joinGame(new JoinGameCommand("G1", "P1", "Alice"));
        assertThat(game.getPlayerCount()).isEqualTo(1);
        verify(eventPublisher, never()).publishPlayerJoined(any(), any());
    }

    @Test void joinGame_gameNotFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.joinGame(new JoinGameCommand("G1", "P1", "A")))
                .isInstanceOf(GameNotFoundException.class);
    }

    // ── leaveGame ─────────────────────────────────────────────────────────────

    @Test void leaveGame_gameNotFound_returnsNull() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThat(service.leaveGame(new LeaveGameCommand("G1", "P1"))).isNull();
    }

    @Test void leaveGame_lastHumanLeaves_deletesGame() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.leaveGame(new LeaveGameCommand("G1", "P1"));
        verify(gameRepository).deleteById("G1");
    }

    @Test void leaveGame_otherHumanStays_savesGame() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.leaveGame(new LeaveGameCommand("G1", "P1"));
        verify(gameRepository).save(any());
        verify(eventPublisher).publishGameStateUpdated(any());
    }

    // ── addBot ────────────────────────────────────────────────────────────────

    @Test void addBot_addsPlayerWithBotPrefix() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.addBot(new AddBotCommand("G1", BotDifficulty.MEDIUM));
        assertThat(game.getPlayers()).anyMatch(p -> p.getId().startsWith("BOT_MEDIUM_"));
    }

    // ── startGame ─────────────────────────────────────────────────────────────

    @Test void startGame_withHumanPlayers_deductsBets() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.startGame(new StartGameCommand("G1"));
        verify(walletClient, times(2)).placeBet(any(), any());
        assertThat(game.getState()).isEqualTo(GameState.IN_PROGRESS);
    }

    @Test void startGame_alreadyStarted_returnsCurrentState() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start(); // already started
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        GameStateDTO result = service.startGame(new StartGameCommand("G1"));
        assertThat(result).isNotNull();
        verifyNoInteractions(walletClient);
    }

    @Test void startGame_withBot_doesNotDeductBotBet() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("BOT_EASY_abc", "Bot"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.startGame(new StartGameCommand("G1"));
        verify(walletClient, times(1)).placeBet(eq("P1"), any()); // solo humano
        verify(walletClient, never()).placeBet(contains("BOT"), any());
    }

    // ── playCard (partida humano vs humano, sin bots) ─────────────────────────

    @Test void playCard_validTurn_publishesCardPlayed() {
        Game game = startedTwoPlayerGame();
        Player current = game.getCurrentPlayer();
        Card card = current.getHand().get(0);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.playCard(new PlayCardCommand("G1", current.getId(), card.getSuit(), card.getRank()));
        verify(eventPublisher).publishCardPlayed(eq("G1"), eq(current.getId()), any());
    }

    @Test void playCard_completeTrick_resolvesAndPublishes() {
        Game game = startedTwoPlayerGame();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        // Jugador 1
        Player first = game.getCurrentPlayer();
        Card c1 = first.getHand().get(0);
        service.playCard(new PlayCardCommand("G1", first.getId(), c1.getSuit(), c1.getRank()));
        // Jugador 2
        Player second = game.getCurrentPlayer();
        Card c2 = second.getHand().get(0);
        service.playCard(new PlayCardCommand("G1", second.getId(), c2.getSuit(), c2.getRank()));
        verify(eventPublisher, atLeastOnce()).publishTrickCompleted(eq("G1"), any(), anyInt());
    }

    @Test void playCard_gameNotFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.playCard(
                new PlayCardCommand("G1", "P1", Suit.OROS, Rank.ACE)))
                .isInstanceOf(GameNotFoundException.class);
    }

    // ── getGameState ──────────────────────────────────────────────────────────

    @Test void getGameState_byId_returnsPublicState() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        assertThat(service.getGameState("G1").gameId()).isEqualTo("G1");
    }

    @Test void getGameState_withPlayerId_returnsFilteredState() {
        Game game = startedTwoPlayerGame();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        GameStateDTO dto = service.getGameState("G1", "P1");
        PlayerDTO p1dto = dto.players().stream().filter(p -> p.id().equals("P1")).findFirst().orElseThrow();
        assertThat(p1dto.hand()).isNotEmpty();
    }

    @Test void getGameStateWithAllHands_allHandsVisible() {
        Game game = startedTwoPlayerGame();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        GameStateDTO dto = service.getGameStateWithAllHands("G1");
        dto.players().forEach(p -> assertThat(p.hand()).isNotEmpty());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CreateGameCommand cmd(String id) {
        return new CreateGameCommand(id, 2, 4, BigDecimal.TEN);
    }

    private Game startedTwoPlayerGame() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start();
        return game;
    }
}