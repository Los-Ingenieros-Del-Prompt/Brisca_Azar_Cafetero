package com.aguardientes.azarcafetero.dominio;

import com.aguardientes.azarcafetero.application.dto.GameStateDTO;
import com.aguardientes.azarcafetero.application.service.GameMapper;
import com.aguardientes.azarcafetero.domain.exception.*;
import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.TrickResolver;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests unitarios del dominio: Game, Player, Card, Trick y GameMapper.
 *
 * Cubre:
 * - Game: addPlayer, removePlayer (varios índices), start, getCurrentPlayer,
 *   isPlayerTurn, playCard, isTrickComplete, resolveTrick,
 *   getWinner/getWinners/getRanking, finish, canStart, hasHumanPlayers,
 *   isGameOver, getBetAmount, defensiveCopy, null guards
 * - Player: addCard (null), equals/hashCode
 * - Card: isTrump
 * - Trick: getLeadSuit vacío
 * - GameMapper: null guards, visibilidad de mano
 */
@DisplayName("Game – dominio")
class GameTest {

    private Game game;
    private Player p1, p2;

    @BeforeEach void setUp() {
        game = new Game("G1", 2, 4, BigDecimal.TEN);
        p1 = new Player("P1", "Alice");
        p2 = new Player("P2", "Bob");
    }

    // ── addPlayer ─────────────────────────────────────────────────────────────

    @Test void addPlayer_underMax_succeeds() {
        game.addPlayer(p1);
        assertThat(game.getPlayerCount()).isEqualTo(1);
    }

