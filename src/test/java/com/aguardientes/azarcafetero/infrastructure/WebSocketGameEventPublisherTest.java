package com.aguardientes.azarcafetero.infrastructure;

import com.aguardientes.azarcafetero.application.dto.*;
import com.aguardientes.azarcafetero.domain.model.GameState;
import com.aguardientes.azarcafetero.infrastructure.messaging.WebSocketGameEventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketGameEventPublisher")
class WebSocketGameEventPublisherTest {

    @Mock SimpMessagingTemplate messagingTemplate;
    WebSocketGameEventPublisher publisher;

    @BeforeEach void setUp() {
        publisher = new WebSocketGameEventPublisher(messagingTemplate);
    }

    @Test void constructor_nullTemplate_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> new WebSocketGameEventPublisher(null));
    }

    @Test void publishGameCreated_sendsToEventsChannel() {
        publisher.publishGameCreated("G1");
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1/events"), (Object) any());
    }

    @Test void publishPlayerJoined_sendsToEventsChannel() {
        publisher.publishPlayerJoined("G1", "P1");
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1/events"), (Object) any());
    }

    @Test void publishGameStarted_sendsToEventsChannel() {
        publisher.publishGameStarted("G1");
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1/events"), (Object) any());
    }

    @Test void publishCardPlayed_sendsToEventsChannel() {
        publisher.publishCardPlayed("G1", "P1", "As de Oros");
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1/events"), (Object) any());
    }

    @Test void publishTrickCompleted_sendsToEventsChannel() {
        publisher.publishTrickCompleted("G1", "P1", 11);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1/events"), (Object) any());
    }

    @Test void publishGameStateUpdated_sendsToGameChannel() {
        GameStateDTO state = new GameStateDTO("G1", GameState.IN_PROGRESS,
                List.of(), null, null, null, null, 0, List.of());
        publisher.publishGameStateUpdated(state);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1"), eq(state));
    }

    @Test void publishGameFinished_sendsWinnerIds() {
        publisher.publishGameFinished("G1", List.of("P1"));
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1/events"), (Object) any());
    }

    @Test void publishGameFinished_noWinners_sendsDraw() {
        publisher.publishGameFinished("G1", List.of());
        verify(messagingTemplate).convertAndSend(eq("/topic/game/G1/events"), (Object) any());
    }
}