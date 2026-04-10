package com.aguardientes.azarcafetero.infrastructure.config;

import com.aguardientes.azarcafetero.application.port.output.GameEventPublisher;
import com.aguardientes.azarcafetero.application.port.output.GameRepository;
import com.aguardientes.azarcafetero.application.port.output.WalletClient;
import com.aguardientes.azarcafetero.application.service.GameMapper;
import com.aguardientes.azarcafetero.application.service.GameService;
import com.aguardientes.azarcafetero.domain.service.GameRules;
import com.aguardientes.azarcafetero.domain.service.ScoreCalculator;
import com.aguardientes.azarcafetero.domain.service.TrickResolver;
import com.aguardientes.azarcafetero.infrastructure.wallet.HttpWalletClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BriscaApplicationConfig {

    @Bean
    public GameRules gameRules() { return new GameRules(); }

    @Bean
    public TrickResolver trickResolver() { return new TrickResolver(); }

    @Bean
    public ScoreCalculator scoreCalculator() { return new ScoreCalculator(); }

    @Bean
    public GameMapper gameMapper() { return new GameMapper(); }

    @Bean
    public WalletClient walletClient(
            @Value("${wallet.service.url}") String walletServiceUrl,
            @Value("${internal.api.key}") String internalApiKey) {
        return new HttpWalletClient(walletServiceUrl, internalApiKey);
    }

    @Bean
    public GameService gameService(
            GameRepository gameRepository,
            GameEventPublisher eventPublisher,
            GameRules gameRules,
            TrickResolver trickResolver,
            ScoreCalculator scoreCalculator,
            GameMapper gameMapper,
            WalletClient walletClient) {
        return new GameService(
                gameRepository,
                eventPublisher,
                gameRules,
                trickResolver,
                scoreCalculator,
                gameMapper,
                walletClient
        );
    }
}