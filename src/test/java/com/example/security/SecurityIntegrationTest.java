package com.example.security;

import com.example.health.HealthController;
import com.example.user.entity.UserEntity;
import com.example.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HealthController.class)
@Import({
        SecurityConfig.class,
        DatabaseUserDetailsService.class,
        SecurityIntegrationTest.TestController.class
})
class SecurityIntegrationTest {

    @RestController
    static class TestController {

        @GetMapping("/test/protected")
        public String protectedEndpoint() {
            return "OK";
        }

        @PostMapping("/test/protected")
        public String protectedPost() {
            return "CREATED";
        }
    }

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    private static final String EMAIL = "test@example.com";
    private static final String PASSWORD = "test-password";

    @BeforeEach
    void setUp() {

        UserEntity user = new UserEntity(
                "Test User",
                EMAIL,
                passwordEncoder.encode(PASSWORD)
        );

        // Simulate retrieving a registered user from PostgreSQL.
        when(userRepository.findByEmail(EMAIL))
                .thenReturn(Optional.of(user));
    }

    @Test
    void shouldAllowHealthCheckWithoutAuthentication()
            throws Exception {

        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectUnauthenticatedRequest()
            throws Exception {

        mockMvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidCredentials()
            throws Exception {

        mockMvc.perform(get("/api/accounts")
                        .with(httpBasic(EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowValidCredentials() throws Exception {

        mockMvc.perform(get("/test/protected")
                        .with(httpBasic(EMAIL, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectUnauthenticatedPostRequest() throws Exception {

        mockMvc.perform(post("/test/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAuthenticatedPostRequest() throws Exception {

        mockMvc.perform(post("/test/protected")
                        .with(httpBasic(EMAIL, PASSWORD)))
                .andExpect(status().isOk());
    }
}
