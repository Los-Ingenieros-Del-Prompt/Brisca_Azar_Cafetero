package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.domain.model.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Deck")
class DeckTest {

    @Test void newDeck_has40Cards() {
        Deck d = new Deck();
        assertThat(d.remainingCards()).isEqualTo(40);
        assertThat(d.isEmpty()).isFalse();
    }

    @Test void draw_reducesCount() {
        Deck d = new Deck();
        Card c = d.draw();
        assertThat(c).isNotNull();
        assertThat(d.remainingCards()).isEqualTo(39);
    }

    @Test void draw_emptyDeck_returnsNull() {
        Deck d = new Deck();
        for (int i = 0; i < 40; i++) d.draw();
        assertThat(d.draw()).isNull();
        assertThat(d.isEmpty()).isTrue();
    }

    @Test void setTrumpCard_lastCardBecomesTrump() {
        Deck d = new Deck();
        d.setTrumpCard();
        assertThat(d.getTrumpCard()).isNotNull();
        assertThat(d.getTrumpSuit()).isNotNull();
    }

    @Test void getTrumpSuit_beforeSet_returnsNull() {
        Deck d = new Deck();
        assertThat(d.getTrumpSuit()).isNull();
    }

    @Test void getCards_returnsDefensiveCopy() {
        Deck d = new Deck();
        d.getCards().clear();
        assertThat(d.remainingCards()).isEqualTo(40);
    }

    @Test void shuffle_doesNotChangeTotalCards() {
        Deck d = new Deck();
        d.shuffle();
        assertThat(d.remainingCards()).isEqualTo(40);
    }
}