package baosys.manager.infra.database.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LikePatternsTest {

    @Test
    void shouldEscapeLikeWildcardsAndBackslash() {
        assertThat(LikePatterns.contains("alice")).isEqualTo("%alice%");
        assertThat(LikePatterns.contains("_")).isEqualTo("%\\_%");
        assertThat(LikePatterns.contains("%")).isEqualTo("%\\%%");
        assertThat(LikePatterns.contains("\\")).isEqualTo("%\\\\%");
        assertThat(LikePatterns.contains("a_b%c\\d")).isEqualTo("%a\\_b\\%c\\\\d%");
    }
}
