package baosys.manager.app.controllers;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import baosys.common.security.annotation.EnableUltraViresCheck;
import baosys.common.security.jwt.JwtTokenService;

/** Real signing, public endpoint and Servlet security chain without requiring a database. */
@SpringBootTest(classes = JwksSecurityTest.Application.class)
@AutoConfigureMockMvc
class JwksSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenService tokens;

    @Test void anonymousJwksContainsOnlyPublicKeysAndVerifiesIssuedToken() throws Exception {
        String json = mvc.perform(get("/oauth2/jwks")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist()).andReturn().getResponse().getContentAsString();
        String token = token(List.of("user:list"));
        var key = (RSAKey) JWKSet.parse(json).getKeyByKeyId(SignedJWT.parse(token).getHeader().getKeyID());
        assertThat(key).isNotNull();
        assertThat(key.isPrivate()).isFalse();
        assertThat(NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build().decode(token).getSubject()).isEqualTo("1001");
    }
    @Test void directServiceAccessRequiresAuthenticationAndMethodPermission() throws Exception {
        mvc.perform(get("/test-protected").header("X-User-Id", "1001")).andExpect(status().isUnauthorized());
        mvc.perform(get("/test-protected").header("Authorization", "Bearer " + token(List.of())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/test-protected").header("Authorization", "Bearer " + token(List.of("user:list"))))
                .andExpect(status().isOk());
    }
    private String token(List<String> permissions) {
        return tokens.generateAccessToken("1001", "test-user", "MANAGER", permissions, Map.of());
    }
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @EnableUltraViresCheck
    @Import({JwksController.class, ProtectedController.class})
    static class Application { }
    @RestController
    static class ProtectedController {
        @GetMapping("/test-protected")
        @PreAuthorize("hasAuthority('user:list')")
        public String protectedResource() { return "ok"; }
    }
}
