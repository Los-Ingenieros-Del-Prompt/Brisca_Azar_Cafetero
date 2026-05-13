package com.aguardientes.azarcafetero.infrastructure.wallet;

import com.aguardientes.azarcafetero.application.port.output.WalletClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Implementación vacía de WalletClient.
 * Usada mientras azar-wallet no está desplegado.
 * Todas las operaciones se loguean y se ignoran sin lanzar excepciones.
 */
public class NoOpWalletClient implements WalletClient {

    private static final Logger log = LoggerFactory.getLogger(NoOpWalletClient.class);

    @Override
    public void placeBet(String userId, BigDecimal amount) {
        log.info("[NoOp] placeBet ignorado - userId={} amount={}", userId, amount);
    }

    @Override
    public void receiveWin(String userId, BigDecimal amount) {
        log.info("[NoOp] receiveWin ignorado - userId={} amount={}", userId, amount);
    }

    @Override
    public void registerLoss(String userId, BigDecimal amount) {
        log.info("[NoOp] registerLoss ignorado - userId={} amount={}", userId, amount);
    }
}