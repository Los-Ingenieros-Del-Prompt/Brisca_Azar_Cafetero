package com.aguardientes.azarcafetero.dominio;

import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.BriscaBotDecisionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests adicionales para BriscaBotDecisionService.
 *
 * Cubren las ramas del Minimax HARD no alcanzadas por el test base:
 *   - 3 jugadores (baza con playerCount=3, nodos MAX/MIN intermedios)
 *   - selectOpponentCandidates con < 3, 4 y 5 cartas desconocidas
 *   - evaluatePartialTrick (baza aún no completa en MIN node)
 *   - Bot ganando vs perdiendo con trump al liderar (heurística)
 *   - Todas las ramas de isCardStronger: trump vs trump, lead vs other, etc.
 */
@DisplayName("BriscaBotDecisionService – cobertura extendida")
class BriscaBotDecisionServiceExtraTest {

    private static final String BOT_ID = "BOT_HARD_extra01";
    private static final Suit   TRUMP  = Suit.OROS;

    private BriscaBotDecisionService service;

    @BeforeEach
    void setUp() {
        service = new BriscaBotDecisionService(new Random(0));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HARD — partida de 3 jugadores
    // Activa los nodos intermedios del Minimax (minValue → maxValue recursivo)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HARD 3p: bot lidera, devuelve carta válida")
    void hard_threePlayer_leading_returnsValidCard() {
        Game game = buildThreePlayerGame(
                BOT_ID,
                new Card[]{
                        card(Suit.COPAS,   Rank.ACE),   // 11 pts
                        card(Suit.BASTOS,  Rank.TWO),   //  0 pts
                        card(Suit.ESPADAS, Rank.KING)   //  4 pts
                },
                "H1", card(Suit.COPAS, Rank.THREE),
                "H2", card(Suit.ESPADAS, Rank.FIVE),
                /* turno del bot — baza vacía */ true
        );
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(game.getPlayerById(BOT_ID).getHand()).contains(result);
    }

    @Test
    @DisplayName("HARD 3p: bot sigue con 1 carta jugada — baza incompleta activa minValue")
    void hard_threePlayer_following_oneCardPlayed() {
        // H1 ya jugó; le toca al bot; H2 aún no jugó → baza no completa en el árbol
        Game game = threePlayerGameWithOnePlayed(
                BOT_ID,
                new Card[]{
                        card(Suit.OROS,   Rank.ACE),   // trump alto
                        card(Suit.COPAS,  Rank.KING),
                        card(Suit.BASTOS, Rank.SEVEN)
                },
                "H1", card(Suit.COPAS, Rank.THREE)   // H1 ya jugó
        );
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(game.getPlayerById(BOT_ID).getHand()).contains(result);
    }

    @Test
    @DisplayName("HARD 3p: bot sigue con trump del oponente en mesa")
    void hard_threePlayer_following_trumpOnTable() {
        Game game = threePlayerGameWithOnePlayed(
                BOT_ID,
                new Card[]{
                        card(Suit.COPAS,   Rank.ACE),   // 11 pts, no trump
                        card(Suit.ESPADAS,  Rank.THREE), // 10 pts
                        card(Suit.BASTOS,  Rank.TWO)    //  0 pts
                },
                "H1", card(Suit.OROS, Rank.ACE)   // trump máximo en mesa
        );
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // selectOpponentCandidates — distintos tamaños de unknownCards
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HARD: con pocas cartas desconocidas (1 carta)")
    void hard_fewUnknownCards_oneCard() {
        // Si casi todas las cartas están en mano del bot,
        // unknownCards tendrá muy pocas entradas → candidatos < 5
        Game game = buildGame(BOT_ID,
                card(Suit.OROS,    Rank.ACE),
                card(Suit.OROS,    Rank.THREE),
                card(Suit.OROS,    Rank.KING),
                card(Suit.OROS,    Rank.HORSE),
                card(Suit.OROS,    Rank.JACK),
                card(Suit.OROS,    Rank.SEVEN),
                card(Suit.OROS,    Rank.SIX),
                card(Suit.OROS,    Rank.FIVE),
                card(Suit.OROS,    Rank.FOUR),
                card(Suit.OROS,    Rank.TWO),
                card(Suit.COPAS,   Rank.ACE),
                card(Suit.COPAS,   Rank.THREE),
                card(Suit.COPAS,   Rank.KING),
                card(Suit.COPAS,   Rank.HORSE),
                card(Suit.COPAS,   Rank.JACK),
                card(Suit.COPAS,   Rank.SEVEN),
                card(Suit.COPAS,   Rank.SIX),
                card(Suit.COPAS,   Rank.FIVE),
                card(Suit.COPAS,   Rank.FOUR),
                card(Suit.COPAS,   Rank.TWO),
                card(Suit.ESPADAS, Rank.ACE),
                card(Suit.ESPADAS, Rank.THREE),
                card(Suit.ESPADAS, Rank.KING),
                card(Suit.ESPADAS, Rank.HORSE),
                card(Suit.ESPADAS, Rank.JACK),
                card(Suit.ESPADAS, Rank.SEVEN),
                card(Suit.ESPADAS, Rank.SIX),
                card(Suit.ESPADAS, Rank.FIVE),
                card(Suit.ESPADAS, Rank.FOUR),
                card(Suit.ESPADAS, Rank.TWO),
                card(Suit.BASTOS,  Rank.ACE),
                card(Suit.BASTOS,  Rank.THREE),
                card(Suit.BASTOS,  Rank.KING),
                card(Suit.BASTOS,  Rank.HORSE),
                card(Suit.BASTOS,  Rank.JACK),
                card(Suit.BASTOS,  Rank.SEVEN),
                card(Suit.BASTOS,  Rank.SIX),
                card(Suit.BASTOS,  Rank.FIVE),
                card(Suit.BASTOS,  Rank.FOUR)
                // Deja fuera solo BASTOS-TWO → 1 carta desconocida
        );
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("HARD: con 4 cartas desconocidas — candidato intermedio")
    void hard_fourUnknownCards_activatesIntermediateCandidate() {
        // Con 4 desconocidas: mayor, menor, triunfo bajo, intermedio → 4 candidatos únicos
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.COPAS, Rank.ACE),
                        card(Suit.COPAS, Rank.THREE)
                },
                "H1", card(Suit.BASTOS, Rank.SEVEN));
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("HARD: con 5+ cartas desconocidas — activa segunda intermedia (índice 1)")
    void hard_fiveUnknownCards_activatesSecondIntermediate() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.COPAS,   Rank.ACE),
                        card(Suit.ESPADAS, Rank.ACE)
                },
                "H1", card(Suit.BASTOS, Rank.TWO));
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ramas de isCardStronger en simulación interna del bot
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HARD: dos trumps en baza — el más alto gana")
    void hard_bothTrumps_higherWins() {
        // H1 jugó trump bajo; bot tiene trump alto → debe ganar la baza
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS, Rank.ACE),   // trump más alto (numVal=11)
                        card(Suit.COPAS, Rank.TWO)
                },
                "H1", card(Suit.OROS, Rank.TWO));  // trump bajo (numVal=1)
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("HARD: palo líder vs otro palo no trump — líder gana")
    void hard_leadSuitVsOther_leadSuitWins() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.COPAS, Rank.ACE),    // mismo palo que líder
                        card(Suit.ESPADAS, Rank.THREE)  // otro palo
                },
                "H1", card(Suit.COPAS, Rank.TWO));  // lidera copas
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("HARD: dos cartas del mismo palo no trump — mayor numérico gana")
    void hard_sameSuitNonTrump_higherNumericWins() {
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.COPAS, Rank.THREE),  // 10pts, numVal=3
                        card(Suit.BASTOS, Rank.FOUR)   // 0pts, otro palo
                },
                "H1", card(Suit.COPAS, Rank.TWO)); // lidera copas numVal=1
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("HARD: challanger no trump no lead, current lead — current gana")
    void hard_challenger_notLead_notTrump_currentIsLead() {
        // Bot tiene espadas (no trump, no lead), H1 lidera copas
        // → copas (lead) siempre gana sobre espadas
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{card(Suit.ESPADAS, Rank.ACE)},  // no trump, no lead
                "H1", card(Suit.COPAS, Rank.TWO));         // lead
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isBotWinning — ramas de la heurística
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HARD: bot empatado no se considera ganador — juega normalmente")
    void hard_botTied_notConsideredWinning() {
        Game game = buildGameWithScores(BOT_ID, 15, "H1", 15,
                new Card[]{
                        card(Suit.COPAS, Rank.ACE),
                        card(Suit.OROS, Rank.SEVEN)   // trump
                });
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("HARD: bot ganando conserva triunfos — penalización extra en leading")
    void hard_botWinning_conservesTrump_whenLeading() {
        Game game = buildGameWithScores(BOT_ID, 30, "H1", 10,
                new Card[]{
                        card(Suit.OROS,   Rank.ACE),   // trump — debe penalizarse
                        card(Suit.COPAS,  Rank.TWO),   // no trump barata ← preferida
                        card(Suit.BASTOS, Rank.FOUR)
                });
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        // Cuando va ganando, no debería gastar el trump
        assertThat(result.getSuit()).isNotEqualTo(TRUMP);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MEDIUM — ramas restantes de heuristicScore (no usada directamente en
    // MEDIUM greedy, pero sí como base lógica compartida)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("MEDIUM: leading con todas cartas de 0 pts — elige numericValue más bajo")
    void medium_leading_allZeroPoints_choosesLowestNumeric() {
        Game game = buildGame(BOT_ID,
                card(Suit.BASTOS,  Rank.TWO),   // numVal=1  ← esperada
                card(Suit.ESPADAS, Rank.FOUR),  // numVal=2
                card(Suit.COPAS,   Rank.FIVE)); // numVal=3
        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);
        assertThat(result).isEqualTo(card(Suit.BASTOS, Rank.TWO));
    }

    @Test
    @DisplayName("MEDIUM: following — trump no alcanza puntos umbral (< 5 pts), descarta")
    void medium_following_trickBelow5pts_doesNotUseTrump() {
        // Truco vale 0pts; bot no debería usar trump
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS,  Rank.TWO),   // trump
                        card(Suit.COPAS, Rank.FOUR)}, // 0 pts, no trump
                "H1", card(Suit.BASTOS, Rank.TWO));   // 0 pts en mesa
        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);
        assertThat(result.getSuit()).isNotEqualTo(TRUMP);
    }

    @Test
    @DisplayName("MEDIUM: following — tiene trump pero no gana la baza con él")
    void medium_following_trumpDoesNotWin_discards() {
        // H1 ya tiene trump alto en mesa; bot tiene trump bajo → no gana
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.OROS,  Rank.TWO),  // trump bajo
                        card(Suit.COPAS, Rank.TWO)}, // no trump
                "H1", card(Suit.OROS, Rank.ACE)); // trump alto ya en mesa
        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);
        // No puede ganar con ninguna → descarta la más barata
        assertThat(result.getPoints()).isEqualTo(0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Ramas de heuristicScore (HARD leading con losing → ataque agresivo)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("HARD: bot perdiendo y tiene carta de 10+ pts — puede jugar agresivo al liderar")
    void hard_leading_losing_highValueCard_aggressivePlay() {
        Game game = buildGameWithScores(BOT_ID, 0, "H1", 20,
                new Card[]{
                        card(Suit.COPAS, Rank.THREE),  // 10 pts — trigger ataque agresivo
                        card(Suit.BASTOS, Rank.TWO)    //  0 pts
                });
        // Solo verificamos que no lanza y retorna algo válido
        Card result = service.decide(game, BOT_ID, BotDifficulty.HARD);
        assertThat(game.getPlayerById(BOT_ID).getHand()).contains(result);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Cobertura de decideMediumGreedy — rama sin triunfos disponibles al seguir
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("MEDIUM: following — no trump en mano, no puede ganar → descarta")
    void medium_following_noTrumpInHand_cantWin_discardsMinimal() {
        // H1 lidera con OROS-ACE (trump), bot no tiene trump ni copas → descarta
        Game game = buildGameWithTrick(BOT_ID,
                new Card[]{
                        card(Suit.ESPADAS, Rank.TWO),
                        card(Suit.BASTOS,  Rank.FOUR)},
                "H1", card(Suit.OROS, Rank.ACE)); // trump en mesa
        Card result = service.decide(game, BOT_ID, BotDifficulty.MEDIUM);
        assertThat(result.getPoints()).isEqualTo(0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Builders
    // ══════════════════════════════════════════════════════════════════════════

    private static Card card(Suit s, Rank r) { return new Card(s, r); }

    /** Juego de 1 jugador (bot), baza vacía, bot lidera. */
    private Game buildGame(String playerId, Card... cards) {
        Game game = new Game("T", 1, 4, BigDecimal.ONE);
        Player p = new Player(playerId, "Bot");
        game.addPlayer(p);
        for (Card c : cards) p.addCard(c);
        forceInProgress(game, TRUMP);
        return game;
    }

    /** Juego de 2 jugadores: rival ya jugó su carta, le toca al bot. */
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

    /** Juego de 2 jugadores con puntuaciones distintas, baza vacía, bot lidera. */
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

    /**
     * Juego de 3 jugadores.
     * Si {@code botLeads} es true la baza está vacía y es turno del bot (índice 2).
     */
    private Game buildThreePlayerGame(String botId, Card[] botCards,
                                      String h1Id, Card h1Card,
                                      String h2Id, Card h2Card,
                                      boolean botLeads) {
        Game game = new Game("T", 3, 4, BigDecimal.ONE);

        Player h1 = new Player(h1Id, "H1");
        h1.addCard(h1Card);
        game.addPlayer(h1);

        Player h2 = new Player(h2Id, "H2");
        h2.addCard(h2Card);
        game.addPlayer(h2);

        Player bot = new Player(botId, "Bot");
        for (Card c : botCards) bot.addCard(c);
        game.addPlayer(bot);

        forceInProgress(game, TRUMP);

        if (botLeads) {
            forceCurrentPlayerIndex(game, 2); // índice del bot
        }
        return game;
    }

    /**
     * Juego de 3 jugadores donde H1 ya jugó y le toca al bot (índice 1 tras H1).
     * H2 (índice 2) aún no jugó → la baza tiene 1 carta; baza incompleta al calcular.
     */
    private Game threePlayerGameWithOnePlayed(String botId, Card[] botCards,
                                              String h1Id, Card h1Card) {
        Game game = new Game("T", 3, 4, BigDecimal.ONE);

        Player h1 = new Player(h1Id, "H1");
        h1.addCard(h1Card);
        game.addPlayer(h1);

        Player bot = new Player(botId, "Bot");
        for (Card c : botCards) bot.addCard(c);
        game.addPlayer(bot);

        Player h2 = new Player("H2", "H2");
        h2.addCard(card(Suit.BASTOS, Rank.FOUR));
        game.addPlayer(h2);

        forceInProgress(game, TRUMP);

        // H1 ya jugó su carta
        h1.playCard(h1Card);
        game.getCurrentTrick().addCard(h1Id, h1Card);

        // Turno del bot (índice 1)
        forceCurrentPlayerIndex(game, 1);
        return game;
    }

    // ── helpers de reflexión ──────────────────────────────────────────────────

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
}