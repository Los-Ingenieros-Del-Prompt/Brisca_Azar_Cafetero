package com.aguardientes.azarcafetero.application;

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

    @Test void createGame_existingGame_doesNotSaveOrPublish() {
        Game existing = new Game("G1", 2, 4, BigDecimal.TEN);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(existing));
        service.createGame(cmd("G1"));
        verify(gameRepository, never()).save(any());
        verify(eventPublisher, never()).publishGameCreated(any());
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
        verify(eventPublisher, never()).publishPlayerJoined(any(), eq("P1"));
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

    @Test void leaveGame_otherHumanStays_savesAndPublishes() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.leaveGame(new LeaveGameCommand("G1", "P1"));
        verify(gameRepository).save(any());
        verify(eventPublisher).publishGameStateUpdated(any());
    }

    @Test void leaveGame_withRemainingHumans_publishesStateUpdate() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.addPlayer(new Player("P3", "Charlie"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.leaveGame(new LeaveGameCommand("G1", "P1"));
        verify(eventPublisher).publishGameStateUpdated(any());
    }

    // ── addBot ────────────────────────────────────────────────────────────────

    @Test void addBot_medium_addsPlayerWithCorrectPrefix() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.addBot(new AddBotCommand("G1", BotDifficulty.MEDIUM));
        assertThat(game.getPlayers()).anyMatch(p -> p.getId().startsWith("BOT_MEDIUM_"));
        assertThat(game.getPlayers()).anyMatch(p -> p.getName().equals("Bot Medium"));
    }

    @Test void addBot_hard_addsPlayerWithHardPrefix() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.addBot(new AddBotCommand("G1", BotDifficulty.HARD));
        assertThat(game.getPlayers()).anyMatch(p -> p.getId().startsWith("BOT_HARD_"));
    }

    @Test void addBot_gameNotFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addBot(new AddBotCommand("G1", BotDifficulty.EASY)))
                .isInstanceOf(GameNotFoundException.class);
    }

    // ── startGame ─────────────────────────────────────────────────────────────

    @Test void startGame_humanPlayers_deductsBetsAndPublishes() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.startGame(new StartGameCommand("G1"));
        verify(walletClient, times(2)).placeBet(any(), any());
        assertThat(game.getState()).isEqualTo(GameState.IN_PROGRESS);
    }

    @Test void startGame_withBot_doesNotDeductBotBet() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("BOT_EASY_abc", "Bot"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.startGame(new StartGameCommand("G1"));
        verify(walletClient, times(1)).placeBet(eq("P1"), any());
        verify(walletClient, never()).placeBet(contains("BOT"), any());
    }

    @Test void startGame_alreadyInProgress_returnsStateWithoutEvents() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.startGame(new StartGameCommand("G1"));
        verify(eventPublisher, never()).publishGameStarted(any());
        verifyNoInteractions(walletClient);
    }

    @Test void startGame_gameNotFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.startGame(new StartGameCommand("G1")))
                .isInstanceOf(GameNotFoundException.class);
    }

    // ── playCard ──────────────────────────────────────────────────────────────

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
        Player first = game.getCurrentPlayer();
        Card c1 = first.getHand().get(0);
        service.playCard(new PlayCardCommand("G1", first.getId(), c1.getSuit(), c1.getRank()));
        Player second = game.getCurrentPlayer();
        Card c2 = second.getHand().get(0);
        service.playCard(new PlayCardCommand("G1", second.getId(), c2.getSuit(), c2.getRank()));
        verify(eventPublisher, atLeastOnce()).publishTrickCompleted(eq("G1"), any(), anyInt());
    }

    @Test void playCard_gameOver_finalizesAndDeletes() {
        Game game = startedTwoPlayerGame();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);
        verify(gameRepository, atLeastOnce()).deleteById("G1");
        verify(eventPublisher, atLeastOnce()).publishGameFinished(eq("G1"), any());
    }

    @Test void playCard_humanWinner_receivesWin() {
        Game game = startedTwoPlayerGame();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);
        verify(walletClient, atLeastOnce()).receiveWin(any(), any());
    }

    @Test void playCard_withBot_reducedPrize_finishesOk() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("BOT_EASY_abc1", "Bot"));
        game.start();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);
        verify(gameRepository, atLeastOnce()).deleteById("G1");
    }

    @Test void playCard_allBots_noHumanWinner_noReceiveWin() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("BOT_EASY_b1", "Bot1"));
        game.addPlayer(new Player("BOT_MEDIUM_b2", "Bot2"));
        game.getPlayerById("BOT_EASY_b1").addPoints(60);
        game.start();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);
        verify(walletClient, never()).receiveWin(any(), any());
    }

    @Test void playCard_gameNotFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.playCard(
                new PlayCardCommand("G1", "P1", Suit.OROS, Rank.ACE)))
                .isInstanceOf(GameNotFoundException.class);
    }

    // ── getGameState ──────────────────────────────────────────────────────────

    @Test void getGameState_byId_returnsPublicState() {
        when(gameRepository.findById("G1")).thenReturn(Optional.of(new Game("G1", 2, 4, BigDecimal.TEN)));
        assertThat(service.getGameState("G1").gameId()).isEqualTo("G1");
    }

    @Test void getGameState_byId_notFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGameState("G1"))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test void getGameState_withPlayerId_showsOnlyThatPlayersHand() {
        Game game = startedTwoPlayerGame();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        GameStateDTO dto = service.getGameState("G1", "P1");
        assertThat(dto.players().stream().filter(p -> p.id().equals("P1"))
                .findFirst().orElseThrow().hand()).isNotEmpty();
    }

    @Test void getGameState_withPlayerId_notFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGameState("G1", "P1"))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test void getGameStateWithAllHands_allHandsVisible() {
        Game game = startedTwoPlayerGame();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.getGameStateWithAllHands("G1").players()
                .forEach(p -> assertThat(p.hand()).isNotEmpty());
    }

    @Test void getGameStateWithAllHands_notFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGameStateWithAllHands("G1"))
                .isInstanceOf(GameNotFoundException.class);
    }

    // ── runBotTurns (via reflexión) ───────────────────────────────────────────

    @Test void runBotTurns_gameNotFound_doesNothing() throws Exception {
        when(gameRepository.findById("GHOST")).thenReturn(Optional.empty());
        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "GHOST")).doesNotThrowAnyException();
    }

    @Test void runBotTurns_gameFinished_exitsLoop() throws Exception {
        Game game = startedTwoPlayerGame();
        game.finish();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();
    }

    @Test void runBotTurns_currentPlayerHuman_exitsLoop() throws Exception {
        Game game = startedTwoPlayerGame(); // ambos humanos
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();
        verify(eventPublisher, never()).publishCardPlayed(any(), any(), any());
    }

    // ── capitalize (via reflexión) ────────────────────────────────────────────

    @Test void capitalize_null_returnsNull() throws Exception {
        var m = GameService.class.getDeclaredMethod("capitalize", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(service, (Object) null)).isNull();
    }

    @Test void capitalize_empty_returnsEmpty() throws Exception {
        var m = GameService.class.getDeclaredMethod("capitalize", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(service, "")).isEqualTo("");
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

    private void playAllCards(Game game) {
        int limit = 100, count = 0;
        while (!game.isGameOver() && game.getState() == GameState.IN_PROGRESS && count++ < limit) {
            Player current = game.getCurrentPlayer();
            if (current == null || current.getHand().isEmpty()) break;
            Card card = current.getHand().get(0);
            service.playCard(new PlayCardCommand(
                    game.getId(), current.getId(), card.getSuit(), card.getRank()));
        }
    }
}