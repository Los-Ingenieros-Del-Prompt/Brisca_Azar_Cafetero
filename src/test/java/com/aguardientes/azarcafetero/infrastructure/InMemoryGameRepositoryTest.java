package com.aguardientes.azarcafetero.infrastructure;

import com.aguardientes.azarcafetero.domain.model.Game;
import com.aguardientes.azarcafetero.infrastructure.persistence.InMemoryGameRepository;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@DisplayName("InMemoryGameRepository")
class InMemoryGameRepositoryTest {

    private final InMemoryGameRepository repo = new InMemoryGameRepository();
    private Game game;

    @BeforeEach void setUp() {
        game = new Game("G1", 2, 4, BigDecimal.TEN);
    }

    @Test void saveAndFind_returnsGame() {
        repo.save(game);
        assertThat(repo.findById("G1")).contains(game);
    }

    @Test void findById_notFound_returnsEmpty() {
        assertThat(repo.findById("GHOST")).isEmpty();
    }

    @Test void exists_savedGame_returnsTrue() {
        repo.save(game);
        assertThat(repo.exists("G1")).isTrue();
    }

    @Test void exists_notSaved_returnsFalse() {
        assertThat(repo.exists("G1")).isFalse();
    }

    @Test void deleteById_removesGame() {
        repo.save(game);
        repo.deleteById("G1");
        assertThat(repo.findById("G1")).isEmpty();
    }

    @Test void save_null_throws() {
        assertThatIllegalArgumentException().isThrownBy(() -> repo.save(null));
    }

    @Test void getOrCreate_newGame_createsIt() {
        Game g = repo.getOrCreate("G2", 2, 4);
        assertThat(g).isNotNull();
        assertThat(g.getId()).isEqualTo("G2");
    }

    @Test void getOrCreate_existingGame_returnsExisting() {
        repo.save(game);
        Game g = repo.getOrCreate("G1", 2, 4);
        assertThat(g).isSameAs(game);
    }
}