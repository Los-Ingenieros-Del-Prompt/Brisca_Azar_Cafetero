package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.domain.model.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Trick")
class TrickTest {

    private Trick trick;
    private final Card ace   = new Card(Suit.OROS,   Rank.ACE);   // 11 pts
    private final Card three = new Card(Suit.COPAS,  Rank.THREE); // 10 pts
    private final Card two   = new Card(Suit.BASTOS, Rank.TWO);   //  0 pts

    @BeforeEach void setUp() { trick = new Trick(); }

    @Test void newTrick_isEmpty() {
        assertThat(trick.getPlayedCards()).isEmpty();
        assertThat(trick.getLeadPlayerId()).isNull();
        assertThat(trick.getLeadSuit()).isNull();
        assertThat(trick.getTotalPoints()).isZero();
    }

    @Test void addCard_firstPlayer_becomesLead() {
        trick.addCard("P1", ace);
        assertThat(trick.getLeadPlayerId()).isEqualTo("P1");
        assertThat(trick.getLeadSuit()).isEqualTo(Suit.OROS);
    }

    @Test void addCard_secondPlayer_leadUnchanged() {
        trick.addCard("P1", ace);
        trick.addCard("P2", three);
        assertThat(trick.getLeadPlayerId()).isEqualTo("P1");
    }

    @Test void getTotalPoints_sumAllCards() {
        trick.addCard("P1", ace);
        trick.addCard("P2", three);
        assertThat(trick.getTotalPoints()).isEqualTo(21);
    }

    @Test void isComplete_exactPlayers_returnsTrue() {
        trick.addCard("P1", ace);
        trick.addCard("P2", three);
        assertThat(trick.isComplete(2)).isTrue();
        assertThat(trick.isComplete(3)).isFalse();
    }

    @Test void getCardByPlayer_returnsCorrectCard() {
        trick.addCard("P1", ace);
        assertThat(trick.getCardByPlayer("P1")).isEqualTo(ace);
        assertThat(trick.getCardByPlayer("P2")).isNull();
    }

    @Test void clear_resetsAllState() {
        trick.addCard("P1", ace);
        trick.clear();
        assertThat(trick.getPlayedCards()).isEmpty();
        assertThat(trick.getLeadPlayerId()).isNull();
    }

    @Test void getAllCards_returnsCopies() {
        trick.addCard("P1", ace);
        trick.addCard("P2", two);
        assertThat(trick.getAllCards()).containsExactly(ace, two);
    }

    @Test void getPlayedCards_returnsDefensiveCopy() {
        trick.addCard("P1", ace);
        trick.getPlayedCards().clear();
        assertThat(trick.getPlayedCardCount()).isEqualTo(1);
    }
}