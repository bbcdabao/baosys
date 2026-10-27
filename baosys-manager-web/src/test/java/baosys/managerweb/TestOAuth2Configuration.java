package baosys.managerweb;

import baosys.common.security.jwt.JwtConstants;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@TestConfiguration(proxyBeanMethods = false)
public class TestOAuth2Configuration {

    @Bean
    ReactiveClientRegistrationRepository registrations() {
        return new InMemoryReactiveClientRegistrationRepository(
            ClientRegistration.withRegistrationId("baosys")
                .clientId(JwtConstants.BROWSER_CLIENT_ID)
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/baosys")
                .scope("openid", "profile")
                .authorizationUri("http://localhost/authorize")
                .tokenUri("http://localhost/token")
                .jwkSetUri("http://localhost/jwks")
                .userNameAttributeName("sub")
                .build()
        );
    }
}
