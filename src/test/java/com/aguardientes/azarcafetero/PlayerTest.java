package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.domain.exception.InvalidMoveException;
import com.aguardientes.azarcafetero.domain.model.*;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

@DisplayName("Player")
class PlayerTest {

    private Player player;
    private final Card ace = new Card(Suit.OROS, Rank.ACE);
    private final Card two = new Card(Suit.COPAS, Rank.TWO);

    @BeforeEach void setUp() {
        player = new Player("P1", "Alice");
    }

    @Test void newPlayer_hasEmptyHand_andZeroScore() {
        assertThat(player.getId()).isEqualTo("P1");
        assertThat(player.getName()).isEqualTo("Alice");
        assertThat(player.getScore()).isZero();
        assertThat(player.getHandSize()).isZero();
        assertThat(player.hasCards()).isFalse();
    }

    @Test void addCard_increasesHandSize() {
        player.addCard(ace);
        assertThat(player.getHandSize()).isEqualTo(1);
        assertThat(player.hasCards()).isTrue();
    }

    @Test void addCard_null_isIgnored() {
        player.addCard(null);
        assertThat(player.getHandSize()).isZero();
    }

    @Test void playCard_existingCard_removesFromHand() {
        player.addCard(ace);
        Card played = player.playCard(ace);
        assertThat(played).isEqualTo(ace);
        assertThat(player.getHandSize()).isZero();
    }

    @Test void playCard_cardNotInHand_throwsInvalidMove() {
        assertThatThrownBy(() -> player.playCard(ace))
                .isInstanceOf(InvalidMoveException.class);
    }

    @Test void addPoints_accumulatesCorrectly() {
        player.addPoints(11);
        player.addPoints(10);
        assertThat(player.getScore()).isEqualTo(21);
    }

    @Test void getHand_returnsDefensiveCopy() {
        player.addCard(ace);
        player.getHand().clear();
        assertThat(player.getHandSize()).isEqualTo(1);
    }

    @Test void equality_sameId_isEqual() {
        Player p2 = new Player("P1", "Different Name");
        assertThat(player).isEqualTo(p2).hasSameHashCodeAs(p2);
    }

    @Test void equality_differentId_notEqual() {
        assertThat(player).isNotEqualTo(new Player("P2", "Alice"));
    }

    @Test void nullId_throws() {
        assertThatNullPointerException().isThrownBy(() -> new Player(null, "Alice"));
    }
}