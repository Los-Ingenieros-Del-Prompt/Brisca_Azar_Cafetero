package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.service.GameMapper;
import com.aguardientes.azarcafetero.domain.model.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("GameMapper")
class GameMapperTest {

    private final GameMapper mapper = new GameMapper();

    @Test void toCardDTO_mapsAllFields() {
        CardDTO dto = mapper.toCardDTO(new Card(Suit.OROS, Rank.ACE));
        assertThat(dto.suit()).isEqualTo(Suit.OROS);
        assertThat(dto.rank()).isEqualTo(Rank.ACE);
        assertThat(dto.points()).isEqualTo(11);
    }

    @Test void toCardDTO_null_returnsNull() {
        assertThat(mapper.toCardDTO(null)).isNull();
    }

    @Test void toPlayerDTO_withHand_includesCards() {
        Player p = new Player("P1", "Alice");
        p.addCard(new Card(Suit.COPAS, Rank.KING));
        PlayerDTO dto = mapper.toPlayerDTO(p, true);
        assertThat(dto.id()).isEqualTo("P1");
        assertThat(dto.name()).isEqualTo("Alice");
        assertThat(dto.hand()).hasSize(1);
        assertThat(dto.handSize()).isEqualTo(1);
    }

    @Test void toPlayerDTO_withoutHand_returnsEmptyList() {
        Player p = new Player("P1", "Alice");
        p.addCard(new Card(Suit.COPAS, Rank.KING));
        PlayerDTO dto = mapper.toPlayerDTO(p, false);
        assertThat(dto.hand()).isEmpty();
        assertThat(dto.handSize()).isEqualTo(1); // size still correct
    }

    @Test void toPlayerDTO_null_returnsNull() {
        assertThat(mapper.toPlayerDTO(null, true)).isNull();
    }

    @Test void toTrickDTO_mapsPlayedCards() {
        Trick trick = new Trick();
        trick.addCard("P1", new Card(Suit.OROS, Rank.ACE));
        TrickDTO dto = mapper.toTrickDTO(trick);
        assertThat(dto.playedCards()).containsKey("P1");
        assertThat(dto.leadPlayerId()).isEqualTo("P1");
        assertThat(dto.totalPoints()).isEqualTo(11);
    }

    @Test void toTrickDTO_null_returnsNull() {
        assertThat(mapper.toTrickDTO(null)).isNull();
    }

    @Test void toGameStateDTO_onlyIncludesRequestingPlayerHand() {
        Game game = startedGame("P1", "P2");
        GameStateDTO dto = mapper.toGameStateDTO(game, "P1");
        PlayerDTO p1dto = dto.players().stream().filter(p -> p.id().equals("P1")).findFirst().orElseThrow();
        PlayerDTO p2dto = dto.players().stream().filter(p -> p.id().equals("P2")).findFirst().orElseThrow();
        assertThat(p1dto.hand()).isNotEmpty();
        assertThat(p2dto.hand()).isEmpty();
    }

    @Test void toFullGameStateDTO_includesAllHands() {
        Game game = startedGame("P1", "P2");
        GameStateDTO dto = mapper.toFullGameStateDTO(game);
        dto.players().forEach(p -> assertThat(p.hand()).isNotEmpty());
    }

    @Test void toPublicGameStateDTO_noHandsVisible() {
        Game game = startedGame("P1", "P2");
        GameStateDTO dto = mapper.toPublicGameStateDTO(game);
        dto.players().forEach(p -> assertThat(p.hand()).isEmpty());
    }

    @Test void toGameStateDTO_null_returnsNull() {
        assertThat(mapper.toGameStateDTO(null, "P1")).isNull();
    }

    @Test void winners_finishedGame_mapsCorrectly() {
        Game game = startedGame("P1", "P2");
        game.getPlayerById("P1").addPoints(50);
        game.finish();
        GameStateDTO dto = mapper.toFullGameStateDTO(game);
        assertThat(dto.winners()).hasSize(1);
        assertThat(dto.winners().get(0).id()).isEqualTo("P1");
    }

    private Game startedGame(String id1, String id2) {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player(id1, id1));
        game.addPlayer(new Player(id2, id2));
        game.start();
        return game;
    }
}