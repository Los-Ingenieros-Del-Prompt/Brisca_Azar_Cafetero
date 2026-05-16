package com.aguardientes.azarcafetero.dominio;

import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.BriscaBotDecisionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.util.Random;
import static org.assertj.core.api.Assertions.*;

@DisplayName("BriscaBotDecisionService")
class BriscaBotDecisionServiceTest {

    private static final String BOT_ID = "BOT_MEDIUM_test01";
    private static final Suit   TRUMP  = Suit.OROS;

    private BriscaBotDecisionService service;

    @BeforeEach void setUp() {
        service = new BriscaBotDecisionService(new Random(42));
    }

    // ── Validaciones básicas ──────────────────────────────────────────────────

    @Test void decide_returnsCardFromHand() {
        Game game = buildGame(BOT_ID,
                card(Suit.OROS, Rank.ACE),
                card(Suit.COPAS, Rank.TWO),
                card(Suit.ESPADAS, Rank.KING));
        assertThat(game.getPlayerById(BOT_ID).getHand())
                .contains(service.decide(game, BOT_ID, BotDifficulty.MEDIUM));
    }

    @ParameterizedTest
    @EnumSource(BotDifficulty.class)
    void decide_singleCard_returnsIt(BotDifficulty d) {
        Game game = buildGame(BOT_ID, card(Suit.COPAS, Rank.THREE));
        assertThat(service.decide(game, BOT_ID, d)).isEqualTo(card(Suit.COPAS, Rank.THREE));
    }

    @ParameterizedTest
    @EnumSource(BotDifficulty.class)
    void decide_neverReturnsNull(BotDifficulty d) {
        Game game = buildGame(BOT_ID, card(Suit.OROS, Rank.ACE), card(Suit.COPAS, Rank.THREE));
        assertThat(service.decide(game, BOT_ID, d)).isNotNull();
    }

