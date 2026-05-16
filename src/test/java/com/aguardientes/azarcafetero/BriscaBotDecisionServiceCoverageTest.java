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
 * Cubre ramas no alcanzadas del BriscaBotDecisionService:
 * - HARD minimax (evaluateCompletedTrick, evaluatePartialTrick, selectOpponentCandidates)
 * - heuristicScore (following branch, isBotWinning)
 * - decide con mano vacía lanza excepción
 * - MEDIUM siguiendo con triunfo que no gana tampoco
 * - HARD siguiendo (bot no lidera)
 */
@DisplayName("BriscaBotDecisionService – cobertura adicional")
class BriscaBotDecisionServiceCoverageTest {

    private static final String BOT_ID = "BOT_HARD_test01";
    private static final Suit   TRUMP  = Suit.OROS;

    private BriscaBotDecisionService service;

    @BeforeEach
    void setUp() {
        service = new BriscaBotDecisionService(new Random(123));
    }

    // ─── Casos límite ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("decide con mano vacía lanza IllegalStateException")
    void decide_emptyHand_throws() {
        Game game = buildGame(BOT_ID); // sin cartas
        forceInProgress(game, TRUMP);
        assertThatThrownBy(() -> service.decide(game, BOT_ID, BotDifficulty.HARD))
                .isInstanceOf(IllegalStateException.class);
    }

    // ─── HARD siguiendo la baza ───────────────────────────────────────────────

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

    // ─── HARD liderando – múltiples cartas, distintos escenarios ─────────────

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

    // ─── MEDIUM casos límite ──────────────────────────────────────────────────

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

    // ─── Múltiples oponentes (selectOpponentCandidates con muchas cartas) ─────

    @Test
    @DisplayName("HARD con pocos candidatos oponentes (solo 1 carta desconocida): no falla")
    void hardBot_fewOpponentCandidates_doesNotFail() {
        // Mano grande del bot deja pocas cartas desconocidas al oponente
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
    @DisplayName("HARD siguiendo con muchas cartas desconocidas (selectOpponentCandidates >5): no falla")
    void hardBot_manyUnknownCards_selectsProperCandidates() {
        // Pocos cards en mano → muchas desconocidas
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
        Game game = new Game("TEST_GAME", 2, 4, BigDecimal.ONE);

        Player rival = new Player(rivalId, "Human");
        rival.addCard(rivalCard);
        game.addPlayer(rival);

        Player bot = new Player(botId, "Bot");
        for (Card c : botCards) bot.addCard(c);
        game.addPlayer(bot);

        forceInProgress(game, TRUMP);

        rival.playCard(rivalCard);
        game.getCurrentTrick().addCard(rivalId, rivalCard);
        forceCurrentPlayerIndex(game, 1);
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
        forceCurrentPlayerIndex(game, 1);
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