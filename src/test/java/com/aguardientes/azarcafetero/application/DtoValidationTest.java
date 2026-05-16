package com.aguardientes.azarcafetero.application;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.domain.model.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("DTO validation tests")
class DtoValidationTest {

    // ─── GetGameStateCommand ──────────────────────────────────────────────────

    @Test void getGameState_valid_doesNotThrow() {
        assertThatNoException().isThrownBy(() -> new GetGameStateCommand("P1"));
    }

    @Test void getGameState_null_throws() {
        assertThatThrownBy(() -> new GetGameStateCommand(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void getGameState_blank_throws() {
        assertThatThrownBy(() -> new GetGameStateCommand("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── CreateGameCommand ────────────────────────────────────────────────────

    @Test void createGame_null_gameId_throws() {
        assertThatThrownBy(() -> new CreateGameCommand(null, 2, 4, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void createGame_blank_gameId_throws() {
        assertThatThrownBy(() -> new CreateGameCommand("  ", 2, 4, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void createGame_minPlayers_tooLow_throws() {
        assertThatThrownBy(() -> new CreateGameCommand("G1", 1, 4, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void createGame_minPlayers_tooHigh_throws() {
        assertThatThrownBy(() -> new CreateGameCommand("G1", 5, 5, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void createGame_maxPlayers_tooLow_throws() {
        assertThatThrownBy(() -> new CreateGameCommand("G1", 2, 1, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void createGame_maxPlayers_tooHigh_throws() {
        assertThatThrownBy(() -> new CreateGameCommand("G1", 2, 5, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void createGame_minGreaterThanMax_throws() {
        assertThatThrownBy(() -> new CreateGameCommand("G1", 4, 2, BigDecimal.TEN))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void createGame_betAmount_null_throws() {
        assertThatThrownBy(() -> new CreateGameCommand("G1", 2, 4, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void createGame_betAmount_zero_throws() {
        assertThatThrownBy(() -> new CreateGameCommand("G1", 2, 4, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void createGame_betAmount_negative_throws() {
        assertThatThrownBy(() -> new CreateGameCommand("G1", 2, 4, new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── PlayCardCommand ──────────────────────────────────────────────────────

    @Test void playCard_null_gameId_throws() {
        assertThatThrownBy(() -> new PlayCardCommand(null, "P1", Suit.OROS, Rank.ACE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void playCard_blank_gameId_throws() {
        assertThatThrownBy(() -> new PlayCardCommand("  ", "P1", Suit.OROS, Rank.ACE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void playCard_null_playerId_throws() {
        assertThatThrownBy(() -> new PlayCardCommand("G1", null, Suit.OROS, Rank.ACE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void playCard_blank_playerId_throws() {
        assertThatThrownBy(() -> new PlayCardCommand("G1", "  ", Suit.OROS, Rank.ACE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void playCard_null_suit_throws() {
        assertThatThrownBy(() -> new PlayCardCommand("G1", "P1", null, Rank.ACE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void playCard_null_rank_throws() {
        assertThatThrownBy(() -> new PlayCardCommand("G1", "P1", Suit.OROS, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── JoinGameCommand ──────────────────────────────────────────────────────

    @Test void joinGame_null_gameId_throws() {
        assertThatThrownBy(() -> new JoinGameCommand(null, "P1", "Alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void joinGame_blank_gameId_throws() {
        assertThatThrownBy(() -> new JoinGameCommand(" ", "P1", "Alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void joinGame_null_playerId_throws() {
        assertThatThrownBy(() -> new JoinGameCommand("G1", null, "Alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void joinGame_blank_playerId_throws() {
        assertThatThrownBy(() -> new JoinGameCommand("G1", " ", "Alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void joinGame_null_playerName_throws() {
        assertThatThrownBy(() -> new JoinGameCommand("G1", "P1", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void joinGame_blank_playerName_throws() {
        assertThatThrownBy(() -> new JoinGameCommand("G1", "P1", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── AddBotCommand edge cases ─────────────────────────────────────────────

    @Test void addBot_null_gameId_throws() {
        assertThatThrownBy(() -> new AddBotCommand(null, BotDifficulty.EASY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void addBot_blank_gameId_throws() {
        assertThatThrownBy(() -> new AddBotCommand("  ", BotDifficulty.EASY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void addBot_null_difficulty_defaults_to_medium() {
        AddBotCommand cmd = new AddBotCommand("G1", null);
        assertThat(cmd.difficulty()).isEqualTo(BotDifficulty.MEDIUM);
    }

    @Test void addBot_generateBotId_easy_hasPrefix() {
        assertThat(AddBotCommand.generateBotId(BotDifficulty.EASY)).startsWith("BOT_EASY_");
    }

    @Test void addBot_generateBotId_hard_hasPrefix() {
        assertThat(AddBotCommand.generateBotId(BotDifficulty.HARD)).startsWith("BOT_HARD_");
    }

    @Test void addBot_isBot_true_for_botId() {
        assertThat(AddBotCommand.isBot("BOT_EASY_abc123")).isTrue();
    }

    @Test void addBot_isBot_false_for_human() {
        assertThat(AddBotCommand.isBot("PLAYER_1")).isFalse();
    }

    @Test void addBot_isBot_false_for_null() {
        assertThat(AddBotCommand.isBot(null)).isFalse();
    }

    @Test void addBot_difficultyFromId_easy() {
        assertThat(AddBotCommand.difficultyFromId("BOT_EASY_abc")).isEqualTo(BotDifficulty.EASY);
    }

    @Test void addBot_difficultyFromId_hard() {
        assertThat(AddBotCommand.difficultyFromId("BOT_HARD_abc")).isEqualTo(BotDifficulty.HARD);
    }

    @Test void addBot_difficultyFromId_invalid_fallback_to_medium() {
        assertThat(AddBotCommand.difficultyFromId("BOT_INVALID_abc")).isEqualTo(BotDifficulty.MEDIUM);
    }

    @Test void addBot_difficultyFromId_malformed_fallback_to_medium() {
        assertThat(AddBotCommand.difficultyFromId("NOTABOT")).isEqualTo(BotDifficulty.MEDIUM);
    }
}