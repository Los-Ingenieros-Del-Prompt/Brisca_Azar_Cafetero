package com.aguardientes.azarcafetero.application.port.input;

import com.aguardientes.azarcafetero.application.dto.GameStateDTO;
import com.aguardientes.azarcafetero.application.dto.PlayCardCommand;

public interface PlayCardUseCase {
    GameStateDTO playCard(PlayCardCommand command);
}
