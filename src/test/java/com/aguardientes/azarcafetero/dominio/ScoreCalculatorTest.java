package com.aguardientes.azarcafetero.dominio;

import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.ScoreCalculator;
import org.junit.jupiter.api.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@DisplayName("ScoreCalculator")
class ScoreCalculatorTest {

    private final ScoreCalculator calc = new ScoreCalculator();

    @Test void calculateTrickPoints_sumsAllCards() {
        Trick trick = new Trick();
        trick.addCard("P1", new Card(Suit.OROS,  Rank.ACE));   // 11
        trick.addCard("P2", new Card(Suit.COPAS, Rank.THREE)); // 10
        assertThat(calc.calculateTrickPoints(trick)).isEqualTo(21);
    }

    @Test void calculatePlayerScore_returnsCurrentScore() {
        Player p = new Player("P1", "A");
        p.addPoints(25);
        assertThat(calc.calculatePlayerScore(p)).isEqualTo(25);
    }

    @Test void findWinner_returnsHighestScorer() {
        Player p1 = playerWithScore("P1", 20);
        Player p2 = playerWithScore("P2", 40);
        assertThat(calc.findWinner(List.of(p1, p2)).getId()).isEqualTo("P2");
    }

    @Test void findWinner_emptyList_returnsNull() {
        assertThat(calc.findWinner(List.of())).isNull();
    }

    @Test void getRanking_sortedDescending() {
        Player p1 = playerWithScore("P1", 10);
        Player p2 = playerWithScore("P2", 30);
        Player p3 = playerWithScore("P3", 20);
        List<Player> ranking = calc.getRanking(List.of(p1, p2, p3));
        assertThat(ranking).extracting(Player::getId)
                .containsExactly("P2", "P3", "P1");
    }

    private Player playerWithScore(String id, int score) {
        Player p = new Player(id, id);
        p.addPoints(score);
        return p;
    }
}