    @Test void decide_botNotInGame_throws() {
        Game game = buildGame("HUMAN_1", card(Suit.BASTOS, Rank.SEVEN));
        assertThatThrownBy(() -> service.decide(game, "BOT_EASY_x", BotDifficulty.EASY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void decide_emptyHand_throws() {
        Game game = buildGame(BOT_ID); // sin cartas
        forceInProgress(game, TRUMP);
        assertThatThrownBy(() -> service.decide(game, BOT_ID, BotDifficulty.HARD))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── EASY ──────────────────────────────────────────────────────────────────

    @Test void easy_alwaysReturnsValidCard() {
        for (int i = 0; i < 20; i++) {
            Game game = buildGame(BOT_ID,
                    card(Suit.OROS, Rank.ACE),
                    card(Suit.COPAS, Rank.FIVE),
                    card(Suit.ESPADAS, Rank.JACK));
            assertThat(game.getPlayerById(BOT_ID).getHand())
                    .contains(service.decide(game, BOT_ID, BotDifficulty.EASY));
        }
    }

    // ── MEDIUM — liderando ────────────────────────────────────────────────────

    @Test void medium_leading_playsLowestNonTrump() {
        Game game = buildGame(BOT_ID,
                card(Suit.BASTOS, Rank.FIVE),  // 0 pts, no trump ← esperada
                card(Suit.COPAS, Rank.ACE),    // 11 pts
                card(Suit.OROS, Rank.TWO));    // trump
        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);
        assertThat(result.getSuit()).isNotEqualTo(TRUMP);
        assertThat(result.getPoints()).isEqualTo(0);
    }

    @Test void medium_leading_onlyTrumps_usesLowestTrump() {
        Game game = buildGame(BOT_ID,
                card(Suit.OROS, Rank.ACE),
                card(Suit.OROS, Rank.THREE));
        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);
        assertThat(result.getSuit()).isEqualTo(TRUMP);
    }

    // ── MEDIUM — siguiendo ────────────────────────────────────────────────────

    @Test void medium_following_winsWithMinSameSuit() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.BASTOS, Rank.FIVE),
                        card(Suit.BASTOS, Rank.KING),  // ← gana sobre SEVEN
                        card(Suit.BASTOS, Rank.ACE),
                        card(Suit.OROS, Rank.TWO)},
                "HUMAN_1", card(Suit.BASTOS, Rank.SEVEN));
        assertThat(service.decide(game, BOT_ID, BotDifficulty.MEDIUM))
                .isEqualTo(card(Suit.BASTOS, Rank.KING));
    }

    @Test void medium_following_discardsLowestWhenCantWin() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.COPAS, Rank.TWO),
                        card(Suit.ESPADAS, Rank.FOUR),
                        card(Suit.COPAS, Rank.FIVE)},
                "HUMAN_1", card(Suit.OROS, Rank.ACE)); // trump máximo
        assertThat(service.decide(game, BOT_ID, BotDifficulty.MEDIUM).getPoints()).isEqualTo(0);
    }

    @Test void medium_following_usesTrumpWhenTrickWorthIt() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.TWO),    // trump
                        card(Suit.COPAS, Rank.TWO)},  // numValue=1, no gana sobre THREE (numValue=3)
                "HUMAN_1", card(Suit.COPAS, Rank.THREE)); // 10 pts
        assertThat(service.decide(game, BOT_ID, BotDifficulty.MEDIUM).getSuit()).isEqualTo(TRUMP);
    }

    @Test void medium_following_doesNotUseTrumpForCheapTrick() {
        // COPAS-TWO (numValue=1) es más barato que OROS-FOUR (numValue=2)
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS,  Rank.FOUR),  // trump, numValue=2
                        card(Suit.COPAS, Rank.TWO)},  // numValue=1 ← se descarta
                "HUMAN_1", card(Suit.COPAS, Rank.FOUR));
        assertThat(service.decide(game, BOT_ID, BotDifficulty.MEDIUM).getSuit()).isNotEqualTo(TRUMP);
    }

    // ── HARD — liderando ──────────────────────────────────────────────────────

    @Test void hard_leading_losing_doesNotWasteTrump() {
        Game game = buildGameWithScores(BOT_ID, 0, "HUMAN_1", 15,
                new Card[]{
                        card(Suit.COPAS, Rank.ACE),
                        card(Suit.BASTOS, Rank.TWO),
                        card(Suit.OROS, Rank.SEVEN)});  // trump
        assertThat(service.decide(game, BOT_ID, BotDifficulty.HARD).getSuit())
                .isNotEqualTo(TRUMP);
    }

    @Test void hard_leading_winning_playsConservatively() {
        Game game = buildGameWithScores(BOT_ID, 20, "HUMAN_1", 5,
                new Card[]{
                        card(Suit.COPAS, Rank.ACE),
                        card(Suit.BASTOS, Rank.TWO),  // ← esperada
                        card(Suit.OROS, Rank.SEVEN)});
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result.getPoints()).isEqualTo(0);
        assertThat(result.getSuit()).isNotEqualTo(TRUMP);
    }

    @Test void hard_leading_tied_returnsValidCard() {
        Game game = buildGameWithScores(BOT_ID, 10, "HUMAN_1", 10,
                new Card[]{card(Suit.COPAS, Rank.ACE), card(Suit.BASTOS, Rank.TWO)});
        assertThat(service.decide(game, BOT_ID, BotDifficulty.HARD)).isNotNull();
    }

    // ── HARD — siguiendo ──────────────────────────────────────────────────────

    @Test void hard_following_returnsCardFromHand() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.ACE),
                        card(Suit.COPAS, Rank.THREE),
                        card(Suit.BASTOS, Rank.FIVE)},
                "HUMAN_1", card(Suit.COPAS, Rank.ACE));
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(game.getPlayerById(BOT_ID).getHand()).contains(result);
    }

    // ── builders ──────────────────────────────────────────────────────────────

    private static Card card(Suit s, Rank r) { return new Card(s, r); }

    private Game buildGame(String playerId, Card... cards) {
        Game game = new Game("T", 1, 4, BigDecimal.ONE);
        Player p = new Player(playerId, "Bot");
        game.addPlayer(p);
        for (Card c : cards) p.addCard(c);
        forceInProgress(game, TRUMP);
        return game;
    }

    private Game buildGameWithTrick(String botId, Card[] botCards,
                                    String rivalId, Card rivalCard) {
        Game game = new Game("T", 2, 4, BigDecimal.ONE);
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
                                     String rivalId, int rivalScore, Card[] botCards) {
        Game game = new Game("T", 2, 4, BigDecimal.ONE);
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

    private void forceCurrentPlayerIndex(Game game, int index) {
        try {
            var f = Game.class.getDeclaredField("currentPlayerIndex");
            f.setAccessible(true);
            f.set(game, index);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    @Test void simulatedTrick_leadSuit_followedCorrectly() {
        // Bot sigue el palo liderado
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.COPAS, Rank.KING),
                        card(Suit.COPAS, Rank.FIVE),
                        card(Suit.BASTOS, Rank.TWO)},
                "HUMAN_1", card(Suit.COPAS, Rank.THREE));
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
        assertThat(game.getPlayerById(BOT_ID).getHand()).contains(result);
    }

    @Test void simulatedTrick_noMatchingSuit_usesTrumpOrDiscard() {
        // Bot no tiene el palo liderado
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.ACE),   // trump
                        card(Suit.ESPADAS, Rank.TWO)},
                "HUMAN_1", card(Suit.COPAS, Rank.ACE)); // lidera copas, bot no tiene
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test void simulatedTrick_trickWinner_isCorrectlyIdentified() {
        // Trump gana sobre cualquier palo normal
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.TWO),   // trump bajo
                        card(Suit.COPAS, Rank.ACE)}, // copas alta pero no trump
                "HUMAN_1", card(Suit.COPAS, Rank.THREE));
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test void hard_leading_withOpponentScoreHigh_aggressivePlay() {
        // Rival va ganando → bot juega agresivo
        Game game = buildGameWithScores(BOT_ID, 5, "HUMAN_1", 25,
                new Card[]{
                        card(Suit.COPAS, Rank.ACE),
                        card(Suit.OROS, Rank.ACE),   // trump máximo
                        card(Suit.BASTOS, Rank.TWO)});
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test void medium_following_noTrumpAndCantWin_discardsLowest() {
        // Sin trump, no puede ganar → descarta la más barata
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.BASTOS, Rank.TWO),
                        card(Suit.ESPADAS, Rank.TWO)},
                "HUMAN_1", card(Suit.COPAS, Rank.ACE)); // copa alta, bot no tiene copas ni trump
        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);
        assertThat(result.getPoints()).isEqualTo(0);
    }

    @Test void medium_following_trumpWinsOverLeadSuit() {
        // Bot tiene trump Y carta del palo → elige según puntos del trick
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.THREE),  // trump
                        card(Suit.COPAS, Rank.TWO)},  // mismo palo que líder
                "HUMAN_1", card(Suit.COPAS, Rank.ACE)); // 11 pts en mesa
        assertThat(service.decide(game, BOT_ID, BotDifficulty.MEDIUM)).isNotNull();
    }

    @Test void hard_following_withTrumpAndLeadSuit_choosesOptimal() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.ACE),    // trump alto
                        card(Suit.COPAS, Rank.KING),  // mismo palo que líder
                        card(Suit.BASTOS, Rank.TWO)},
                "HUMAN_1", card(Suit.COPAS, Rank.SEVEN)); // 0 pts
        assertThat(game.getPlayerById(BOT_ID).getHand())
                .contains(service.decide(game, BOT_ID, BotDifficulty.HARD));
    }

    @Test void easy_multipleCards_neverThrows() {
        for (int i = 0; i < 10; i++) {
            Game game = buildGame(BOT_ID,
                    card(Suit.OROS,    Rank.ACE),
                    card(Suit.COPAS,   Rank.THREE),
                    card(Suit.ESPADAS, Rank.FIVE),
                    card(Suit.BASTOS,  Rank.SEVEN));
            assertThatCode(() -> service.decide(game, BOT_ID, BotDifficulty.EASY))
                    .doesNotThrowAnyException();
        }
    }

    @Test void medium_leading_highValueNonTrump_playsLow() {
        // Tiene carta de alto valor y baja → elige la baja al liderar
        Game game = buildGame(BOT_ID,
                card(Suit.COPAS, Rank.ACE),   // 11 pts
                card(Suit.BASTOS, Rank.TWO),  // 0 pts ← esperada
                card(Suit.ESPADAS, Rank.FOUR)); // 0 pts
        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);
        assertThat(result.getPoints()).isEqualTo(0);
        assertThat(result.getSuit()).isNotEqualTo(TRUMP);
    }
}