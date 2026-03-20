package com.aguardientes.azarcafetero.infrastructure.config;

import com.aguardientes.azarcafetero.application.port.output.GameEventPublisher;
import com.aguardientes.azarcafetero.application.port.output.GameRepository;
import com.aguardientes.azarcafetero.application.service.GameMapper;
import com.aguardientes.azarcafetero.application.service.GameService;
import com.aguardientes.azarcafetero.domain.service.GameRules;
import com.aguardientes.azarcafetero.domain.service.ScoreCalculator;
import com.aguardientes.azarcafetero.domain.service.TrickResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BriscaApplicationConfig {

    @Bean
    public GameRules gameRules() {
        return new GameRules();
    }

    @Bean
    public TrickResolver trickResolver() {
        return new TrickResolver();
    }

    @Bean
    public ScoreCalculator scoreCalculator() {
        return new ScoreCalculator();
    }

    @Bean
    public GameMapper gameMapper() {
        return new GameMapper();
    }

    @Bean
    public GameService gameService(
            GameRepository gameRepository,
            GameEventPublisher eventPublisher,
            GameRules gameRules,
            TrickResolver trickResolver,
            ScoreCalculator scoreCalculator,
            GameMapper gameMapper) {
        return new GameService(
                gameRepository,
                eventPublisher,
                gameRules,
                trickResolver,
                scoreCalculator,
                gameMapper
        );
    }
}
