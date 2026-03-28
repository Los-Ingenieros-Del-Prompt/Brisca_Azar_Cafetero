package com.aguardientes.azarcafetero.application.port.input;

import com.aguardientes.azarcafetero.application.dto.GameStateDTO;
import com.aguardientes.azarcafetero.application.dto.JoinGameCommand;

public interface JoinGameUseCase {
    GameStateDTO joinGame(JoinGameCommand command);
}
