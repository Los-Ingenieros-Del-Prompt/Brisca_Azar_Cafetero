package com.aguardientes.azarcafetero.application.service;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.port.input.*;
import com.aguardientes.azarcafetero.application.port.output.GameEventPublisher;
import com.aguardientes.azarcafetero.application.port.output.GameRepository;
import com.aguardientes.azarcafetero.application.port.output.WalletClient;
import com.aguardientes.azarcafetero.domain.exception.GameNotFoundException;
import com.aguardientes.azarcafetero.domain.model.Card;
import com.aguardientes.azarcafetero.domain.model.Game;
import com.aguardientes.azarcafetero.domain.model.Player;
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

    public GameService(
            GameRepository gameRepository,
            GameEventPublisher eventPublisher,
            GameRules gameRules,
            TrickResolver trickResolver,
            ScoreCalculator scoreCalculator,
            GameMapper gameMapper,
            WalletClient walletClient) {
        this.gameRepository = gameRepository;
        this.eventPublisher = eventPublisher;
        this.gameRules = gameRules;
        this.trickResolver = trickResolver;
        this.scoreCalculator = scoreCalculator;
        this.gameMapper = gameMapper;
        this.walletClient = walletClient;
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

    @Override
    public GameStateDTO startGame(StartGameCommand command) {
        Game game = findGameOrThrow(command.gameId());
        if (game.getState() != com.aguardientes.azarcafetero.domain.model.GameState.WAITING_FOR_PLAYERS) {
            return gameMapper.toFullGameStateDTO(game);
        }
        gameRules.validateGameStart(game);

        // Descontar apuesta a cada jugador antes de iniciar
        deductBetsFromAllPlayers(game);

        game.start();
        gameRepository.save(game);
        eventPublisher.publishGameStarted(game.getId());
        eventPublisher.publishGameStateUpdated(gameMapper.toFullGameStateDTO(game));
        return gameMapper.toFullGameStateDTO(game);
    }

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
        eventPublisher.publishGameStateUpdated(gameMapper.toPublicGameStateDTO(game));
        return gameMapper.toGameStateDTO(game, command.playerId());
    }

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
            Player winner = game.getWinner();
            String winnerUserId = winner != null ? winner.getId() : null;

            // El ganador se lleva el pozo; los perdedores registran su pérdida
            if (winnerUserId != null) {
                settlePrize(game, winnerUserId);
            }
            eventPublisher.publishGameFinished(game.getId(), winnerUserId);
        }
    }

    private void settlePrize(Game game, String winnerUserId) {
        List<Player> players = game.getPlayers();
        BigDecimal betAmount = game.getBetAmount();
        BigDecimal totalPrize = betAmount.multiply(BigDecimal.valueOf(players.size()));

        walletClient.receiveWin(winnerUserId, totalPrize);

        players.stream()
                .filter(p -> !p.getId().equals(winnerUserId))
                .forEach(loser -> walletClient.registerLoss(loser.getId(), betAmount));
    }

    private void deductBetsFromAllPlayers(Game game) {
        BigDecimal betAmount = game.getBetAmount();
        for (Player player : game.getPlayers()) {
            walletClient.placeBet(player.getId(), betAmount);
        }
    }

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

    private Game findGameOrThrow(String gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
    }
}