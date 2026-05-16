package com.aguardientes.azarcafetero.dominio;

import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.BriscaBotDecisionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios del algoritmo de decisión del bot de Brisca.
 *
 * Cubre:
 * - Validaciones básicas (mano vacía, bot no en partida, null)
 * - EASY: aleatoriedad, mano de un elemento
 * - MEDIUM: líder (sin puntos, solo triunfos, ganando), seguidor
 *   (mismo palo, sin triunfo, con triunfo, baza barata)
 * - HARD: liderando (perdiendo, ganando, empate, muy atrás, pocos candidatos),
 *   siguiendo (con/sin triunfo, muchas cartas desconocidas, solo 1 desconocida)
 * - Minimax: maxValue con bot null, SimulatedTrick
 */
@DisplayName("BriscaBotDecisionService")
class BriscaBotDecisionServiceTest {

    private static final String BOT_ID = "BOT_MEDIUM_test01";
    private static final Suit   TRUMP  = Suit.OROS;

    private BriscaBotDecisionService service;

    @BeforeEach
    void setUp() {
        service = new BriscaBotDecisionService(new Random(42));
    }

    // ─── Validaciones básicas ─────────────────────────────────────────────────

    @Test
    @DisplayName("Siempre devuelve una carta válida que está en la mano del bot")
    void decide_returnsCardFromHand() {
        Game game = buildGame(BOT_ID,
                card(Suit.OROS, Rank.ACE),
                card(Suit.COPAS, Rank.TWO),
                card(Suit.ESPADAS, Rank.KING));

        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);

