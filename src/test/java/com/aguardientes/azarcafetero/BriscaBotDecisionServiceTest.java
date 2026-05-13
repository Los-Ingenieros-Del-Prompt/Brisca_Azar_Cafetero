package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.BriscaBotDecisionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios del algoritmo de decisión del bot de Brisca.
 *
 * No necesita Spring — solo dominio puro.
 */
@DisplayName("BriscaBotDecisionService")
class BriscaBotDecisionServiceTest {

    private static final String BOT_ID = "BOT_MEDIUM_test01";
    private static final Suit   TRUMP  = Suit.OROS;

    private BriscaBotDecisionService service;

    @BeforeEach
    void setUp() {
        service = new BriscaBotDecisionService(new Random(42)); // seed fijo para reproducibilidad
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

    // ─── MEDIUM: lidera la baza ───────────────────────────────────────────────

    @Test
    @DisplayName("MEDIUM liderando: juega la carta más barata (sin puntos, sin triunfo)")
    void mediumBot_leading_playsLowestNonTrumpZeroPoints() {
        Game game = buildGame(BOT_ID,
                card(Suit.BASTOS, Rank.FIVE),    // 0 pts, no triunfo  ← esperada
                card(Suit.COPAS, Rank.ACE),      // 11 pts, no triunfo
                card(Suit.OROS, Rank.TWO));      // 0 pts, TRIUNFO

        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);

        // Debe elegir BASTOS-FIVE (sin puntos, no triunfo) sobre OROS-TWO (triunfo)
        assertThat(result.getSuit()).isNotEqualTo(TRUMP);
        assertThat(result.getPoints()).isEqualTo(0);
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
                "HUMAN_1", card(Suit.BASTOS, Rank.SEVEN)  // rival jugó SEVEN de BASTOS
        );

        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);

        // KING (valor 12) gana sobre SEVEN (valor 7); ACE (valor 1 numérico) NO gana sobre 7
        // Espera KING que es el mínimo que supera al SEVEN en valor numérico
        assertThat(result).isEqualTo(card(Suit.BASTOS, Rank.KING));
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

        // No puede ganar (no tiene triunfos ni supera el ACE de OROS)
        // Debe descartar una carta de 0 puntos
        assertThat(result.getPoints()).isEqualTo(0);
    }

    @Test
    @DisplayName("MEDIUM siguiendo: usa triunfo si la baza tiene >= 5 puntos y no puede ganar con mismo palo")
    void mediumBot_following_usesTrumpWhenTrickIsWorthIt() {
        // Rival jugó COPAS-THREE (10 pts, numericValue=3).
        // Bot tiene COPAS-TWO (numericValue=2) — NO supera al THREE (2 < 3).
        // Como no puede ganar mismo palo y la baza vale 10 pts (>= 5), usa el triunfo.
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.TWO),       // triunfo (0 pts)
                        card(Suit.COPAS, Rank.TWO),      // 0 pts, numericValue=2, NO gana sobre THREE
                },
                "HUMAN_1", card(Suit.COPAS, Rank.THREE) // rival jugó 10 puntos, numericValue=3
        );

        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);

        // Bot no puede ganar con COPAS-TWO (2 < 3).
        // Baza vale 10 pts (>= 5): debe usar el triunfo OROS-TWO.
        assertThat(result.getSuit()).isEqualTo(TRUMP);
    }

    @Test
    @DisplayName("MEDIUM siguiendo: NO usa triunfo si la baza tiene < 5 puntos")
    void mediumBot_following_doesNotUseTrumpForCheapTrick() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.TWO),       // triunfo (0 pts)
                        card(Suit.COPAS, Rank.FOUR),     // 0 pts
                },
                "HUMAN_1", card(Suit.COPAS, Rank.FOUR)  // rival jugó 0 puntos
        );

        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);

        // Baza tiene 0 pts (< 5): no debería desperdiciar el triunfo
        assertThat(result.getSuit()).isNotEqualTo(TRUMP);
    }

    // ─── HARD: liderando ──────────────────────────────────────────────────────

    @Test
    @DisplayName("HARD liderando y perdiendo: ataca con carta de alto valor (no triunfo)")
    void hardBot_leading_losing_playsHighValueNonTrump() {
        // Bot tiene 0 puntos, rival tiene 15 → está perdiendo
        Game game = buildGameWithScores(BOT_ID, 0, "HUMAN_1", 15,
                new Card[]{
                        card(Suit.COPAS, Rank.ACE),      // 11 pts, no triunfo ← esperada
                        card(Suit.BASTOS, Rank.TWO),     // 0 pts
                        card(Suit.OROS, Rank.SEVEN),     // 0 pts, triunfo
                });

        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);

        assertThat(result).isEqualTo(card(Suit.COPAS, Rank.ACE));
    }

    @Test
    @DisplayName("HARD liderando y ganando: juega conservador (carta barata sin triunfo)")
    void hardBot_leading_winning_playsConservatively() {
        // Bot tiene 20 puntos, rival tiene 5 → está ganando
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

    // ─── La carta devuelta nunca es nula ─────────────────────────────────────

    @ParameterizedTest
    @EnumSource(BotDifficulty.class)
    @DisplayName("Nunca devuelve null en ningún nivel de dificultad")
    void decide_neverReturnsNull(BotDifficulty difficulty) {
        Game game = buildGame(BOT_ID,
                card(Suit.OROS, Rank.ACE),
                card(Suit.COPAS, Rank.THREE));

        assertThat(service.decide(game, BOT_ID, difficulty)).isNotNull();
    }

    // ─── Builders de test ────────────────────────────────────────────────────

    private static Card card(Suit suit, Rank rank) {
        return new Card(suit, rank);
    }

    /**
     * Crea una partida mínima con el bot como jugador activo (lidera la baza).
     */
    private Game buildGame(String playerId, Card... cards) {
        Game game = new Game("TEST_GAME", 1, 4, BigDecimal.ONE);
        Player player = new Player(playerId, "Bot");
        game.addPlayer(player);
        // Inyecta cartas directamente en la mano
        for (Card c : cards) player.addCard(c);
        // Simula estado IN_PROGRESS sin repartir (para no alterar las cartas)
        forceInProgress(game, TRUMP);
        return game;
    }

    /**
     * Crea una partida donde el rival ya jugó una carta (bot sigue).
     */
    private Game buildGameWithTrick(String botId, Card[] botCards,
                                    String rivalId, Card rivalCard) {
        Game game = new Game("TEST_GAME", 2, 4, BigDecimal.ONE);

        Player rival = new Player(rivalId, "Human");
        rival.addCard(rivalCard);
        game.addPlayer(rival);

        Player bot = new Player(botId, "Bot");
        for (Card c : botCards) bot.addCard(c);
        game.addPlayer(bot);

        forceInProgress(game, TRUMP);

        // El rival lidera: juega su carta (avanza el índice al bot)
        rival.playCard(rivalCard);
        game.getCurrentTrick().addCard(rivalId, rivalCard);

        // Forzamos que el turno sea del bot
        forceCurrentPlayerIndex(game, 1);

        return game;
    }

    /**
     * Crea una partida con puntuaciones definidas para probar estrategia ganando/perdiendo.
     */
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
        forceCurrentPlayerIndex(game, 1); // bot lidera
        return game;
    }

    // ─── Helpers de reflexión para forzar estado interno ─────────────────────

    private void forceInProgress(Game game, Suit trump) {
        try {
            var stateField = Game.class.getDeclaredField("state");
            stateField.setAccessible(true);
            stateField.set(game, GameState.IN_PROGRESS);

            // Forzar trump suit a través del deck
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

    private void forceCurrentPlayerIndex(Game game, int index) {
        try {
            var field = Game.class.getDeclaredField("currentPlayerIndex");
            field.setAccessible(true);
            field.set(game, index);
        } catch (Exception e) {
            throw new RuntimeException("Test setup failed", e);
        }
    }
}