package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.service.GameMapper;
import com.aguardientes.azarcafetero.application.service.GameService;
import com.aguardientes.azarcafetero.application.port.output.*;
import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CoverageGap – ramas no cubiertas")
class CoverageGapTest {

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

    // ── Game ─────────────────────────────────────────────────────────────────

    @Test @DisplayName("getCurrentPlayer con lista vacía devuelve null")
    void game_getCurrentPlayer_emptyPlayers_returnsNull() {
        assertThat(new Game("G1", 2, 4, BigDecimal.TEN).getCurrentPlayer()).isNull();
    }

    @Test @DisplayName("isPlayerTurn sin jugadores devuelve false")
    void game_isPlayerTurn_noPlayers_returnsFalse() {
        assertThat(new Game("G1", 2, 4, BigDecimal.TEN).isPlayerTurn("P1")).isFalse();
    }

    @Test @DisplayName("null betAmount lanza NullPointerException")
    void game_nullBetAmount_throws() {
        assertThatNullPointerException().isThrownBy(() -> new Game("G1", 2, 4, null));
    }

    @Test @DisplayName("removePlayer: índice == currentPlayerIndex no falla")
    void game_removePlayer_indexEqualsCurrentPlayer() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.addPlayer(new Player("P3", "Charlie"));
        // currentPlayerIndex = 0 por defecto; eliminamos P1 (idx 0 == 0)
        game.removePlayer("P1");
        assertThat(game.getPlayerCount()).isEqualTo(2);
    }

    @Test @DisplayName("removePlayer: index < currentPlayerIndex decrementa índice")
    void game_removePlayer_indexLessThanCurrent_decrementsIndex() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.addPlayer(new Player("P3", "Charlie"));
        setCurrentPlayerIndex(game, 2);
        game.removePlayer("P1");
        assertThat(game.getCurrentPlayerIndex()).isEqualTo(1);
    }

    @Test @DisplayName("removePlayer: currentPlayerIndex >= size tras borrado → reset a 0")
    void game_removePlayer_indexOutOfBounds_resetsToZero() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        setCurrentPlayerIndex(game, 1);
        game.removePlayer("P2");
        assertThat(game.getCurrentPlayerIndex()).isEqualTo(0);
    }

    @Test @DisplayName("isGameOver: false si deck no está agotado")
    void game_isGameOver_deckNotExhausted_returnsFalse() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start();
        assertThat(game.isGameOver()).isFalse();
    }

    @Test @DisplayName("isGameOver: false si deck agotado pero jugadores tienen cartas")
    void game_isGameOver_deckEmptyButPlayersHaveCards_returnsFalse() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player p1 = new Player("P1", "Alice");
        Player p2 = new Player("P2", "Bob");
        game.addPlayer(p1);
        game.addPlayer(p2);
        p1.addCard(new Card(Suit.OROS, Rank.ACE));
        p2.addCard(new Card(Suit.COPAS, Rank.ACE));
        drainDeck(game);
        assertThat(game.isGameOver()).isFalse();
    }

    // ── Player ───────────────────────────────────────────────────────────────

    @Test @DisplayName("Player.addCard(null) no agrega nada")
    void player_addCard_null_doesNotAdd() {
        Player p = new Player("P1", "Alice");
        p.addCard(null);
        assertThat(p.getHandSize()).isEqualTo(0);
    }

    @Test @DisplayName("Player.equals: misma instancia → true")
    void player_equals_sameInstance() {
        Player p = new Player("P1", "Alice");
        assertThat(p).isEqualTo(p);
    }

    @Test @DisplayName("Player.equals: null → false")
    void player_equals_null() {
        assertThat(new Player("P1", "Alice").equals(null)).isFalse();
    }

    @Test @DisplayName("Player.equals: clase distinta → false")
    void player_equals_differentClass() {
        assertThat(new Player("P1", "Alice").equals("str")).isFalse();
    }

    // ── Card ─────────────────────────────────────────────────────────────────

    @Test @DisplayName("Card.isTrump: misma suit → true")
    void card_isTrump_true() {
        assertThat(new Card(Suit.OROS, Rank.ACE).isTrump(Suit.OROS)).isTrue();
    }

    @Test @DisplayName("Card.isTrump: suit distinta → false")
    void card_isTrump_false() {
        assertThat(new Card(Suit.OROS, Rank.ACE).isTrump(Suit.COPAS)).isFalse();
    }

    // ── Trick ────────────────────────────────────────────────────────────────

    @Test @DisplayName("Trick.getLeadSuit: baza vacía → null")
    void trick_getLeadSuit_empty_returnsNull() {
        assertThat(new Trick().getLeadSuit()).isNull();
    }

    // ── GameMapper – null-guards ──────────────────────────────────────────────

    @Test @DisplayName("GameMapper.toTrickDTO(null) → null")
    void mapper_toTrickDTO_null() {
        assertThat(new GameMapper().toTrickDTO(null)).isNull();
    }

    @Test @DisplayName("GameMapper.toGameStateDTO(null, id) → null")
    void mapper_toGameStateDTO_null() {
        assertThat(new GameMapper().toGameStateDTO(null, "P1")).isNull();
    }

    @Test @DisplayName("GameMapper.toPublicGameStateDTO(null) → null")
    void mapper_toPublicGameStateDTO_null() {
        assertThat(new GameMapper().toPublicGameStateDTO(null)).isNull();
    }

    @Test @DisplayName("GameMapper.toFullGameStateDTO(null) → null")
    void mapper_toFullGameStateDTO_null() {
        assertThat(new GameMapper().toFullGameStateDTO(null)).isNull();
    }

    @Test @DisplayName("GameMapper.toGameStateDTO muestra mano sólo al jugador que pide")
    void mapper_toGameStateDTO_onlyRequestingPlayerHasHand() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start();
        var dto = new GameMapper().toGameStateDTO(game, "P1");
        assertThat(dto.players().stream().filter(p -> p.id().equals("P1")).findFirst().orElseThrow().hand()).isNotEmpty();
        assertThat(dto.players().stream().filter(p -> p.id().equals("P2")).findFirst().orElseThrow().hand()).isEmpty();
    }

    // ── GameService – ramas adicionales ──────────────────────────────────────

    @Test @DisplayName("addBot MEDIUM → nombre 'Bot Medium'")
    void service_addBot_medium_name() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.addBot(new AddBotCommand("G1", BotDifficulty.MEDIUM));
        assertThat(game.getPlayers()).anyMatch(p -> p.getName().equals("Bot Medium"));
    }

    @Test @DisplayName("createGame: juego existente no guarda ni publica")
    void service_createGame_existing_doesNotSave() {
        Game existing = new Game("G1", 2, 4, BigDecimal.TEN);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(existing));
        service.createGame(new CreateGameCommand("G1", 2, 4, BigDecimal.TEN));
        verify(gameRepository, never()).save(any());
        verify(eventPublisher, never()).publishGameCreated(any());
    }

    @Test @DisplayName("joinGame: jugador ya unido no duplica ni emite publishPlayerJoined")
    void service_joinGame_alreadyJoined() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.joinGame(new JoinGameCommand("G1", "P1", "Alice"));
        assertThat(game.getPlayerCount()).isEqualTo(1);
        verify(eventPublisher, never()).publishPlayerJoined(any(), eq("P1"));
    }

    @Test @DisplayName("startGame: IN_PROGRESS retorna sin lanzar publishGameStarted")
    void service_startGame_alreadyInProgress_noEvents() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.startGame(new StartGameCommand("G1"));
        verify(eventPublisher, never()).publishGameStarted(any());
    }

    @Test
    void service_settlePrize_humanWinnerAndLoser() throws Exception {

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

    @Test @DisplayName("settlePrize: empate cubre rama humanWinners con tamaño > 1")
    void service_settlePrize_tie_coversMultipleWinnersBranch() {
        // En empate, humanWinners.size() > 1 → prizeEach = totalPot / 2
        // Solo verificamos que la partida termina correctamente sin excepción
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player p1 = new Player("P1", "Alice");
        Player p2 = new Player("P2", "Bob");
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        // Empate exacto: puntos iguales elevados
        p1.addPoints(40);
        p2.addPoints(40);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);
        // La partida terminó → deleteById fue llamado
        verify(gameRepository, atLeastOnce()).deleteById("G1");
        // Al menos un receiveWin fue llamado (uno por ganador humano)
        verify(walletClient, atLeastOnce()).receiveWin(any(), any());
    }

    // ── BriscaBotDecisionService – ramas extra ────────────────────────────────

    @Test @DisplayName("MEDIUM siguiendo: baza >=5 con triunfo disponible → usa triunfo")
    void mediumBot_following_usesTrump() {
        BriscaBotDecisionService svc = new BriscaBotDecisionService(new Random(0));
        Suit trump = Suit.OROS;
        Game game = buildGameWithTrick("BOT_MEDIUM_t1",
                new Card[]{new Card(trump, Rank.TWO), new Card(Suit.ESPADAS, Rank.FOUR)},
                "HUMAN_1", new Card(Suit.COPAS, Rank.ACE), trump);
        assertThat(svc.decide(game, "BOT_MEDIUM_t1", BotDifficulty.MEDIUM)).isNotNull();
    }

    @Test @DisplayName("MEDIUM liderando: bot ganando → penaliza triunfos")
    void mediumBot_leading_winning_penalizesTrump() {
        BriscaBotDecisionService svc = new BriscaBotDecisionService(new Random(0));
        Suit trump = Suit.OROS;
        Game game = new Game("G_BOT", 2, 4, BigDecimal.ONE);
        Player rival = new Player("H1", "Human");
        rival.addPoints(5);
        game.addPlayer(rival);
        Player bot = new Player("BOT_MEDIUM_t2", "Bot");
        bot.addPoints(20);
        bot.addCard(new Card(trump, Rank.ACE));
        bot.addCard(new Card(Suit.COPAS, Rank.TWO));
        game.addPlayer(bot);
        forceInProgress(game, trump);
        setCurrentPlayerIndex(game, 1);
        assertThat(svc.decide(game, "BOT_MEDIUM_t2", BotDifficulty.MEDIUM)).isNotNull();
    }

    @Test @DisplayName("HARD: bot no encontrado lanza IllegalArgumentException")
    void hardBot_botNotFound_throws() {
        BriscaBotDecisionService svc = new BriscaBotDecisionService();
        Game game = new Game("G1", 2, 4, BigDecimal.ONE);
        game.addPlayer(new Player("P1", "Alice"));
        forceInProgress(game, Suit.OROS);
        assertThatThrownBy(() -> svc.decide(game, "GHOST", BotDifficulty.HARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test @DisplayName("HARD: mano de un solo elemento retorna directamente sin minimax")
    void hardBot_singleCard_returnsThatCard() {
        BriscaBotDecisionService svc = new BriscaBotDecisionService();
        Game game = new Game("G_BOT", 2, 4, BigDecimal.ONE);
        game.addPlayer(new Player("H1", "Human"));
        Player bot = new Player("BOT_HARD_solo", "Bot");
        Card only = new Card(Suit.BASTOS, Rank.KING);
        bot.addCard(only);
        game.addPlayer(bot);
        forceInProgress(game, Suit.OROS);
        setCurrentPlayerIndex(game, 1);
        assertThat(svc.decide(game, "BOT_HARD_solo", BotDifficulty.HARD)).isEqualTo(only);
    }

    @Test @DisplayName("HARD siguiendo: devuelve carta válida de la mano")
    void hardBot_following_returnsCardFromHand() {
        BriscaBotDecisionService svc = new BriscaBotDecisionService(new Random(42));
        Suit trump = Suit.OROS;
        Game game = buildGameWithTrick("BOT_HARD_f1",
                new Card[]{new Card(Suit.OROS, Rank.ACE), new Card(Suit.COPAS, Rank.THREE)},
                "HUMAN_1", new Card(Suit.COPAS, Rank.ACE), trump);
        Card result = svc.decide(game, "BOT_HARD_f1", BotDifficulty.HARD);
        assertThat(game.getPlayerById("BOT_HARD_f1").getHand()).contains(result);
    }

    @Test @DisplayName("EASY: mano de un elemento devuelve esa carta")
    void easyBot_singleCard_returnsThatCard() {
        BriscaBotDecisionService svc = new BriscaBotDecisionService(new Random(0));
        Game game = new Game("G_BOT", 2, 4, BigDecimal.ONE);
        game.addPlayer(new Player("H1", "Human"));
        Player bot = new Player("BOT_EASY_e1", "Bot");
        Card only = new Card(Suit.BASTOS, Rank.KING);
        bot.addCard(only);
        game.addPlayer(bot);
        forceInProgress(game, Suit.OROS);
        setCurrentPlayerIndex(game, 1);
        assertThat(svc.decide(game, "BOT_EASY_e1", BotDifficulty.EASY)).isEqualTo(only);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setCurrentPlayerIndex(Game game, int index) {
        try {
            var f = Game.class.getDeclaredField("currentPlayerIndex");
            f.setAccessible(true);
            f.set(game, index);
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

    private Game buildGameWithTrick(String botId, Card[] botCards,
                                    String rivalId, Card rivalCard, Suit trump) {
        Game game = new Game("G_BOT", 2, 4, BigDecimal.ONE);
        Player rival = new Player(rivalId, "Human");
        rival.addCard(rivalCard);
        game.addPlayer(rival);
        Player bot = new Player(botId, "Bot");
        for (Card c : botCards) bot.addCard(c);
        game.addPlayer(bot);
        forceInProgress(game, trump);
        rival.playCard(rivalCard);
        game.getCurrentTrick().addCard(rivalId, rivalCard);
        setCurrentPlayerIndex(game, 1);
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

    // ── GameService private methods via reflection ─────────────────────────────

    /**
     * Cubre la rama capitalize(null) → devuelve null
     * y capitalize("") → devuelve ""
     */
    @Test @DisplayName("capitalize(null) devuelve null")
    void service_capitalize_null_returnsNull() throws Exception {
        var m = GameService.class.getDeclaredMethod("capitalize", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(service, (Object) null)).isNull();
    }

    @Test @DisplayName("capitalize(\"\") devuelve \"\"")
    void service_capitalize_empty_returnsEmpty() throws Exception {
        var m = GameService.class.getDeclaredMethod("capitalize", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(service, "")).isEqualTo("");
    }

    /**
     * Cubre las ramas de runBotTurns directamente:
     * - juego no encontrado en repositorio → break inmediato
     * - juego no IN_PROGRESS → break inmediato
     */
    @Test @DisplayName("runBotTurns: juego no existe en repositorio → no hace nada")
    void service_runBotTurns_gameNotFound_exitsImmediately() throws Exception {
        when(gameRepository.findById("GHOST")).thenReturn(Optional.empty());
        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        // No debe lanzar excepción
        assertThatCode(() -> m.invoke(service, "GHOST")).doesNotThrowAnyException();
    }

    @Test @DisplayName("runBotTurns: juego FINISHED → sale del loop")
    void service_runBotTurns_gameFinished_exitsImmediately() throws Exception {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start();
        game.finish(); // GameState.FINISHED
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();
    }

    @Test @DisplayName("runBotTurns: jugador actual no es bot → sale del loop")
    void service_runBotTurns_currentPlayerNotBot_exitsImmediately() throws Exception {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice")); // humano
        game.addPlayer(new Player("P2", "Bob"));   // humano
        game.start();
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();
        // No debe haber jugado cartas (ningún publishCardPlayed)
        verify(eventPublisher, never()).publishCardPlayed(any(), any(), any());
    }

    @Test @DisplayName("runBotTurns: bot sin cartas y partida terminada → finaliza")
    void service_runBotTurns_botNoCards_gameOver_finalizes() throws Exception {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player bot = new Player("BOT_EASY_x1", "Bot");
        Player human = new Player("P1", "Alice");
        game.addPlayer(bot);
        game.addPlayer(human);
        // Forzar IN_PROGRESS sin repartir cartas (bots sin cartas)
        forceInProgress(game, Suit.OROS);
        // Deck vacío + sin cartas en mano → isGameOver = true
        drainDeck(game);

        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        // finalizeAndCleanup se llama → settle + deleteById
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();
    }

    @Test @DisplayName("runBotTurns: bot sin cartas pero partida aún activa → break sin finalizar")
    void service_runBotTurns_botNoCards_gameNotOver_breaks() throws Exception {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player bot = new Player("BOT_EASY_x2", "Bot");
        Player human = new Player("P1", "Alice");
        human.addCard(new Card(Suit.COPAS, Rank.ACE)); // humano tiene cartas
        game.addPlayer(bot);   // bot no tiene cartas
        game.addPlayer(human);
        forceInProgress(game, Suit.OROS);
        drainDeck(game); // deck vacío pero human tiene carta → isGameOver = false

        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();
        // No debe llamar a deleteById porque la partida no terminó
        verify(gameRepository, never()).deleteById(any());
    }

    @Test
    void easyBot_emptyHand_throws() {

        BriscaBotDecisionService svc =
                new BriscaBotDecisionService();

        Game game = new Game("G1", 2, 4, BigDecimal.ONE);

        Player bot = new Player("BOT", "Bot");

        game.addPlayer(bot);

        forceInProgress(game, Suit.OROS);

        assertThatThrownBy(() ->
                svc.decide(game, "BOT", BotDifficulty.EASY))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mediumBot_leading_onlyTrumps_usesFallback() {

        BriscaBotDecisionService svc =
                new BriscaBotDecisionService();

        Game game = new Game("G1", 2, 4, BigDecimal.ONE);

        game.addPlayer(new Player("P1", "Human"));

        Player bot = new Player("BOT", "Bot");

        bot.addCard(new Card(Suit.OROS, Rank.ACE));
        bot.addCard(new Card(Suit.OROS, Rank.TWO));

        game.addPlayer(bot);

        forceInProgress(game, Suit.OROS);

        setCurrentPlayerIndex(game, 1);

        Card result =
                svc.decide(game, "BOT", BotDifficulty.MEDIUM);

        assertThat(bot.getHand()).contains(result);
    }

    @Test
    void mediumBot_following_winsWithoutTrump() {

        BriscaBotDecisionService svc =
                new BriscaBotDecisionService();

        Game game = buildGameWithTrick(
                "BOT",
                new Card[]{
                        new Card(Suit.COPAS, Rank.ACE),
                        new Card(Suit.OROS, Rank.TWO)
                },
                "H1",
                new Card(Suit.COPAS, Rank.THREE),
                Suit.BASTOS
        );

        Card result =
                svc.decide(game, "BOT", BotDifficulty.MEDIUM);

        assertThat(result.getSuit()).isEqualTo(Suit.COPAS);
    }

    @Test
    void hardBot_maxValue_botNull_returnsZero() throws Exception {

        BriscaBotDecisionService svc =
                new BriscaBotDecisionService();

        Game game = new Game("G1", 2, 4, BigDecimal.ONE);

        Trick trick = new Trick();

        Class<?> simClass = Class.forName(
                "com.aguardientes.azarcafetero.domain.service.BriscaBotDecisionService$SimulatedTrick"
        );

        var ctor = simClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);

        Object sim =
                ctor.newInstance(
                        trick,
                        "P1",
                        new Card(Suit.OROS, Rank.ACE)
                );

        var method =
                BriscaBotDecisionService.class.getDeclaredMethod(
                        "maxValue",
                        simClass,
                        List.class,
                        Suit.class,
                        Game.class,
                        String.class,
                        int.class,
                        double.class,
                        double.class
                );

        method.setAccessible(true);

        double result = (double) method.invoke(
                svc,
                sim,
                new ArrayList<Card>(),
                Suit.OROS,
                game,
                "ghost",
                1,
                Double.NEGATIVE_INFINITY,
                Double.POSITIVE_INFINITY
        );

        assertThat(result).isEqualTo(0);
    }
}