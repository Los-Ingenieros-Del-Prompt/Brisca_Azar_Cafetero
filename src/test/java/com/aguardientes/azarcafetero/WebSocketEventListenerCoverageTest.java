package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.application.dto.LeaveGameCommand;
import com.aguardientes.azarcafetero.application.service.GameService;
import com.aguardientes.azarcafetero.infrastructure.websocket.WebSocketEventListener;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketEventListener – cobertura adicional")
class WebSocketEventListenerCoverageTest {

    @Mock GameService gameService;

    WebSocketEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new WebSocketEventListener(gameService);
    }

    @Test
    @DisplayName("constructor con null gameService lanza NPE")
    void constructor_null_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> new WebSocketEventListener(null));
    }

    @Test
    @DisplayName("disconnect sin sessionAttributes (null) no llama leaveGame")
    void handleDisconnect_nullSessionAttributes_doesNothing() {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getMessage()).thenReturn(buildMessage(null));

        listener.handleWebSocketDisconnectListener(event);

        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("disconnect con sesión sin playerId no llama leaveGame")
    void handleDisconnect_noPlayerId_doesNothing() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("gameId", "G1");

        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getMessage()).thenReturn(buildMessage(attrs));

        listener.handleWebSocketDisconnectListener(event);

        verifyNoInteractions(gameService);
    }

    @Test
    @DisplayName("disconnect con playerId y gameId llama leaveGame")
    void handleDisconnect_withPlayerAndGame_callsLeaveGame() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("playerId", "P1");
        attrs.put("playerName", "Alice");
        attrs.put("gameId", "G1");

        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getMessage()).thenReturn(buildMessage(attrs));

        listener.handleWebSocketDisconnectListener(event);

        verify(gameService).leaveGame(any(LeaveGameCommand.class));
    }

    @Test
    @DisplayName("disconnect con playerId pero sin gameId no llama leaveGame")
    void handleDisconnect_noGameId_doesNotCallLeaveGame() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("playerId", "P1");
        // sin gameId

        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getMessage()).thenReturn(buildMessage(attrs));

        listener.handleWebSocketDisconnectListener(event);

        verifyNoInteractions(gameService);
    }

    @SuppressWarnings("unchecked")
    private Message<byte[]> buildMessage(Map<String, Object> sessionAttrs) {
        // Usamos el builder de Spring en lugar de Mockito para el mensaje
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT);

        if (sessionAttrs != null) {
            accessor.setSessionAttributes(sessionAttrs);
        }

        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}