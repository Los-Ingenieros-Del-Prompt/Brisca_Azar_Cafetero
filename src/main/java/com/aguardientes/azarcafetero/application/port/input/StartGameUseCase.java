package com.aguardientes.azarcafetero.application.port.input;

import com.aguardientes.azarcafetero.application.dto.GameStateDTO;
import com.aguardientes.azarcafetero.application.dto.StartGameCommand;

public interface StartGameUseCase {
    GameStateDTO startGame(StartGameCommand command);
}