    @Test void addPlayer_duplicate_throws() {
        game.addPlayer(p1);
        assertThatThrownBy(() -> game.addPlayer(new Player("P1", "Other")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void addPlayer_whenFull_throwsGameFull() {
        Game g = new Game("G2", 2, 2, BigDecimal.ONE);
        g.addPlayer(p1);
        g.addPlayer(p2);
        assertThatThrownBy(() -> g.addPlayer(new Player("P3", "Charlie")))
                .isInstanceOf(GameFullException.class);
    }

    @Test void addPlayer_afterStart_throws() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        assertThatThrownBy(() -> game.addPlayer(new Player("P3", "Charlie")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── removePlayer ──────────────────────────────────────────────────────────

    @Test void removePlayer_existing_removesFromList() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.removePlayer("P1");
        assertThat(game.getPlayers()).noneMatch(p -> p.getId().equals("P1"));
    }

    @Test void removePlayer_nonExistent_doesNotThrow() {
        game.addPlayer(p1);
        assertThatCode(() -> game.removePlayer("GHOST")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("removePlayer: índice == currentPlayerIndex no falla")
    void removePlayer_indexEqualsCurrentPlayer() {
        Game g = new Game("G1", 2, 4, BigDecimal.TEN);
        g.addPlayer(new Player("P1", "Alice"));
        g.addPlayer(new Player("P2", "Bob"));
        g.addPlayer(new Player("P3", "Charlie"));
        // currentPlayerIndex = 0 por defecto; eliminamos P1 (idx 0 == 0)
        g.removePlayer("P1");
        assertThat(g.getPlayerCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("removePlayer: index < currentPlayerIndex decrementa índice")
    void removePlayer_indexLessThanCurrent_decrementsIndex() {
        Game g = new Game("G1", 2, 4, BigDecimal.TEN);
        g.addPlayer(new Player("P1", "Alice"));
        g.addPlayer(new Player("P2", "Bob"));
        g.addPlayer(new Player("P3", "Charlie"));
        setCurrentPlayerIndex(g, 2);
        g.removePlayer("P1");
        assertThat(g.getCurrentPlayerIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("removePlayer: currentPlayerIndex >= size tras borrado → reset a 0")
    void removePlayer_indexOutOfBounds_resetsToZero() {
        Game g = new Game("G1", 2, 4, BigDecimal.TEN);
        g.addPlayer(new Player("P1", "Alice"));
        g.addPlayer(new Player("P2", "Bob"));
        setCurrentPlayerIndex(g, 1);
        g.removePlayer("P2");
        assertThat(g.getCurrentPlayerIndex()).isEqualTo(0);
    }

    // ── start ─────────────────────────────────────────────────────────────────

    @Test void start_withEnoughPlayers_setsInProgress() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        assertThat(game.getState()).isEqualTo(GameState.IN_PROGRESS);
        assertThat(game.getTrumpCard()).isNotNull();
        assertThat(game.getTrumpSuit()).isNotNull();
    }

    @Test void start_dealsThreeCardsEach() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        game.getPlayers().forEach(p -> assertThat(p.getHandSize()).isEqualTo(3));
    }

    @Test void start_notEnoughPlayers_throws() {
        game.addPlayer(p1);
        assertThatThrownBy(() -> game.start()).isInstanceOf(IllegalStateException.class);
    }

    @Test void start_alreadyStarted_throws() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        assertThatThrownBy(() -> game.start()).isInstanceOf(IllegalStateException.class);
    }

    // ── getCurrentPlayer / turn ───────────────────────────────────────────────

    @Test void getCurrentPlayer_returnsFirstPlayer() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        assertThat(game.getCurrentPlayer()).isNotNull();
    }

    @Test
    @DisplayName("getCurrentPlayer con lista vacía devuelve null")
    void getCurrentPlayer_emptyPlayers_returnsNull() {
        assertThat(new Game("G1", 2, 4, BigDecimal.TEN).getCurrentPlayer()).isNull();
    }

    @Test void isPlayerTurn_correctPlayer_returnsTrue() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        String currentId = game.getCurrentPlayer().getId();
        assertThat(game.isPlayerTurn(currentId)).isTrue();
    }

    @Test void isPlayerTurn_wrongPlayer_returnsFalse() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        String currentId = game.getCurrentPlayer().getId();
        String otherId = currentId.equals("P1") ? "P2" : "P1";
        assertThat(game.isPlayerTurn(otherId)).isFalse();
    }

    @Test
    @DisplayName("isPlayerTurn sin jugadores devuelve false")
    void isPlayerTurn_noPlayers_returnsFalse() {
        assertThat(new Game("G1", 2, 4, BigDecimal.TEN).isPlayerTurn("P1")).isFalse();
    }

    // ── playCard ──────────────────────────────────────────────────────────────

    @Test void playCard_validCard_addedToTrick() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        Player current = game.getCurrentPlayer();
        Card card = current.getHand().get(0);
        game.playCard(current.getId(), card);
        assertThat(game.getCurrentTrick().getCardByPlayer(current.getId())).isEqualTo(card);
    }

    @Test void playCard_wrongTurn_throws() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        String currentId = game.getCurrentPlayer().getId();
        String otherId = currentId.equals("P1") ? "P2" : "P1";
        Player other = game.getPlayerById(otherId);
        Card card = other.getHand().get(0);
        assertThatThrownBy(() -> game.playCard(otherId, card))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void playCard_notStarted_throwsGameNotStarted() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        assertThatThrownBy(() -> game.playCard("P1", new Card(Suit.OROS, Rank.ACE)))
                .isInstanceOf(GameNotStartedException.class);
    }

    @Test void playCard_playerNotInGame_throwsPlayerNotFound() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        assertThatThrownBy(() -> game.playCard("GHOST", new Card(Suit.OROS, Rank.ACE)))
                .isInstanceOf(PlayerNotInGameException.class);
    }

    // ── isTrickComplete / resolveTrick ────────────────────────────────────────

    @Test void isTrickComplete_afterBothPlay_returnsTrue() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        playBothCards();
        assertThat(game.isTrickComplete()).isTrue();
    }

