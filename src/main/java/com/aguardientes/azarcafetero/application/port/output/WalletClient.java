package com.aguardientes.azarcafetero.application.port.output;

import java.math.BigDecimal;

public interface WalletClient {
    void placeBet(String userId, BigDecimal amount);
    void receiveWin(String userId, BigDecimal amount);
    void registerLoss(String userId, BigDecimal amount);
}