package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.TrickResolver;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("TrickResolver")
class TrickResolverTest {

    private final TrickResolver resolver = new TrickResolver();
    private static final Suit TRUMP = Suit.OROS;

    @Test void trumpBeatsNonTrump() {
        Trick trick = new Trick();
        trick.addCard("P1", new Card(Suit.COPAS, Rank.ACE));  // 11pts, no trump
        trick.addCard("P2", new Card(Suit.OROS,  Rank.TWO)); // 0pts, trump
        assertThat(resolver.determineWinner(trick, TRUMP)).isEqualTo("P2");
    }

    @Test void higherTrumpBeatsLowerTrump() {
        Trick trick = new Trick();
        trick.addCard("P1", new Card(Suit.OROS, Rank.TWO));  // numeric 1
        trick.addCard("P2", new Card(Suit.OROS, Rank.ACE));  // numeric 11
        assertThat(resolver.determineWinner(trick, TRUMP)).isEqualTo("P2");
    }

    @Test void leadSuitBeatsOtherNonTrump() {
        Trick trick = new Trick();
        trick.addCard("P1", new Card(Suit.COPAS,   Rank.THREE)); // lead suit
        trick.addCard("P2", new Card(Suit.ESPADAS, Rank.ACE));   // different suit, higher value
        assertThat(resolver.determineWinner(trick, TRUMP)).isEqualTo("P1");
    }

    @Test void higherLeadSuitBeatsLower() {
        Trick trick = new Trick();
        trick.addCard("P1", new Card(Suit.COPAS, Rank.THREE)); // lead, 10pts, numeric 3
        trick.addCard("P2", new Card(Suit.COPAS, Rank.ACE));   // same suit, numeric 11
        assertThat(resolver.determineWinner(trick, TRUMP)).isEqualTo("P2");
    }

    @Test void firstPlayerWins_whenNoTrumpAndDifferentSuits() {
        Trick trick = new Trick();
        trick.addCard("P1", new Card(Suit.COPAS,   Rank.TWO)); // lead
        trick.addCard("P2", new Card(Suit.ESPADAS, Rank.ACE)); // different suit
        // Neither is trump, P2 doesn't match lead suit → P1 wins
        assertThat(resolver.determineWinner(trick, TRUMP)).isEqualTo("P1");
    }

    @Test void singleCard_thatPlayerWins() {
        Trick trick = new Trick();
        trick.addCard("P1", new Card(Suit.BASTOS, Rank.SEVEN));
        assertThat(resolver.determineWinner(trick, TRUMP)).isEqualTo("P1");
    }

    @Test void threePlayer_trumpAlwaysWins() {
        Trick trick = new Trick();
        trick.addCard("P1", new Card(Suit.COPAS, Rank.ACE));   // 11pts no trump
        trick.addCard("P2", new Card(Suit.COPAS, Rank.THREE)); // 10pts no trump
        trick.addCard("P3", new Card(Suit.OROS,  Rank.TWO));   // 0pts trump
        assertThat(resolver.determineWinner(trick, TRUMP)).isEqualTo("P3");
    }
}