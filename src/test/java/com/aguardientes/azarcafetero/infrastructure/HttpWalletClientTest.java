package com.aguardientes.azarcafetero.infrastructure;

import com.aguardientes.azarcafetero.infrastructure.wallet.HttpWalletClient;
import com.aguardientes.azarcafetero.infrastructure.wallet.WalletOperationException;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("HttpWalletClient")
class HttpWalletClientTest {

    /** El constructor no debe lanzar excepción con parámetros válidos. */
    @Test
    void constructor_doesNotThrow() {
        assertThatNoException()
                .isThrownBy(() -> new HttpWalletClient("http://localhost:8080", "key"));
    }

    /**
     * Cuando la URL no existe (puerto 1 inaccesible), placeBet debe lanzar
     * WalletOperationException (el cliente captura el error HTTP y lo re-lanza).
     */
    @Test
    void placeBet_networkError_throwsWalletOperationException() {
        HttpWalletClient client = new HttpWalletClient("http://localhost:1", "key");
        assertThatThrownBy(() -> client.placeBet("user1", BigDecimal.TEN))
                .isInstanceOf(WalletOperationException.class);
    }

    @Test
    void receiveWin_networkError_throwsWalletOperationException() {
        HttpWalletClient client = new HttpWalletClient("http://localhost:1", "key");
        assertThatThrownBy(() -> client.receiveWin("user1", BigDecimal.TEN))
                .isInstanceOf(WalletOperationException.class);
    }

    /**
     * registerLoss solo hace log.warn en caso de error — no re-lanza la excepción.
     */
    @Test
    void registerLoss_networkError_doesNotThrow() {
        HttpWalletClient client = new HttpWalletClient("http://localhost:1", "key");
        assertThatNoException()
                .isThrownBy(() -> client.registerLoss("user1", BigDecimal.TEN));
    }

    /** WalletOperationException guarda el mensaje y causa. */
    @Test
    void walletOperationException_storesMessageAndCause() {
        RuntimeException cause = new RuntimeException("red problem");
        WalletOperationException ex = new WalletOperationException("msg", cause);
        assertThat(ex.getMessage()).isEqualTo("msg");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}