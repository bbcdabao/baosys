package baosys.common.security.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BCryptPasswordHasherTest {

    @Test
    void shouldHashAndVerifyPassword() {
        PasswordHasher hasher = new BCryptPasswordHasher(4);
        String encoded = hasher.hash("correct-horse-battery-staple");

        assertThat(encoded).isNotEqualTo("correct-horse-battery-staple");
        assertThat(hasher.matches("correct-horse-battery-staple", encoded)).isTrue();
        assertThat(hasher.matches("wrong", encoded)).isFalse();
    }
}
