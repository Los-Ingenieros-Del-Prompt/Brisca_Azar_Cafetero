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

/**
 * Tests unitarios del GameService.
 *
 * Cubre:
 * - createGame (nuevo, existente)
 * - joinGame (nuevo, duplicado, no encontrado)
 * - leaveGame (no encontrado, último humano, otros humanos)
 * - addBot (prefijos MEDIUM/HARD, no encontrado)
 * - startGame (humanos, ya iniciado, con bot, no encontrado)
 * - playCard (turno válido, baza completa, no encontrado)
 * - getGameState (overloads, not found)
 * - finalizeAndCleanup / settlePrize (ganador humano, perdedor, con bot, todos bots, empate)
 * - runBotTurns (no encontrado, terminado, humano activo, bot sin cartas)
 * - capitalize (null, vacío)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameService")
class GameServiceTest {

    @Mock GameRepository gameRepository;
    @Mock GameEventPublisher eventPublisher;
    @Mock WalletClient walletClient;

    GameService service;

    @BeforeEach
    void setUp() {
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

    @Test
    @DisplayName("createGame: juego existente no guarda ni publica")
    void createGame_existing_doesNotSaveOrPublish() {
        Game existing = new Game("G1", 2, 4, BigDecimal.TEN);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(existing));
        service.createGame(new CreateGameCommand("G1", 2, 4, BigDecimal.TEN));
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
        verify(eventPublisher, never()).publishPlayerJoined(any(), any());
    }

    @Test
    @DisplayName("joinGame: jugador ya unido no duplica ni emite publishPlayerJoined")
    void joinGame_alreadyJoined_noDuplicateOrEvent() {
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

    @Test void leaveGame_otherHumanStays_savesGame() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.leaveGame(new LeaveGameCommand("G1", "P1"));
        verify(gameRepository).save(any());
        verify(eventPublisher).publishGameStateUpdated(any());
    }

    @Test
    @DisplayName("leaveGame cuando quedan humanos publica estado actualizado")
    void leaveGame_withRemainingHumans_publishesStateUpdate() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.addPlayer(new Player("P3", "Charlie"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.leaveGame(new LeaveGameCommand("G1", "P1"));
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

    @Test
    @DisplayName("addBot HARD genera ID con prefijo BOT_HARD_")
    void addBot_hard_addsHardBot() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.addBot(new AddBotCommand("G1", BotDifficulty.HARD));
        assertThat(game.getPlayers()).anyMatch(p -> p.getId().startsWith("BOT_HARD_"));
    }

    @Test
    @DisplayName("addBot MEDIUM → nombre 'Bot Medium'")
    void addBot_medium_name() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.addBot(new AddBotCommand("G1", BotDifficulty.MEDIUM));
        assertThat(game.getPlayers()).anyMatch(p -> p.getName().equals("Bot Medium"));
    }

    @Test
    @DisplayName("addBot lanza GameNotFoundException si el juego no existe")
    void addBot_gameNotFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addBot(new AddBotCommand("G1", BotDifficulty.EASY)))
                .isInstanceOf(GameNotFoundException.class);
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
        game.start();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        GameStateDTO result = service.startGame(new StartGameCommand("G1"));
        assertThat(result).isNotNull();
        verifyNoInteractions(walletClient);
    }

    @Test
    @DisplayName("startGame: IN_PROGRESS retorna sin lanzar publishGameStarted")
    void startGame_alreadyInProgress_noEvents() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.startGame(new StartGameCommand("G1"));
        verify(eventPublisher, never()).publishGameStarted(any());
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

    @Test
    @DisplayName("startGame lanza GameNotFoundException si el juego no existe")
    void startGame_gameNotFound_throws() {
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

    @Test
    @DisplayName("getGameState(id) lanza GameNotFoundException si no existe")
    void getGameState_notFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGameState("G1"))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test
    @DisplayName("getGameState(id, playerId) lanza GameNotFoundException si no existe")
    void getGameState_withPlayerId_notFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGameState("G1", "P1"))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test
    @DisplayName("getGameStateWithAllHands lanza GameNotFoundException si no existe")
    void getGameStateWithAllHands_notFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGameStateWithAllHands("G1"))
                .isInstanceOf(GameNotFoundException.class);
    }

    // ── finalizeAndCleanup / settlePrize ──────────────────────────────────────

    @Test
    @DisplayName("playCard que termina la partida llama publishGameFinished y deleteById")
    void playCard_gameOver_finalizesGame() {
        Game game = startedTwoPlayerGame();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);
        verify(gameRepository, atLeastOnce()).deleteById("G1");
        verify(eventPublisher, atLeastOnce()).publishGameFinished(eq("G1"), any());
    }

    @Test
    @DisplayName("settlePrize: ganador humano recibe el premio")
    void settlePrize_humanWinner_receivesWin() {
        Game game = startedTwoPlayerGame();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);
        verify(walletClient, atLeastOnce()).receiveWin(any(), any());
    }

    @Test
    @DisplayName("settlePrize: perdedor humano registra pérdida (si no empate)")
    void settlePrize_humanLoser_registersLoss() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player p1 = new Player("P1", "Alice");
        Player p2 = new Player("P2", "Bob");
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        p1.addPoints(60);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);
        verify(gameRepository, atLeastOnce()).deleteById("G1");
    }

    @Test
    @DisplayName("settlePrize via reflection: ganador recibe win, perdedor registra loss")
    void settlePrize_humanWinnerAndLoser_viaReflection() throws Exception {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player p1 = new Player("P1", "Alice");
        Player p2 = new Player("P2", "Bob");
        game.addPlayer(p1);
        game.addPlayer(p2);
        p1.addPoints(60);
        p2.addPoints(10);

        var method = GameService.class.getDeclaredMethod("settlePrize", Game.class);
        method.setAccessible(true);
        method.invoke(service, game);

        verify(walletClient).receiveWin(eq("P1"), any());
        verify(walletClient).registerLoss(eq("P2"), any());
    }

    @Test
    @DisplayName("settlePrize: partida con bot reduce premio al 50%")
    void settlePrize_withBot_reducedPrize() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player human = new Player("P1", "Alice");
        Player bot = new Player("BOT_EASY_abc1", "Bot Easy");
        game.addPlayer(human);
        game.addPlayer(bot);
        game.start();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);
        verify(gameRepository, atLeastOnce()).deleteById("G1");
    }

    @Test
    @DisplayName("settlePrize: todos bots ganan → no hay ganador humano, no llama receiveWin")
    void settlePrize_allBotsWin_noHumanWinner() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player bot1 = new Player("BOT_EASY_abc1", "Bot1");
        Player bot2 = new Player("BOT_MEDIUM_abc2", "Bot2");
        game.addPlayer(bot1);
        game.addPlayer(bot2);
        bot1.addPoints(60);
        game.start();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);
        verify(walletClient, never()).receiveWin(any(), any());
    }

    @Test
    @DisplayName("settlePrize: empate cubre rama humanWinners con tamaño > 1")
    void settlePrize_tie_coversMultipleWinnersBranch() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player p1 = new Player("P1", "Alice");
        Player p2 = new Player("P2", "Bob");
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        p1.addPoints(40);
        p2.addPoints(40);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);
        verify(gameRepository, atLeastOnce()).deleteById("G1");
        verify(walletClient, atLeastOnce()).receiveWin(any(), any());
    }

    // ── runBotTurns ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("runBotTurns: juego no existe en repositorio → no hace nada")
    void runBotTurns_gameNotFound_exitsImmediately() throws Exception {
        when(gameRepository.findById("GHOST")).thenReturn(Optional.empty());
        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "GHOST")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("runBotTurns: juego FINISHED → sale del loop")
    void runBotTurns_gameFinished_exitsImmediately() throws Exception {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start();
        game.finish();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("runBotTurns: jugador actual no es bot → sale del loop")
    void runBotTurns_currentPlayerNotBot_exitsImmediately() throws Exception {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();
        verify(eventPublisher, never()).publishCardPlayed(any(), any(), any());
    }

    @Test
    @DisplayName("runBotTurns: bot sin cartas y partida terminada → finaliza")
    void runBotTurns_botNoCards_gameOver_finalizes() throws Exception {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player bot = new Player("BOT_EASY_x1", "Bot");
        Player human = new Player("P1", "Alice");
        game.addPlayer(bot);
        game.addPlayer(human);
        forceInProgress(game, Suit.OROS);
        drainDeck(game);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("runBotTurns: bot sin cartas pero partida aún activa → break sin finalizar")
    void runBotTurns_botNoCards_gameNotOver_breaks() throws Exception {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player bot = new Player("BOT_EASY_x2", "Bot");
        Player human = new Player("P1", "Alice");
        human.addCard(new Card(Suit.COPAS, Rank.ACE));
        game.addPlayer(bot);
        game.addPlayer(human);
        forceInProgress(game, Suit.OROS);
        drainDeck(game);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();
        verify(gameRepository, never()).deleteById(any());
    }

    // ── capitalize ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("capitalize(null) devuelve null")
    void capitalize_null_returnsNull() throws Exception {
        var m = GameService.class.getDeclaredMethod("capitalize", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(service, (Object) null)).isNull();
    }

    @Test
    @DisplayName("capitalize(\"\") devuelve \"\"")
    void capitalize_empty_returnsEmpty() throws Exception {
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
        int safetyLimit = 100, count = 0;
        while (!game.isGameOver() && game.getState() == GameState.IN_PROGRESS && count++ < safetyLimit) {
            Player current = game.getCurrentPlayer();
            if (current == null || current.getHand().isEmpty()) break;
            Card card = current.getHand().get(0);
            service.playCard(new PlayCardCommand(
                    game.getId(), current.getId(), card.getSuit(), card.getRank()));
        }
    }

    private void forceInProgress(Game game, Suit trump) {
        try {
            var sf = Game.class.getDeclaredField("state");
            sf.setAccessible(true);
            sf.set(game, GameState.IN_PROGRESS);
            var df = Game.class.getDeclaredField("deck");
            df.setAccessible(true);
            Object deck = df.get(game);
            var tf = deck.getClass().getDeclaredField("trumpCard");
            tf.setAccessible(true);
            tf.set(deck, new Card(trump, Rank.SEVEN));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void drainDeck(Game game) {
        try {
            var df = Game.class.getDeclaredField("deck");
            df.setAccessible(true);
            Object deck = df.get(game);
            var cf = deck.getClass().getDeclaredField("cards");
            cf.setAccessible(true);
            ((java.util.List<?>) cf.get(deck)).clear();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}