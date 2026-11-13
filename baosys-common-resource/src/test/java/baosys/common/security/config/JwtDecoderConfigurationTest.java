package baosys.common.security.config;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;

class JwtDecoderConfigurationTest {

    @Test
    void requiresExplicitKeySourceAndAlgorithm() {
        var config = new JwtDecoderConfiguration();
        var props = new OAuth2ResourceServerProperties();
        assertThatThrownBy(() -> config.jwtDecoder(props)).isInstanceOf(
            IllegalArgumentException.class
        );
        props.getJwt().setJwkSetUri("http://localhost/jwks");
        props.getJwt().setJwsAlgorithms(List.of("RS512"));
        assertThatThrownBy(() -> config.jwtDecoder(props)).isInstanceOf(
            IllegalArgumentException.class
        );
    }
}
