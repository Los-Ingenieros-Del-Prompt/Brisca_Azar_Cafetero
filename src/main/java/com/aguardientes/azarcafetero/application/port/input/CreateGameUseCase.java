package com.aguardientes.azarcafetero.application.port.input;

import com.aguardientes.azarcafetero.application.dto.CreateGameCommand;
import com.aguardientes.azarcafetero.application.dto.GameStateDTO;

public interface CreateGameUseCase {
    GameStateDTO createGame(CreateGameCommand command);
}
