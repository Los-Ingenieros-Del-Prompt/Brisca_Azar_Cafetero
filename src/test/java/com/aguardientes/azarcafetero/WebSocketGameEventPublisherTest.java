package com.aguardientes.azarcafetero;

import com.aguardientes.azarcafetero.application.dto.LeaveGameCommand;
import com.aguardientes.azarcafetero.application.service.GameService;
import com.aguardientes.azarcafetero.infrastructure.websocket.WebSocketEventListener;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketEventListener")
class WebSocketEventListenerTest {

    @Mock GameService gameService;
    WebSocketEventListener listener;

    @BeforeEach void setUp() {
        listener = new WebSocketEventListener(gameService);
    }

    @Test void constructor_null_throws() {
        assertThatNullPointerException()
                .isThrownBy(() -> new WebSocketEventListener(null));
    }

    @Test void disconnect_nullSessionAttributes_doesNothing() {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getMessage()).thenReturn(buildMessage(null));
        listener.handleWebSocketDisconnectListener(event);
        verifyNoInteractions(gameService);
    }

    @Test void disconnect_noPlayerId_doesNothing() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("gameId", "G1");
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getMessage()).thenReturn(buildMessage(attrs));
        listener.handleWebSocketDisconnectListener(event);
        verifyNoInteractions(gameService);
    }

    @Test void disconnect_noGameId_doesNothing() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("playerId", "P1");
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getMessage()).thenReturn(buildMessage(attrs));
        listener.handleWebSocketDisconnectListener(event);
        verifyNoInteractions(gameService);
    }

    @Test void disconnect_withPlayerAndGame_callsLeaveGame() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("playerId", "P1");
        attrs.put("playerName", "Alice");
        attrs.put("gameId", "G1");
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getMessage()).thenReturn(buildMessage(attrs));
        listener.handleWebSocketDisconnectListener(event);
        verify(gameService).leaveGame(any(LeaveGameCommand.class));
    }

    private org.springframework.messaging.Message<byte[]> buildMessage(
            Map<String, Object> sessionAttrs) {
        SimpMessageHeaderAccessor accessor =
                SimpMessageHeaderAccessor.create(SimpMessageType.DISCONNECT);
        if (sessionAttrs != null) accessor.setSessionAttributes(sessionAttrs);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}