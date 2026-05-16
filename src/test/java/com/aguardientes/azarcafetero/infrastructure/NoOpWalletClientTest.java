package com.aguardientes.azarcafetero.infrastructure;

import com.aguardientes.azarcafetero.infrastructure.wallet.NoOpWalletClient;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("NoOpWalletClient")
class NoOpWalletClientTest {

    private final NoOpWalletClient client = new NoOpWalletClient();

    @Test void placeBet_doesNotThrow() {
        assertThatCode(() -> client.placeBet("U1", BigDecimal.TEN))
                .doesNotThrowAnyException();
    }

    @Test void receiveWin_doesNotThrow() {
        assertThatCode(() -> client.receiveWin("U1", BigDecimal.TEN))
                .doesNotThrowAnyException();
    }

    @Test void registerLoss_doesNotThrow() {
        assertThatCode(() -> client.registerLoss("U1", BigDecimal.TEN))
                .doesNotThrowAnyException();
    }
}