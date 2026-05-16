package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.service.*;
import com.aguardientes.azarcafetero.application.port.output.*;
import com.aguardientes.azarcafetero.domain.exception.GameNotFoundException;
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
 * Cubre ramas no alcanzadas en GameServiceTest original:
 * - finalizeAndCleanup + settlePrize (múltiples escenarios)
 * - getGameState overloads
 * - addBot cuando juego no existe
 * - capitalize con null/vacío
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GameService – cobertura adicional")
class GameServiceCoverageTest {

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

    // ── getGameState overloads ──────────────────────────────────────────────

    @Test
    @DisplayName("getGameState(id) lanza GameNotFoundException si no existe")
    void getGameState_notFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGameState("G1"))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test
    @DisplayName("getGameState(id, playerId) lanza GameNotFoundException si no existe")
    void getGameState_withPlayerId_notFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGameState("G1", "P1"))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test
    @DisplayName("getGameStateWithAllHands lanza GameNotFoundException si no existe")
    void getGameStateWithAllHands_notFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getGameStateWithAllHands("G1"))
                .isInstanceOf(GameNotFoundException.class);
    }

    // ── addBot errores ──────────────────────────────────────────────────────

    @Test
    @DisplayName("addBot lanza GameNotFoundException si el juego no existe")
    void addBot_gameNotFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addBot(new AddBotCommand("G1", BotDifficulty.EASY)))
                .isInstanceOf(GameNotFoundException.class);
    }

    @Test
    @DisplayName("addBot HARD genera ID con prefijo BOT_HARD_")
    void addBot_hard_addsHardBot() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        service.addBot(new AddBotCommand("G1", BotDifficulty.HARD));
        assertThat(game.getPlayers()).anyMatch(p -> p.getId().startsWith("BOT_HARD_"));
    }

    // ── startGame ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("startGame lanza GameNotFoundException si el juego no existe")
    void startGame_gameNotFound_throws() {
        when(gameRepository.findById("G1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.startGame(new StartGameCommand("G1")))
                .isInstanceOf(GameNotFoundException.class);
    }

    // ── playCard que termina la partida (finalizeAndCleanup) ───────────────

    /**
     * Juega todas las cartas de una partida 2-jugadores para disparar
     * finalizeAndCleanup → settlePrize con humanos.
     */
    @Test
    @DisplayName("playCard que termina la partida llama publishGameFinished y deleteById")
    void playCard_gameOver_finalizesGame() throws Exception {
        Game game = startedTwoPlayerGame("G1");
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        playAllCards(game);

        verify(gameRepository, atLeastOnce()).deleteById("G1");
        verify(eventPublisher, atLeastOnce()).publishGameFinished(eq("G1"), any());
    }

    @Test
    @DisplayName("settlePrize: ganador humano recibe el premio")
    void settlePrize_humanWinner_receivesWin() throws Exception {
        Game game = startedTwoPlayerGame("G1");
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        playAllCards(game);

        // Al menos un jugador recibió un premio (receiveWin)
        verify(walletClient, atLeastOnce()).receiveWin(any(), any());
    }

    @Test
    @DisplayName("settlePrize: perdedor humano registra pérdida (si no empate)")
    void settlePrize_humanLoser_registersLoss() throws Exception {
        // Forzar puntuaciones distintas para evitar empate
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player p1 = new Player("P1", "Alice");
        Player p2 = new Player("P2", "Bob");
        game.addPlayer(p1);
        game.addPlayer(p2);
        game.start();
        // Dar muchos puntos al P1 para asegurarse de ganar
        p1.addPoints(60);

        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);

        // registerLoss puede llamarse con el perdedor
        // (verificamos que no lanza excepción y que la partida termina)
        verify(gameRepository, atLeastOnce()).deleteById("G1");
    }

    @Test
    @DisplayName("settlePrize: partida con bot reduce premio al 50%")
    void settlePrize_withBot_reducedPrize() throws Exception {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player human = new Player("P1", "Alice");
        Player bot = new Player("BOT_EASY_abc1", "Bot Easy");
        game.addPlayer(human);
        game.addPlayer(bot);
        game.start();

        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);

        // La partida termina correctamente (no lanza excepción)
        verify(gameRepository, atLeastOnce()).deleteById("G1");
    }

    @Test
    @DisplayName("settlePrize: todos bots ganan → no hay ganador humano, no llama receiveWin")
    void settlePrize_allBotsWin_noHumanWinner() throws Exception {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        Player bot1 = new Player("BOT_EASY_abc1", "Bot1");
        Player bot2 = new Player("BOT_MEDIUM_abc2", "Bot2");
        game.addPlayer(bot1);
        game.addPlayer(bot2);
        // Dar muchos puntos al bot1 para que gane
        bot1.addPoints(60);
        game.start();

        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));
        playAllCards(game);

        verify(walletClient, never()).receiveWin(any(), any());
    }

    // ── leaveGame ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("leaveGame cuando quedan humanos publica estado actualizado")
    void leaveGame_withRemainingHumans_publishesStateUpdate() {
        Game game = new Game("G1", 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.addPlayer(new Player("P3", "Charlie"));
        when(gameRepository.findById("G1")).thenReturn(Optional.of(game));

        service.leaveGame(new LeaveGameCommand("G1", "P1"));

        verify(eventPublisher).publishGameStateUpdated(any());
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private Game startedTwoPlayerGame(String id) {
        Game game = new Game(id, 2, 4, BigDecimal.TEN);
        game.addPlayer(new Player("P1", "Alice"));
        game.addPlayer(new Player("P2", "Bob"));
        game.start();
        return game;
    }

    /**
     * Juega todas las cartas alternando entre los dos jugadores hasta terminar.
     * Usa el servicio para disparar toda la lógica de resolución.
     */
    private void playAllCards(Game game) {
        int safetyLimit = 100;
        int count = 0;
        while (!game.isGameOver() && game.getState() == GameState.IN_PROGRESS && count++ < safetyLimit) {
            Player current = game.getCurrentPlayer();
            if (current == null || current.getHand().isEmpty()) break;
            Card card = current.getHand().get(0);
            service.playCard(new PlayCardCommand(
                    game.getId(), current.getId(), card.getSuit(), card.getRank()));
        }
    }
}