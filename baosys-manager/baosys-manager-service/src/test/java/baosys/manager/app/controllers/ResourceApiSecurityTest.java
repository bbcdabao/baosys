package baosys.manager.app.controllers;

import baosys.common.security.jwt.JwtConstants;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import baosys.common.security.config.SecurityProperties;
import baosys.common.security.resource.JwtPermissionConverter;
import baosys.common.security.resource.ResourceServerSecurityConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitWebConfig(ResourceApiSecurityTest.Config.class)
@TestPropertySource(properties = "spring.security.resource.permit-all-paths[0]=/public")
class ResourceApiSecurityTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(
                org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()
            )
            .build();
    }

    @Test
    void publicPathsDoNotRequireAuthentication() throws Exception {
        mvc.perform(get("/public")).andExpect(status().isOk());
    }

    @Test
    void sharedApisStillRequireAuthentication() throws Exception {
        mvc.perform(get("/shared")).andExpect(status().isUnauthorized());
        mvc.perform(get("/shared").header("Authorization", "Bearer entry-only")).andExpect(
            status().isOk()
        );
    }

    @Test
    void apiAuthorityWorksWithoutSubsystemEntry() throws Exception {
        mvc.perform(get("/restricted").header("Authorization", "Bearer api-only")).andExpect(
            status().isOk()
        );
    }

    @Test
    void subsystemEntryDoesNotGrantApiAuthority() throws Exception {
        mvc.perform(get("/restricted").header("Authorization", "Bearer entry-only")).andExpect(
            status().isForbidden()
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableConfigurationProperties(SecurityProperties.class)
    @Import({ ResourceServerSecurityConfig.class, Endpoints.class })
    static class Config {

        @Bean
        JwtAuthenticationConverter converter() {
            return JwtPermissionConverter.create();
        }

        @Bean
        JwtDecoder decoder() {
            return token ->
                Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .subject("1")
                    .claim(
                        JwtConstants.PERMISSIONS_CLAIM,
                        token.equals("api-only") ? List.of("MGR:user:list") : List.of("MGR")
                    )
                    .build();
        }
    }

    @RestController
    static class Endpoints {

        @GetMapping({ "/public", "/shared" })
        public String shared() {
            return "ok";
        }

        @GetMapping("/restricted")
        @PreAuthorize("hasAuthority('MGR:user:list')")
        public String restricted() {
            return "ok";
        }
    }
}
