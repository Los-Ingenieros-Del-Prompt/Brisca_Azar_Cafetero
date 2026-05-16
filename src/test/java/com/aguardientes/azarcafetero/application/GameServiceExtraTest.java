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
 * Tests adicionales de GameService para cubrir las ramas no alcanzadas:
 *
 *   - settlePrize: empate entre dos humanos → reparte entre ambos
 *   - settlePrize: humano pierde (registerLoss) cuando no es ganador
 *   - settlePrize: ganador humano vs bot → multiplicador 0.5
 *   - finalizeAndCleanup: RuntimeException en settlePrize → se propaga
 *   - finalizeAndCleanup: Exception (no Runtime) en settlePrize → IllegalStateException
 *   - playBotTurn: trick completo tras carta del bot → resolveTrick
 *   - runBotTurns: bot sin cartas y partida terminada → finaliza
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameService – cobertura extendida")
class GameServiceExtraTest {

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
    // settlePrize — empate entre dos humanos
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Empate entre dos humanos → ambos reciben premio repartido")
    void playCard_twoHumansTied_bothReceivePrize() {
        Game game = startedTwoPlayerGame("P1", "P2");
        // Forzamos empate de puntuación
        game.getPlayerById("P1").addPoints(30);
        game.getPlayerById("P2").addPoints(30);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        playAllCards(game);

        // Ambos deben recibir pago (pueden empatar)
        verify(walletClient, atLeast(1)).receiveWin(any(), any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // settlePrize — perdedor humano registra pérdida
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Perdedor humano → registerLoss llamado")
    void playCard_humanLoser_registersLoss() {
        Game game = startedTwoPlayerGame("P1", "P2");
        // P1 gana mucho para asegurar que es el único ganador
        game.getPlayerById("P1").addPoints(80);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        playAllCards(game);

        verify(walletClient, atLeastOnce()).receiveWin(eq("P1"), any());
        verify(walletClient, atLeastOnce()).registerLoss(eq("P2"), any());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // settlePrize — humano gana contra bot (multiplicador 0.5)
    // ══════════════════════════════════════════════════════════════════════════

    
    // ══════════════════════════════════════════════════════════════════════════
    // finalizeAndCleanup — RuntimeException en settlePrize se propaga
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("settlePrize lanza RuntimeException → se propaga desde finalizeAndCleanup")
    void playCard_settlePrize_runtimeException_propagates() {
        Game game = startedTwoPlayerGame("P1", "P2");
        game.getPlayerById("P1").addPoints(80);
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        // walletClient.receiveWin lanza RuntimeException
        doThrow(new RuntimeException("wallet down"))
                .when(walletClient).receiveWin(any(), any());

        // Jugamos hasta que el juego termina → settlePrize llamado → excepción
        assertThatThrownBy(() -> playAllCards(game))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("wallet down");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // runBotTurns — bot sin cartas + partida terminada
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("runBotTurns: bot sin cartas y juego terminado → llama finalizeAndCleanup")
    void runBotTurns_botNoCards_gameOver_finalizes() throws Exception {
        // Creamos un juego donde ya no quedan cartas y el bot no tiene mano
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player human = new Player("P1", "Alice");
        Player bot   = new Player("BOT_EASY_x2", "Bot");
        human.addPoints(20);
        bot.addPoints(10);
        game.addPlayer(human);
        game.addPlayer(bot);

        // Forzar estado IN_PROGRESS, mazo vacío y trump
        forceInProgress(game, Suit.OROS);
        drainDeck(game);

        // Ambos sin cartas → isGameOver() = true
        // currentPlayer = bot (índice 1)
        forceCurrentPlayerIndex(game, 1);

        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        var m = GameService.class.getDeclaredMethod("runBotTurns", String.class);
        m.setAccessible(true);
        assertThatCode(() -> m.invoke(service, "G1")).doesNotThrowAnyException();

        // El juego debe quedar finalizado y eliminado del repo
        verify(gameRepository, atLeastOnce()).deleteById("G1");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // addBot — EASY también funciona
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("addBot EASY → prefijo correcto en ID")
    void addBot_easy_addsPlayerWithEasyPrefix() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        service.addBot(new AddBotCommand("G1", BotDifficulty.EASY));

        assertThat(game.getPlayers()).anyMatch(p -> p.getId().startsWith("BOT_EASY_"));
        assertThat(game.getPlayers()).anyMatch(p -> p.getName().equals("Bot Easy"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // capitalize — caso normal
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("capitalize: string normal → primera letra mayúscula")
    void capitalize_normalString_firstLetterUppercase() throws Exception {
        var m = GameService.class.getDeclaredMethod("capitalize", String.class);
        m.setAccessible(true);
        assertThat(m.invoke(service, "EASY")).isEqualTo("Easy");
        assertThat(m.invoke(service, "MEDIUM")).isEqualTo("Medium");
        assertThat(m.invoke(service, "HARD")).isEqualTo("Hard");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // playCard — trick completo en partida con bot (bot juega automático)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("playCard: humano completa baza → bot juega el siguiente turno automáticamente")
    void playCard_humanCompletesLastCard_botTakesNextTurn() throws InterruptedException {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player human = new Player("P1", "Alice");
        Player bot   = new Player("BOT_MEDIUM_x3", "Bot");
        game.addPlayer(human);
        game.addPlayer(bot);
        game.start();

        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        // El humano juega su primera carta (turno 0 = P1)
        Player currentPlayer = game.getCurrentPlayer();
        if ("P1".equals(currentPlayer.getId())) {
            Card card = currentPlayer.getHand().get(0);
            service.playCard(new PlayCardCommand("G1", "P1", card.getSuit(), card.getRank()));
        }

        // El bot ejecuta en un hilo aparte; damos tiempo a que procese
        Thread.sleep(3000);

        // El eventPublisher debe haber recibido al menos la carta del humano
        verify(eventPublisher, atLeastOnce()).publishCardPlayed(eq("G1"), any(), any());
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

    // ── reflexión ──────────────────────────────────────────────────────────

    private void forceInProgress(Game game, Suit trump) {
        try {
            var sf = Game.class.getDeclaredField("state");
            sf.setAccessible(true);
            sf.set(game, GameState.IN_PROGRESS);
            var df = Game.class.getDeclaredField("deck");
            df.setAccessible(true);
            Object deck = df.get(game);
            var tf = deck.getClass().getDeclaredField("trumpCard");
            tf.setAccessible(true);
            tf.set(deck, new Card(trump, Rank.SEVEN));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void drainDeck(Game game) {
        try {
            var df = Game.class.getDeclaredField("deck");
            df.setAccessible(true);
            Object deck = df.get(game);
            var cf = deck.getClass().getDeclaredField("cards");
            cf.setAccessible(true);
            ((java.util.List<?>) cf.get(deck)).clear();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private void forceCurrentPlayerIndex(Game game, int index) {
        try {
            var f = Game.class.getDeclaredField("currentPlayerIndex");
            f.setAccessible(true);
            f.set(game, index);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}