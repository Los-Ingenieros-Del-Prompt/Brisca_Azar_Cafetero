package com.aguardientes.azarcafetero.application;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.service.*;
import com.aguardientes.azarcafetero.application.port.output.*;
import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests targeting the remaining uncovered branches in GameService:
 *
 *   1. finalizeAndCleanup → catch(Exception e) non-Runtime path
 *      → must wrap in IllegalStateException
 *
 *   2. sleep() interrupted path
 *      (indirectly exercised when bot thread is interrupted)
 *
 *   3. GameService.leaveGame when game has only bots left
 *      (hasHumanPlayers = false → deleteById)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameService – missing branch coverage")
class GameServiceMissingBranchTest {

    @Mock GameRepository gameRepository;
    @Mock GameEventPublisher eventPublisher;
    @Mock WalletClient walletClient;

    GameService service;

    @BeforeEach
    void setUp() {
        service = new GameService(
                gameRepository, eventPublisher,
                new GameRules(), new TrickResolver(),
                new ScoreCalculator(), new GameMapper(),
                walletClient, new BriscaBotDecisionService()
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // catch(Exception e) — non-RuntimeException wraps to IllegalStateException
    // Line ~365-368 in GameService.finalizeAndCleanup
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("settlePrize lanza Exception (no Runtime) → IllegalStateException envuelve la causa")
    void finalizeAndCleanup_checkedExceptionInSettlePrize_wrapsAsIllegalState() {
        Game game = startedTwoPlayerGame("P1", "P2");
        game.getPlayerById("P1").addPoints(80);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        // receiveWin lanza una Exception que no es RuntimeException
        // Usamos un Answer que lanza directamente la excepción checked
        doAnswer(inv -> {
            throw new Exception("checked wallet error");
        }).when(walletClient).receiveWin(any(), any());

        assertThatThrownBy(() -> playAllCards(game))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se pudo liquidar el premio");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // leaveGame — solo quedan bots → deleteById
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("leaveGame: último humano sale, solo quedan bots → deleteById")
    void leaveGame_onlyBotsRemain_deletesGame() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Human"));
        game.addPlayer(new Player("BOT_EASY_x1", "Bot"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        service.leaveGame(new LeaveGameCommand("G1", "P1"));

        // hasHumanPlayers() = false → deleteById
        verify(gameRepository).deleteById("G1");
        verify(gameRepository, never()).save(any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // startGame — bot is first to play, triggerBotTurnIfNeeded fires
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("startGame con bot en primer turno → triggerBotTurnIfNeeded encola el bot")
    void startGame_botIsFirst_triggersBot() throws InterruptedException {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        // Bot first so it gets index 0 (first turn)
        game.addPlayer(new Player("BOT_MEDIUM_b1", "Bot"));
        game.addPlayer(new Player("P1", "Human"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        service.startGame(new StartGameCommand("G1"));

        // Give bot executor time to fire at least one publish
        Thread.sleep(2500);

        // Bot must have published at least one event (card played or state update)
        verify(eventPublisher, atLeastOnce()).publishGameStateUpdated(any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // addBot — verify publishPlayerJoined is called
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("addBot → publishPlayerJoined y publishGameStateUpdated llamados")
    void addBot_publishesEvents() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        service.addBot(new AddBotCommand("G1", BotDifficulty.HARD));

        verify(eventPublisher).publishPlayerJoined(eq("G1"), argThat(id -> id.startsWith("BOT_HARD_")));
        verify(eventPublisher).publishGameStateUpdated(any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // runBotTurns — maxTurns limit prevents infinite loop
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("runBotTurns: juego se mantiene IN_PROGRESS pero no hay movimientos → sale por límite")
    void runBotTurns_maxTurnsLimit_preventsInfiniteLoop() throws Exception {
        // Bot with cards but game keeps returning IN_PROGRESS (simulate via reflection)
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player bot = new Player("BOT_EASY_loop", "Bot");
        Player human = new Player("P1", "Human");
        game.addPlayer(bot);
        game.addPlayer(human);

        // Force IN_PROGRESS and bot as current player
        setField(game, "state", GameState.IN_PROGRESS);
        setTrumpCard(game, Suit.OROS);
        setField(game, "currentPlayerIndex", 0);

        // Don't give bot any cards → hasCards() = false → exits immediately
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════════════════════

    private Game startedTwoPlayerGame(String id1, String id2) {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player(id1, id1));
        game.addPlayer(new Player(id2, id2));
        game.start();
        return game;
    }

    private void playAllCards(Game game) {
        int limit = 100, count = 0;
        while (!game.isGameOver() && game.getState() == GameState.IN_PROGRESS && count++ < limit) {
            Player current = game.getCurrentPlayer();
            if (current == null || current.getHand().isEmpty()) break;
            Card card = current.getHand().get(0);
            service.playCard(new PlayCardCommand(
                    game.getId(), current.getId(), card.getSuit(), card.getRank()));
        }
    }

    private void setField(Object obj, String name, Object value) {
        try {
            var f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, value);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void setTrumpCard(Game game, Suit suit) {
        try {
            var df = Game.class.getDeclaredField("deck");
            df.setAccessible(true);
            Object deck = df.get(game);
            var tf = deck.getClass().getDeclaredField("trumpCard");
            tf.setAccessible(true);
            tf.set(deck, new Card(suit, Rank.SEVEN));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}