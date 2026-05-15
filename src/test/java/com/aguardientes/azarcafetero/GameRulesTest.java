package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.domain.exception.*;
import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.GameRules;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("GameRules")
class GameRulesTest {

    private final GameRules rules = new GameRules();

    @Test void validatePlayerTurn_correct_doesNotThrow() {
        Game game = startedGame();
        String currentId = game.getCurrentPlayer().getId();
        assertThatCode(() -> rules.validatePlayerTurn(game, currentId))
                .doesNotThrowAnyException();
    }

    @Test void validatePlayerTurn_wrong_throwsNotPlayerTurn() {
        Game game = startedGame();
        String currentId = game.getCurrentPlayer().getId();
        String otherId = currentId.equals("P1") ? "P2" : "P1";
        assertThatThrownBy(() -> rules.validatePlayerTurn(game, otherId))
                .isInstanceOf(NotPlayerTurnException.class);
    }

    @Test void validateCardPlay_playerHasCard_doesNotThrow() {
        Game game = startedGame();
        Player p = game.getCurrentPlayer();
        Card card = p.getHand().get(0);
        assertThatCode(() -> rules.validateCardPlay(game, p.getId(), card))
                .doesNotThrowAnyException();
    }

    @Test void validateCardPlay_playerDoesNotHaveCard_throws() {
        Game game = startedGame();
        Player p = game.getCurrentPlayer();
        Card fake = new Card(Suit.OROS, Rank.ACE);
        // Ensure player doesn't have this specific card
        if (p.getHand().contains(fake)) return; // skip if unlucky deal
        assertThatThrownBy(() -> rules.validateCardPlay(game, p.getId(), fake))
                .isInstanceOf(InvalidMoveException.class);
    }

    @Test void validateCardPlay_playerNotFound_throws() {
        Game game = startedGame();
        assertThatThrownBy(() -> rules.validateCardPlay(game, "GHOST",
                new Card(Suit.OROS, Rank.ACE)))
                .isInstanceOf(InvalidMoveException.class);
    }

    @Test void validateGameStart_canStart_doesNotThrow() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "A"));
        game.addPlayer(new Player("P2", "B"));
        assertThatCode(() -> rules.validateGameStart(game)).doesNotThrowAnyException();
    }

    @Test void validateGameStart_cannotStart_throws() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "A")); // only 1 player
        assertThatThrownBy(() -> rules.validateGameStart(game))
                .isInstanceOf(InvalidMoveException.class);
    }

    @Test void canPlayerPlayCard_hasCard_returnsTrue() {
        Player p = new Player("P1", "A");
        Card c = new Card(Suit.OROS, Rank.ACE);
        p.addCard(c);
        assertThat(rules.canPlayerPlayCard(p, c)).isTrue();
    }

    @Test void canPlayerPlayCard_noCard_returnsFalse() {
        Player p = new Player("P1", "A");
        assertThat(rules.canPlayerPlayCard(p, new Card(Suit.OROS, Rank.ACE))).isFalse();
    }

    private Game startedGame() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start();
        return game;
    }
}