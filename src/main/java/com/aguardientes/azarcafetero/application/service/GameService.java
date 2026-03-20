package com.aguardientes.azarcafetero.application.service;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.application.port.input.*;
import com.aguardientes.azarcafetero.application.port.output.GameEventPublisher;
import com.aguardientes.azarcafetero.application.port.output.GameRepository;
import com.aguardientes.azarcafetero.domain.exception.GameNotFoundException;
import com.aguardientes.azarcafetero.domain.model.Card;
import com.aguardientes.azarcafetero.domain.model.Game;
import com.aguardientes.azarcafetero.domain.model.Player;
import com.aguardientes.azarcafetero.domain.service.GameRules;
import com.aguardientes.azarcafetero.domain.service.ScoreCalculator;
import com.aguardientes.azarcafetero.domain.service.TrickResolver;

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

    public GameService(
            GameRepository gameRepository,
            GameEventPublisher eventPublisher,
            GameRules gameRules,
            TrickResolver trickResolver,
            ScoreCalculator scoreCalculator,
            GameMapper gameMapper) {
        this.gameRepository = gameRepository;
        this.eventPublisher = eventPublisher;
        this.gameRules = gameRules;
        this.trickResolver = trickResolver;
        this.scoreCalculator = scoreCalculator;
        this.gameMapper = gameMapper;
    }

    @Override
    public GameStateDTO createGame(CreateGameCommand command) {
        if (gameRepository.exists(command.gameId())) {
            throw new IllegalArgumentException("Game already exists: " + command.gameId());
        }

        Game game = new Game(command.gameId(), command.minPlayers(), command.maxPlayers());
        gameRepository.save(game);

        eventPublisher.publishGameCreated(game.getId());

        return gameMapper.toPublicGameStateDTO(game);
    }

    @Override
    public GameStateDTO joinGame(JoinGameCommand command) {
        Game game = findGameOrThrow(command.gameId());

        Player player = new Player(command.playerId(), command.playerName());
        game.addPlayer(player);

        gameRepository.save(game);

        eventPublisher.publishPlayerJoined(game.getId(), player.getId());
        eventPublisher.publishGameStateUpdated(gameMapper.toPublicGameStateDTO(game));

        return gameMapper.toGameStateDTO(game, command.playerId());
    }

    @Override
    public GameStateDTO startGame(StartGameCommand command) {
        Game game = findGameOrThrow(command.gameId());

        gameRules.validateGameStart(game);
        game.start();

        gameRepository.save(game);

        eventPublisher.publishGameStarted(game.getId());
        eventPublisher.publishGameStateUpdated(gameMapper.toPublicGameStateDTO(game));

        return gameMapper.toPublicGameStateDTO(game);
    }

    @Override
    public GameStateDTO playCard(PlayCardCommand command) {
        Game game = findGameOrThrow(command.gameId());

        Card card = new Card(command.suit(), command.rank());

        gameRules.validatePlayerTurn(game, command.playerId());
        gameRules.validateCardPlay(game, command.playerId(), card);

        game.playCard(command.playerId(), card);
        gameRepository.save(game);

        eventPublisher.publishCardPlayed(
                game.getId(),
                command.playerId(),
                card.toString()
        );

        if (game.isTrickComplete()) {
            resolveTrick(game);
        }

        eventPublisher.publishGameStateUpdated(gameMapper.toPublicGameStateDTO(game));

        return gameMapper.toGameStateDTO(game, command.playerId());
    }

    private void resolveTrick(Game game) {
        String winnerId = trickResolver.determineWinner(
                game.getCurrentTrick(),
                game.getTrumpSuit()
        );

        int points = game.getCurrentTrick().getTotalPoints();
        game.resolveTrick(winnerId);

        gameRepository.save(game);

        eventPublisher.publishTrickCompleted(game.getId(), winnerId, points);

        if (game.isGameOver()) {
            game.finish();
            gameRepository.save(game);

            Player winner = game.getWinner();
            eventPublisher.publishGameFinished(
                    game.getId(),
                    winner != null ? winner.getId() : null
            );
        }
    }

    @Override
    public GameStateDTO getGameState(String gameId) {
        Game game = findGameOrThrow(gameId);
        return gameMapper.toPublicGameStateDTO(game);
    }

    private Game findGameOrThrow(String gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
    }
}