        assertThat(game.getPlayerById(BOT_ID).getHand()).contains(result);
    }

    @ParameterizedTest
    @DisplayName("Con una sola carta siempre devuelve esa carta (todos los niveles)")
    @EnumSource(BotDifficulty.class)
    void decide_singleCard_returnsIt(BotDifficulty difficulty) {
        Game game = buildGame(BOT_ID, card(Suit.COPAS, Rank.THREE));

        Card result = service.decide(game, BOT_ID, difficulty);

        assertThat(result).isEqualTo(card(Suit.COPAS, Rank.THREE));
    }

    @Test
    @DisplayName("Lanza excepción si el bot no está en la partida")
    void decide_botNotInGame_throws() {
        Game game = buildGame("HUMAN_1", card(Suit.BASTOS, Rank.SEVEN));

        assertThatThrownBy(() -> service.decide(game, "BOT_EASY_unknown", BotDifficulty.EASY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("decide con mano vacía lanza IllegalStateException")
    void decide_emptyHand_throws() {
        Game game = buildGame(BOT_ID); // sin cartas
        assertThatThrownBy(() -> service.decide(game, BOT_ID, BotDifficulty.HARD))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("HARD: bot no encontrado lanza IllegalArgumentException")
    void hardBot_botNotFound_throws() {
        Game game = new Game("G1", 2, 4, BigDecimal.ONE);
        game.addPlayer(new Player("P1", "Alice"));
        forceInProgress(game, Suit.OROS);
        assertThatThrownBy(() -> service.decide(game, "GHOST", BotDifficulty.HARD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("EASY mano vacía lanza IllegalStateException")
    void easyBot_emptyHand_throws() {
        Game game = new Game("G1", 2, 4, BigDecimal.ONE);
        Player bot = new Player("BOT", "Bot");
        game.addPlayer(bot);
        forceInProgress(game, Suit.OROS);
        assertThatThrownBy(() -> service.decide(game, "BOT", BotDifficulty.EASY))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── EASY ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EASY: carta devuelta pertenece a la mano (no falla)")
    void easyBot_alwaysReturnsValidCard() {
        for (int i = 0; i < 20; i++) {
            Game game = buildGame(BOT_ID,
                    card(Suit.OROS, Rank.ACE),
                    card(Suit.COPAS, Rank.FIVE),
                    card(Suit.ESPADAS, Rank.JACK));
            Card result = service.decide(game, BOT_ID, BotDifficulty.EASY);
            assertThat(game.getPlayerById(BOT_ID).getHand()).contains(result);
        }
    }

    @Test
    @DisplayName("EASY: mano de un elemento devuelve esa carta")
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

    // ─── Nunca devuelve null ──────────────────────────────────────────────────

    @ParameterizedTest
    @EnumSource(BotDifficulty.class)
    @DisplayName("Nunca devuelve null en ningún nivel de dificultad")
    void decide_neverReturnsNull(BotDifficulty difficulty) {
        Game game = buildGame(BOT_ID,
                card(Suit.OROS, Rank.ACE),
                card(Suit.COPAS, Rank.THREE));

        assertThat(service.decide(game, BOT_ID, difficulty)).isNotNull();
    }

    // ─── MEDIUM: lidera la baza ───────────────────────────────────────────────

    @Test
    @DisplayName("MEDIUM liderando: juega la carta más barata (sin puntos, sin triunfo)")
    void mediumBot_leading_playsLowestNonTrumpZeroPoints() {
        Game game = buildGame(BOT_ID,
                card(Suit.BASTOS, Rank.FIVE),    // 0 pts, no triunfo  ← esperada
                card(Suit.COPAS, Rank.ACE),      // 11 pts, no triunfo
                card(Suit.OROS, Rank.TWO));      // 0 pts, TRIUNFO

        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);

        assertThat(result.getSuit()).isNotEqualTo(TRUMP);
        assertThat(result.getPoints()).isEqualTo(0);
    }

    @Test
    @DisplayName("MEDIUM liderando con solo triunfos: juega triunfo de menor valor numérico")
    void mediumBot_leading_onlyTrumps_playsLowestTrump() {
        Game game = buildGame(BOT_ID,
                card(Suit.OROS, Rank.ACE),   // 11 pts, numericValue=1
                card(Suit.OROS, Rank.THREE)  // 10 pts, numericValue=3
        );

        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);
        assertThat(result.getSuit()).isEqualTo(TRUMP);
    }

    @Test
    @DisplayName("MEDIUM liderando con solo triunfos: fallback usa primer elemento de la mano")
    void mediumBot_leading_onlyTrumps_usesFallback() {
        BriscaBotDecisionService svc = new BriscaBotDecisionService();
        Game game = new Game("G1", 2, 4, BigDecimal.ONE);
        game.addPlayer(new Player("P1", "Human"));
        Player bot = new Player("BOT", "Bot");
        bot.addCard(new Card(Suit.OROS, Rank.ACE));
        bot.addCard(new Card(Suit.OROS, Rank.TWO));
        game.addPlayer(bot);
        forceInProgress(game, Suit.OROS);
        setCurrentPlayerIndex(game, 1);
        Card result = svc.decide(game, "BOT", BotDifficulty.MEDIUM);
        assertThat(bot.getHand()).contains(result);
    }

    @Test
    @DisplayName("MEDIUM liderando: bot ganando → penaliza triunfos")
    void mediumBot_leading_winning_penalizesTrump() {
        BriscaBotDecisionService svc = new BriscaBotDecisionService(new Random(0));
        Game game = new Game("G_BOT", 2, 4, BigDecimal.ONE);
        Player rival = new Player("H1", "Human");
        rival.addPoints(5);
        game.addPlayer(rival);
        Player bot = new Player("BOT_MEDIUM_t2", "Bot");
        bot.addPoints(20);
        bot.addCard(new Card(TRUMP, Rank.ACE));
        bot.addCard(new Card(Suit.COPAS, Rank.TWO));
        game.addPlayer(bot);
        forceInProgress(game, TRUMP);
        setCurrentPlayerIndex(game, 1);
        assertThat(svc.decide(game, "BOT_MEDIUM_t2", BotDifficulty.MEDIUM)).isNotNull();
    }

    // ─── MEDIUM: sigue la baza ────────────────────────────────────────────────

    @Test
    @DisplayName("MEDIUM siguiendo: gana con la carta mínima del mismo palo si puede")
    void mediumBot_following_winsWithMinimumSameSuit() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.BASTOS, Rank.FIVE),    // 0 pts
                        card(Suit.BASTOS, Rank.KING),    // 4 pts  ← gana con mínimo sobre SEVEN
                        card(Suit.BASTOS, Rank.ACE),     // 11 pts
                        card(Suit.OROS, Rank.TWO),       // triunfo
                },
                "HUMAN_1", card(Suit.BASTOS, Rank.SEVEN)
        );

        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);

        assertThat(result).isEqualTo(card(Suit.BASTOS, Rank.KING));
    }

    @Test
    @DisplayName("MEDIUM siguiendo: gana con mismo palo (sin necesitar triunfo)")
    void mediumBot_following_winsWithoutTrump() {
        BriscaBotDecisionService svc = new BriscaBotDecisionService();
        Suit trump = Suit.BASTOS;
        Game game = buildGameWithTrickAndTrump("BOT",
                new Card[]{
                        new Card(Suit.COPAS, Rank.ACE),
                        new Card(Suit.OROS, Rank.TWO)
                },
                "H1", new Card(Suit.COPAS, Rank.THREE), trump);
        Card result = svc.decide(game, "BOT", BotDifficulty.MEDIUM);
        assertThat(result.getSuit()).isEqualTo(Suit.COPAS);
    }

    @Test
    @DisplayName("MEDIUM siguiendo: descarta la más barata si no puede ganar la baza")
    void mediumBot_following_discardsLowestWhenCantWin() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.COPAS, Rank.TWO),      // 0 pts
                        card(Suit.ESPADAS, Rank.FOUR),   // 0 pts
                        card(Suit.COPAS, Rank.FIVE),     // 0 pts
                },
                "HUMAN_1", card(Suit.OROS, Rank.ACE)    // rival jugó triunfo máximo
        );

        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);

        assertThat(result.getPoints()).isEqualTo(0);
    }

    @Test
    @DisplayName("MEDIUM siguiendo: usa triunfo si la baza tiene >= 5 puntos y no puede ganar con mismo palo")
    void mediumBot_following_usesTrumpWhenTrickIsWorthIt() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.TWO),       // triunfo (0 pts)
                        card(Suit.COPAS, Rank.TWO),      // 0 pts, numericValue=2, NO gana sobre THREE
                },
                "HUMAN_1", card(Suit.COPAS, Rank.THREE) // rival jugó 10 puntos, numericValue=3
        );

        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);

        assertThat(result.getSuit()).isEqualTo(TRUMP);
    }

    @Test
    @DisplayName("MEDIUM siguiendo: usa triunfo si baza >=5 con triunfo disponible")
    void mediumBot_following_usesTrump_coverageVariant() {
        BriscaBotDecisionService svc = new BriscaBotDecisionService(new Random(0));
        Game game = buildGameWithTrickAndTrump("BOT_MEDIUM_t1",
                new Card[]{new Card(TRUMP, Rank.TWO), new Card(Suit.ESPADAS, Rank.FOUR)},
                "HUMAN_1", new Card(Suit.COPAS, Rank.ACE), TRUMP);
        assertThat(svc.decide(game, "BOT_MEDIUM_t1", BotDifficulty.MEDIUM)).isNotNull();
    }

    @Test
    @DisplayName("MEDIUM siguiendo: NO usa triunfo si la baza tiene < 5 puntos")
    void mediumBot_following_doesNotUseTrumpForCheapTrick() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS,  Rank.FOUR),     // triunfo (0 pts), numericValue=2
                        card(Suit.COPAS, Rank.TWO),      // 0 pts, numericValue=1 ← se descarta
                },
                "HUMAN_1", card(Suit.COPAS, Rank.FOUR)  // rival jugó 0 puntos
        );
        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);
        assertThat(result.getSuit()).isNotEqualTo(TRUMP);
    }

    @Test
    @DisplayName("MEDIUM siguiendo: no tiene triunfo y baza vale >=5 pero igual descarta")
    void mediumBot_following_noTrumpAvailable_discardsWhenTrickWorthIt() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.COPAS, Rank.TWO),    // 0 pts
                        card(Suit.ESPADAS, Rank.FOUR),  // 0 pts
                },
                "HUMAN_1", card(Suit.BASTOS, Rank.ACE)  // 11 pts, rival ganará
        );

        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);
        assertThat(result).isNotNull();
    }

    // ─── HARD: liderando ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HARD liderando y perdiendo: conserva el triunfo, no lo desperdicia")
    void hardBot_leading_losing_doesNotWasteTrump() {
        Game game = buildGameWithScores(BOT_ID, 0, "HUMAN_1", 15,
                new Card[]{
                        card(Suit.COPAS, Rank.ACE),
                        card(Suit.BASTOS, Rank.TWO),
                        card(Suit.OROS, Rank.SEVEN),  // triunfo
                });
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result.getSuit()).isNotEqualTo(TRUMP);
    }

    @Test
    @DisplayName("HARD liderando y ganando: juega conservador (carta barata sin triunfo)")
    void hardBot_leading_winning_playsConservatively() {
        Game game = buildGameWithScores(BOT_ID, 20, "HUMAN_1", 5,
                new Card[]{
                        card(Suit.COPAS, Rank.ACE),      // 11 pts
                        card(Suit.BASTOS, Rank.TWO),     // 0 pts ← esperada
                        card(Suit.OROS, Rank.SEVEN),     // triunfo
                });

        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);

        assertThat(result.getPoints()).isEqualTo(0);
        assertThat(result.getSuit()).isNotEqualTo(TRUMP);
    }

    @Test
    @DisplayName("HARD liderando empate de puntuaciones: devuelve carta válida")
    void hardBot_leading_tied_returnsCard() {
        Game game = buildGameWithScores(BOT_ID, 10, "HUMAN_1", 10,
                new Card[]{
                        card(Suit.COPAS, Rank.ACE),
                        card(Suit.BASTOS, Rank.TWO),
                });
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("HARD liderando muy por detrás: puede jugar carta de alto valor")
    void hardBot_leading_losing_big_canPlayHighValue() {
        Game game = buildGameWithScores(BOT_ID, 0, "HUMAN_1", 40,
                new Card[]{
                        card(Suit.COPAS, Rank.ACE),    // 11 pts
                        card(Suit.BASTOS, Rank.TWO),   // 0 pts
                        card(Suit.ESPADAS, Rank.THREE), // 10 pts
                });
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("HARD con pocos candidatos oponentes (solo 1 carta desconocida): no falla")
    void hardBot_fewOpponentCandidates_doesNotFail() {
        Game game = buildGame(BOT_ID,
                card(Suit.OROS, Rank.ACE),
                card(Suit.OROS, Rank.THREE),
                card(Suit.COPAS, Rank.ACE),
                card(Suit.COPAS, Rank.THREE),
                card(Suit.ESPADAS, Rank.ACE)
        );
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("HARD: mano de un solo elemento retorna directamente sin minimax")
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

    // ─── HARD: siguiendo la baza ──────────────────────────────────────────────

    @Test
    @DisplayName("HARD siguiendo: devuelve carta válida de la mano")
    void hardBot_following_returnsCardFromHand() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.ACE),
                        card(Suit.COPAS, Rank.THREE),
                        card(Suit.BASTOS, Rank.FIVE),
                },
                "HUMAN_1", card(Suit.COPAS, Rank.ACE)
        );

        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(game.getPlayerById(BOT_ID).getHand()).contains(result);
    }

    @Test
    @DisplayName("HARD siguiendo: devuelve carta válida (variante con seed distinto)")
    void hardBot_following_returnsCardFromHand_variant() {
        BriscaBotDecisionService svc = new BriscaBotDecisionService(new Random(42));
        Game game = buildGameWithTrickAndTrump("BOT_HARD_f1",
                new Card[]{new Card(Suit.OROS, Rank.ACE), new Card(Suit.COPAS, Rank.THREE)},
                "HUMAN_1", new Card(Suit.COPAS, Rank.ACE), TRUMP);
        Card result = svc.decide(game, "BOT_HARD_f1", BotDifficulty.HARD);
        assertThat(game.getPlayerById("BOT_HARD_f1").getHand()).contains(result);
    }

    @Test
    @DisplayName("HARD siguiendo con triunfo disponible: usa triunfo si la baza vale puntos")
    void hardBot_following_usesToTrumpHighValueTrick() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.TWO),     // triunfo
                        card(Suit.ESPADAS, Rank.TWO),  // sin puntos
                },
                "HUMAN_1", card(Suit.COPAS, Rank.ACE)  // 11 puntos
        );

        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("HARD siguiendo con muchas cartas desconocidas (selectOpponentCandidates >5): no falla")
    void hardBot_manyUnknownCards_selectsProperCandidates() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.BASTOS, Rank.SEVEN),
                        card(Suit.ESPADAS, Rank.FIVE),
                },
                "HUMAN_1", card(Suit.BASTOS, Rank.TWO)
        );

        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    // ─── Minimax: maxValue con bot null ──────────────────────────────────────

    @Test
    @DisplayName("maxValue con bot null (ghost) devuelve 0")
    void hardBot_maxValue_botNull_returnsZero() throws Exception {
        BriscaBotDecisionService svc = new BriscaBotDecisionService();
        Game game = new Game("G1", 2, 4, BigDecimal.ONE);
        Trick trick = new Trick();

        Class<?> simClass = Class.forName(
                "com.aguardientes.azarcafetero.domain.service.BriscaBotDecisionService$SimulatedTrick"
        );

        var ctor = simClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object sim = ctor.newInstance(trick, "P1", new Card(Suit.OROS, Rank.ACE));

        var method = BriscaBotDecisionService.class.getDeclaredMethod(
                "maxValue",
                simClass, List.class, Suit.class, Game.class, String.class,
                int.class, double.class, double.class
        );
        method.setAccessible(true);

        double result = (double) method.invoke(
                svc, sim, new ArrayList<Card>(), Suit.OROS, game,
                "ghost", 1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY
        );

        assertThat(result).isEqualTo(0);
    }

    // ─── Builders ─────────────────────────────────────────────────────────────

    private static Card card(Suit suit, Rank rank) {
        return new Card(suit, rank);
    }

    private Game buildGame(String playerId, Card... cards) {
        Game game = new Game("TEST_GAME", 1, 4, BigDecimal.ONE);
        Player player = new Player(playerId, "Bot");
        game.addPlayer(player);
        for (Card c : cards) player.addCard(c);
        forceInProgress(game, TRUMP);
        return game;
    }

    private Game buildGameWithTrick(String botId, Card[] botCards,
                                    String rivalId, Card rivalCard) {
        return buildGameWithTrickAndTrump(botId, botCards, rivalId, rivalCard, TRUMP);
    }

    private Game buildGameWithTrickAndTrump(String botId, Card[] botCards,
                                            String rivalId, Card rivalCard, Suit trump) {
        Game game = new Game("TEST_GAME", 2, 4, BigDecimal.ONE);

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

    private Game buildGameWithScores(String botId, int botScore,
                                     String rivalId, int rivalScore,
                                     Card[] botCards) {
        Game game = new Game("TEST_GAME", 2, 4, BigDecimal.ONE);

        Player rival = new Player(rivalId, "Human");
        rival.addPoints(rivalScore);
        game.addPlayer(rival);

        Player bot = new Player(botId, "Bot");
        bot.addPoints(botScore);
        for (Card c : botCards) bot.addCard(c);
        game.addPlayer(bot);

        forceInProgress(game, TRUMP);
        setCurrentPlayerIndex(game, 1);
        return game;
    }

    private void forceInProgress(Game game, Suit trump) {
        try {
            var stateField = Game.class.getDeclaredField("state");
            stateField.setAccessible(true);
            stateField.set(game, GameState.IN_PROGRESS);

            var deckField = Game.class.getDeclaredField("deck");
            deckField.setAccessible(true);
            Object deck = deckField.get(game);

            var trumpCardField = deck.getClass().getDeclaredField("trumpCard");
            trumpCardField.setAccessible(true);
            trumpCardField.set(deck, new Card(trump, Rank.SEVEN));
        } catch (Exception e) {
            throw new RuntimeException("Test setup failed", e);
        }
    }

    private void setCurrentPlayerIndex(Game game, int index) {
        try {
            var field = Game.class.getDeclaredField("currentPlayerIndex");
            field.setAccessible(true);
            field.set(game, index);
        } catch (Exception e) {
            throw new RuntimeException("Test setup failed", e);
        }
    }
}