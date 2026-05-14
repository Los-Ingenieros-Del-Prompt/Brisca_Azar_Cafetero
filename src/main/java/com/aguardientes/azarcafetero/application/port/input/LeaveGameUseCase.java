package com.aguardientes.azarcafetero.application.port.input;

import com.aguardientes.azarcafetero.application.dto.GameStateDTO;
import com.aguardientes.azarcafetero.application.dto.LeaveGameCommand;

public interface LeaveGameUseCase {
    GameStateDTO leaveGame(LeaveGameCommand command);
}
