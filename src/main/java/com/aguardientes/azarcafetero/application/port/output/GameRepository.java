package com.aguardientes.azarcafetero.application.port.output;

import com.aguardientes.azarcafetero.domain.model.Game;

import java.util.Optional;

public interface GameRepository {
    
    void save(Game game);
    
    Optional<Game> findById(String gameId);
    
    void delete(String gameId);
    
    boolean exists(String gameId);
}
