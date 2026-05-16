package com.aguardientes.azarcafetero.dominio;

import com.aguardientes.azarcafetero.domain.model.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Card")
class CardTest {

    @Test void gettersReturnCorrectValues() {
        Card c = new Card(Suit.OROS, Rank.ACE);
        assertThat(c.getSuit()).isEqualTo(Suit.OROS);
        assertThat(c.getRank()).isEqualTo(Rank.ACE);
        assertThat(c.getPoints()).isEqualTo(11);
        assertThat(c.getNumericValue()).isEqualTo(11);
    }

    @Test void isTrump_matchingSuit_returnsTrue() {
        Card c = new Card(Suit.OROS, Rank.TWO);
        assertThat(c.isTrump(Suit.OROS)).isTrue();
        assertThat(c.isTrump(Suit.COPAS)).isFalse();
    }

    @Test void equality_sameCard_isEqual() {
        Card a = new Card(Suit.COPAS, Rank.KING);
        Card b = new Card(Suit.COPAS, Rank.KING);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test void equality_differentCards_notEqual() {
        assertThat(new Card(Suit.OROS, Rank.ACE))
                .isNotEqualTo(new Card(Suit.COPAS, Rank.ACE));
    }

    @Test void toString_containsRankAndSuit() {
        String s = new Card(Suit.ESPADAS, Rank.KING).toString();
        assertThat(s).contains("Rey").contains("Espadas");
    }

    @Test void nullSuit_throws() {
        assertThatNullPointerException().isThrownBy(() -> new Card(null, Rank.ACE));
    }

    @Test void nullRank_throws() {
        assertThatNullPointerException().isThrownBy(() -> new Card(Suit.OROS, null));
    }
}