    @Test void resolveTrick_addsPointsToWinner() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        int trickPoints = playBothCardsAndGetPoints();
        String winnerId = resolveWithFirstPlayer();
        Player winner = game.getPlayerById(winnerId);
        assertThat(winner.getScore()).isEqualTo(trickPoints);
    }

    @Test void resolveTrick_unknownWinner_throwsPlayerNotFound() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        playBothCards();
        assertThatThrownBy(() -> game.resolveTrick("GHOST"))
                .isInstanceOf(PlayerNotInGameException.class);
    }

    // ── isGameOver ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isGameOver: false si deck no está agotado")
    void isGameOver_deckNotExhausted_returnsFalse() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        assertThat(game.isGameOver()).isFalse();
    }

    @Test
    @DisplayName("isGameOver: false si deck agotado pero jugadores tienen cartas")
    void isGameOver_deckEmptyButPlayersHaveCards_returnsFalse() {
        Game g = new Game("G1", 2, 4, BigDecimal.TEN);
        Player a = new Player("P1", "Alice");
        Player b = new Player("P2", "Bob");
        g.addPlayer(a);
        g.addPlayer(b);
        a.addCard(new Card(Suit.OROS, Rank.ACE));
        b.addCard(new Card(Suit.COPAS, Rank.ACE));
        drainDeck(g);
        assertThat(g.isGameOver()).isFalse();
    }

    // ── getWinner / getWinners / getRanking ───────────────────────────────────

    @Test void getWinner_beforeFinish_returnsNull() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        assertThat(game.getWinner()).isNull();
    }

    @Test void getWinners_beforeFinish_returnsEmpty() {
        game.addPlayer(p1);
        assertThat(game.getWinners()).isEmpty();
    }

    @Test void finish_setsStateToFinished() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        game.finish();
        assertThat(game.getState()).isEqualTo(GameState.FINISHED);
    }

    @Test void getWinner_afterFinish_returnsHighestScorer() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        game.getPlayerById("P1").addPoints(30);
        game.finish();
        assertThat(game.getWinner().getId()).isEqualTo("P1");
    }

    @Test void getWinners_tie_returnsBothPlayers() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        game.getPlayerById("P1").addPoints(20);
        game.getPlayerById("P2").addPoints(20);
        game.finish();
        assertThat(game.getWinners()).hasSize(2);
    }

    @Test void getRanking_sortedByScoreDescending() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        game.getPlayerById("P2").addPoints(25);
        game.getPlayerById("P1").addPoints(10);
        List<Player> ranking = game.getRanking();
        assertThat(ranking.get(0).getId()).isEqualTo("P2");
    }

    // ── canStart / hasHumanPlayers ────────────────────────────────────────────

    @Test void canStart_withEnoughPlayers_returnsTrue() {
        game.addPlayer(p1);
        game.addPlayer(p2);
        assertThat(game.canStart()).isTrue();
    }

    @Test void canStart_notEnoughPlayers_returnsFalse() {
        game.addPlayer(p1);
        assertThat(game.canStart()).isFalse();
    }

    @Test void hasHumanPlayers_withHuman_returnsTrue() {
        game.addPlayer(new Player("BOT_EASY_abc", "Bot"));
        game.addPlayer(p1);
        assertThat(game.hasHumanPlayers()).isTrue();
    }

    @Test void hasHumanPlayers_onlyBots_returnsFalse() {
        game.addPlayer(new Player("BOT_EASY_abc", "Bot1"));
        game.addPlayer(new Player("BOT_HARD_xyz", "Bot2"));
        assertThat(game.hasHumanPlayers()).isFalse();
    }

    // ── misc ──────────────────────────────────────────────────────────────────

    @Test void getPlayers_returnsDefensiveCopy() {
        game.addPlayer(p1);
        game.getPlayers().clear();
        assertThat(game.getPlayerCount()).isEqualTo(1);
    }

    @Test void getBetAmount_returnsCorrectly() {
        assertThat(game.getBetAmount()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test void nullId_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> new Game(null, 2, 4, BigDecimal.ONE));
    }

    @Test
    @DisplayName("null betAmount lanza NullPointerException")
    void nullBetAmount_throws() {
        assertThatNullPointerException().isThrownBy(() -> new Game("G1", 2, 4, null));
    }

    // ── Player ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Player.addCard(null) no agrega nada")
    void player_addCard_null_doesNotAdd() {
        Player p = new Player("P1", "Alice");
        p.addCard(null);
        assertThat(p.getHandSize()).isEqualTo(0);
    }

    @Test
    @DisplayName("Player.equals: misma instancia → true")
    void player_equals_sameInstance() {
        Player p = new Player("P1", "Alice");
        assertThat(p).isEqualTo(p);
    }

    @Test
    @DisplayName("Player.equals: null → false")
    void player_equals_null() {
        assertThat(new Player("P1", "Alice").equals(null)).isFalse();
    }

    @Test
    @DisplayName("Player.equals: clase distinta → false")
    void player_equals_differentClass() {
        assertThat(new Player("P1", "Alice").equals("str")).isFalse();
    }

    // ── Card ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Card.isTrump: misma suit → true")
    void card_isTrump_true() {
        assertThat(new Card(Suit.OROS, Rank.ACE).isTrump(Suit.OROS)).isTrue();
    }

    @Test
    @DisplayName("Card.isTrump: suit distinta → false")
    void card_isTrump_false() {
        assertThat(new Card(Suit.OROS, Rank.ACE).isTrump(Suit.COPAS)).isFalse();
    }

    // ── Trick ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Trick.getLeadSuit: baza vacía → null")
    void trick_getLeadSuit_empty_returnsNull() {
        assertThat(new Trick().getLeadSuit()).isNull();
    }

    // ── GameMapper ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("GameMapper.toTrickDTO(null) → null")
    void mapper_toTrickDTO_null() {
        assertThat(new GameMapper().toTrickDTO(null)).isNull();
    }

    @Test
    @DisplayName("GameMapper.toGameStateDTO(null, id) → null")
    void mapper_toGameStateDTO_null() {
        assertThat(new GameMapper().toGameStateDTO(null, "P1")).isNull();
    }

    @Test
    @DisplayName("GameMapper.toPublicGameStateDTO(null) → null")
    void mapper_toPublicGameStateDTO_null() {
        assertThat(new GameMapper().toPublicGameStateDTO(null)).isNull();
    }

    @Test
    @DisplayName("GameMapper.toFullGameStateDTO(null) → null")
    void mapper_toFullGameStateDTO_null() {
        assertThat(new GameMapper().toFullGameStateDTO(null)).isNull();
    }

    @Test
    @DisplayName("GameMapper.toGameStateDTO muestra mano sólo al jugador que pide")
    void mapper_toGameStateDTO_onlyRequestingPlayerHasHand() {
        Game g = new Game("G1", 2, 4, BigDecimal.TEN);
        g.addPlayer(new Player("P1", "Alice"));
        g.addPlayer(new Player("P2", "Bob"));
        g.start();
        var dto = new GameMapper().toGameStateDTO(g, "P1");
        assertThat(dto.players().stream().filter(p -> p.id().equals("P1")).findFirst().orElseThrow().hand()).isNotEmpty();
        assertThat(dto.players().stream().filter(p -> p.id().equals("P2")).findFirst().orElseThrow().hand()).isEmpty();
    }

    // ── helpers privados ──────────────────────────────────────────────────────

    private void playBothCards() {
        Player first = game.getCurrentPlayer();
        Card c1 = first.getHand().get(0);
        game.playCard(first.getId(), c1);
        Player second = game.getCurrentPlayer();
        Card c2 = second.getHand().get(0);
        game.playCard(second.getId(), c2);
    }

    private int playBothCardsAndGetPoints() {
        Player first = game.getCurrentPlayer();
        Card c1 = first.getHand().get(0);
        game.playCard(first.getId(), c1);
        Player second = game.getCurrentPlayer();
        Card c2 = second.getHand().get(0);
        game.playCard(second.getId(), c2);
        return game.getCurrentTrick().getTotalPoints();
    }

    private String resolveWithFirstPlayer() {
        TrickResolver trickResolver = new TrickResolver();
        String winnerId = trickResolver.determineWinner(game.getCurrentTrick(), game.getTrumpSuit());
        game.resolveTrick(winnerId);
        assertThat(game.getCurrentPlayer().getId()).isEqualTo(winnerId);
        return winnerId;
    }

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
}