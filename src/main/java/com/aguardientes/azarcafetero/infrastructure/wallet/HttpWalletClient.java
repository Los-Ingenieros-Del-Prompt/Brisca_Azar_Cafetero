package com.aguardientes.azarcafetero.infrastructure.wallet;

import com.aguardientes.azarcafetero.application.port.output.WalletClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

public class HttpWalletClient implements WalletClient {

    private static final Logger log = LoggerFactory.getLogger(HttpWalletClient.class);

    private final RestClient restClient;
    private final String internalApiKey;

    public HttpWalletClient(String walletServiceUrl, String internalApiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(walletServiceUrl)
                .build();
        this.internalApiKey = internalApiKey;
    }

    @Override
    public void placeBet(String userId, BigDecimal amount) {
        log.info("Wallet → placeBet userId={} amount={}", userId, amount);
        try {
            restClient.post()
                    .uri("/player/bet")
                    .header("X-Internal-Key", internalApiKey)
                    .body(Map.of("userId", userId, "amount", amount))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Error al registrar apuesta para userId={}: {}", userId, e.getMessage());
            throw new WalletOperationException(
                    "No se pudo registrar la apuesta del jugador " + userId, e);
        }
    }

    @Override
    public void receiveWin(String userId, BigDecimal amount) {
        log.info("Wallet → receiveWin userId={} amount={}", userId, amount);
        try {
            restClient.post()
                    .uri("/player/win")
                    .header("X-Internal-Key", internalApiKey)
                    .body(Map.of("userId", userId, "amount", amount))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("Error al acreditar premio para userId={}: {}", userId, e.getMessage());
            throw new WalletOperationException(
                    "No se pudo acreditar el premio al ganador " + userId, e);
        }
    }

    @Override
    public void registerLoss(String userId, BigDecimal amount) {
        log.info("Wallet → registerLoss userId={} amount={}", userId, amount);
        try {
            restClient.post()
                    .uri("/player/loss")
                    .header("X-Internal-Key", internalApiKey)
                    .body(Map.of("userId", userId, "amount", amount))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("No se pudo registrar la pérdida para userId={}: {}", userId, e.getMessage());
        }
    }
}