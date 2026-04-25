package com.aguardientes.azarcafetero.application.service;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.port.input.*;
import com.aguardientes.azarcafetero.application.port.output.GameEventPublisher;
import com.aguardientes.azarcafetero.application.port.output.GameRepository;
import com.aguardientes.azarcafetero.application.port.output.WalletClient;
import com.aguardientes.azarcafetero.domain.exception.GameNotFoundException;
import com.aguardientes.azarcafetero.domain.model.*;
import com.aguardientes.azarcafetero.domain.service.BriscaBotDecisionService;
import com.aguardientes.azarcafetero.domain.service.GameRules;
import com.aguardientes.azarcafetero.domain.service.ScoreCalculator;
import com.aguardientes.azarcafetero.domain.service.TrickResolver;

import java.math.BigDecimal;
import java.util.List;

public class GameService implements
        CreateGameUseCase,
        JoinGameUseCase,
        StartGameUseCase,
        PlayCardUseCase,
        GetGameStateUseCase {

    private final GameRepository gameRepository;
    private final GameEventPublisher eventPublisher;
    private final GameRules gameRules;
    private final TrickResolver trickResolver;
    private final ScoreCalculator scoreCalculator;
    private final GameMapper gameMapper;
    private final WalletClient walletClient;
    private final BriscaBotDecisionService botDecisionService;

    // Constructor existente + bot service
    public GameService(
            GameRepository gameRepository,
            GameEventPublisher eventPublisher,
            GameRules gameRules,
            TrickResolver trickResolver,
            ScoreCalculator scoreCalculator,
            GameMapper gameMapper,
            WalletClient walletClient,
            BriscaBotDecisionService botDecisionService) {
        this.gameRepository      = gameRepository;
        this.eventPublisher      = eventPublisher;
        this.gameRules           = gameRules;
        this.trickResolver       = trickResolver;
        this.scoreCalculator     = scoreCalculator;
        this.gameMapper          = gameMapper;
        this.walletClient        = walletClient;
        this.botDecisionService  = botDecisionService;
    }

    @Override
    public GameStateDTO createGame(CreateGameCommand command) {
        Game game = gameRepository.findById(command.gameId()).orElseGet(() -> {
            Game newGame = new Game(
                    command.gameId(),
                    command.minPlayers(),
                    command.maxPlayers(),
                    command.betAmount()
            );
            gameRepository.save(newGame);
            eventPublisher.publishGameCreated(newGame.getId());
            return newGame;
        });
        return gameMapper.toFullGameStateDTO(game);
    }

    @Override
    public GameStateDTO joinGame(JoinGameCommand command) {
        Game game = findGameOrThrow(command.gameId());
        synchronized (game) {
            boolean alreadyJoined = game.getPlayers().stream()
                    .anyMatch(p -> p.getId().equals(command.playerId()));
            if (!alreadyJoined) {
                Player player = new Player(command.playerId(), command.playerName());
                game.addPlayer(player);
                gameRepository.save(game);
                eventPublisher.publishPlayerJoined(game.getId(), player.getId());
            }
            eventPublisher.publishGameStateUpdated(gameMapper.toFullGameStateDTO(game));
            return gameMapper.toFullGameStateDTO(game);
        }
    }

    /**
     * Une un bot a la partida con el ID generado y un nombre acorde a su dificultad.
     * No llama al WalletClient (los bots no tienen saldo real).
     */
    public GameStateDTO addBot(AddBotCommand command) {
        Game game = findGameOrThrow(command.gameId());
        synchronized (game) {
            String botId   = AddBotCommand.generateBotId(command.difficulty());
            String botName = "Bot " + capitalize(command.difficulty().name());
            Player bot     = new Player(botId, botName);
            game.addPlayer(bot);
            gameRepository.save(game);
            eventPublisher.publishPlayerJoined(game.getId(), botId);
            eventPublisher.publishGameStateUpdated(gameMapper.toFullGameStateDTO(game));
            return gameMapper.toFullGameStateDTO(game);
        }
    }

    @Override
    public GameStateDTO startGame(StartGameCommand command) {
        Game game = findGameOrThrow(command.gameId());
        if (game.getState() != GameState.WAITING_FOR_PLAYERS) {
            return gameMapper.toFullGameStateDTO(game);
        }
        gameRules.validateGameStart(game);
        deductBetsFromHumanPlayers(game);   // Solo descuenta a humanos
        game.start();
        gameRepository.save(game);
        eventPublisher.publishGameStarted(game.getId());
        eventPublisher.publishGameStateUpdated(gameMapper.toFullGameStateDTO(game));

        // Si el primer turno es de un bot, lo disparamos automáticamente
        triggerBotTurnIfNeeded(game);

        return gameMapper.toFullGameStateDTO(game);
    }

    // ─── playCard: + auto-play del bot tras cada jugada ───────────────────────

    @Override
    public GameStateDTO playCard(PlayCardCommand command) {
        Game game = findGameOrThrow(command.gameId());
        Card card = new Card(command.suit(), command.rank());
        gameRules.validatePlayerTurn(game, command.playerId());
        gameRules.validateCardPlay(game, command.playerId(), card);

        game.playCard(command.playerId(), card);
        gameRepository.save(game);
        eventPublisher.publishCardPlayed(game.getId(), command.playerId(), card.toString());

        if (game.isTrickComplete()) {
            resolveTrick(game);
        }

        eventPublisher.publishGameStateUpdated(gameMapper.toFullGameStateDTO(game));

        // Si el siguiente turno es de un bot, lo jugamos automáticamente
        triggerBotTurnIfNeeded(game);

        return gameMapper.toGameStateDTO(game, command.playerId());
    }

    // ─── Bot auto-play ────────────────────────────────────────────────────────

    /**
     * Si el jugador activo es un bot, juega su turno.
     * Se llama de forma recursiva hasta que le toque a un humano o termine la partida.
     *
     * Límite de seguridad: máximo tantos turnos consecutivos como jugadores hay
     * (evita loops infinitos si todos son bots y algo falla).
     */
    /**
     * Delay en ms antes de que el bot juegue su carta.
     * Permite que el frontend renderice el estado intermedio
     * (carta del humano visible en la mesa) antes de que el bot responda.
     */
    private static final long BOT_PLAY_DELAY_MS = 800;

    private void triggerBotTurnIfNeeded(Game game) {
        int maxConsecutiveBotTurns = game.getPlayers().size();
        int turns = 0;

        while (turns < maxConsecutiveBotTurns
                && game.getState() == GameState.IN_PROGRESS) {

            Player currentPlayer = game.getCurrentPlayer();
            if (currentPlayer == null) break;
            if (!AddBotCommand.isBot(currentPlayer.getId())) break;

            playBotTurn(game, currentPlayer.getId());
            turns++;
        }
    }

    private void playBotTurn(Game game, String botId) {
        // Publica el estado ANTES de jugar para que el frontend
        // muestre la carta del humano en la mesa mientras el bot "piensa"
        eventPublisher.publishGameStateUpdated(gameMapper.toFullGameStateDTO(game));

        try {
            Thread.sleep(BOT_PLAY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        BotDifficulty difficulty = AddBotCommand.difficultyFromId(botId);
        Card card = botDecisionService.decide(game, botId, difficulty);

        game.playCard(botId, card);
        gameRepository.save(game);
        eventPublisher.publishCardPlayed(game.getId(), botId, card.toString());

        if (game.isTrickComplete()) {
            resolveTrick(game);
        }

        eventPublisher.publishGameStateUpdated(gameMapper.toFullGameStateDTO(game));
    }

    // ─── resolveTrick: igual que antes ───────────────────────────────────────

    private void resolveTrick(Game game) {
        String winnerId = trickResolver.determineWinner(
                game.getCurrentTrick(), game.getTrumpSuit());
        int points = game.getCurrentTrick().getTotalPoints();
        game.resolveTrick(winnerId);
        gameRepository.save(game);
        eventPublisher.publishTrickCompleted(game.getId(), winnerId, points);

        if (game.isGameOver()) {
            game.finish();
            gameRepository.save(game);
            Player winner    = game.getWinner();
            String winnnerUserId = winner != null ? winner.getId() : null;
            if (winnnerUserId != null) {
                settlePrize(game, winnnerUserId);
            }
            eventPublisher.publishGameFinished(game.getId(), winnnerUserId);
        }
    }

    // ─── Wallet: solo opera con jugadores humanos ─────────────────────────────

    private void settlePrize(Game game, String winnerUserId) {
        if (AddBotCommand.isBot(winnerUserId)) return; // bot ganó, nadie cobra

        List<Player> players  = game.getPlayers();
        BigDecimal betAmount  = game.getBetAmount();
        // Premio reducido al 50% si había bots en la partida (PBI 158)
        boolean hasBot        = players.stream().anyMatch(p -> AddBotCommand.isBot(p.getId()));
        BigDecimal multiplier = hasBot ? new BigDecimal("0.5") : BigDecimal.ONE;
        BigDecimal totalPrize = betAmount.multiply(BigDecimal.valueOf(players.size()))
                .multiply(multiplier);

        walletClient.receiveWin(winnerUserId, totalPrize);

        players.stream()
                .filter(p -> !p.getId().equals(winnerUserId))
                .filter(p -> !AddBotCommand.isBot(p.getId()))   // bots no registran pérdida
                .forEach(loser -> walletClient.registerLoss(loser.getId(), betAmount));
    }

    private void deductBetsFromHumanPlayers(Game game) {
        BigDecimal betAmount = game.getBetAmount();
        game.getPlayers().stream()
                .filter(p -> !AddBotCommand.isBot(p.getId()))
                .forEach(p -> walletClient.placeBet(p.getId(), betAmount));
    }

    // ─── getGameState: sin cambios ────────────────────────────────────────────

    @Override
    public GameStateDTO getGameState(String gameId) {
        return gameMapper.toPublicGameStateDTO(findGameOrThrow(gameId));
    }

    @Override
    public GameStateDTO getGameState(String gameId, String playerId) {
        return gameMapper.toGameStateDTO(findGameOrThrow(gameId), playerId);
    }

    @Override
    public GameStateDTO getGameStateWithAllHands(String gameId) {
        return gameMapper.toFullGameStateDTO(findGameOrThrow(gameId));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Game findGameOrThrow(String gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}