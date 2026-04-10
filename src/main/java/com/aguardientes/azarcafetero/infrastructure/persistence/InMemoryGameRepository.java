package com.aguardientes.azarcafetero.infrastructure.persistence;

import com.aguardientes.azarcafetero.application.port.output.GameRepository;
import com.aguardientes.azarcafetero.domain.model.Game;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryGameRepository implements GameRepository {

    private final ConcurrentHashMap<String, Game> games = new ConcurrentHashMap<>();

    @Override
    public void save(Game game) {
        if (game == null) {
            throw new IllegalArgumentException("Game cannot be null");
        }
        games.put(game.getId(), game);
    }

    @Override
    public Optional<Game> findById(String gameId) {
        return Optional.ofNullable(games.get(gameId));
    }

    @Override
    public void delete(String gameId) {
        games.remove(gameId);
    }

    @Override
    public boolean exists(String gameId) {
        return games.containsKey(gameId);
    }

    public Game getOrCreate(String gameId, int minPlayers, int maxPlayers) {
        return games.computeIfAbsent(gameId, id ->
                new Game(id, minPlayers, maxPlayers, java.math.BigDecimal.ZERO));
    }
}