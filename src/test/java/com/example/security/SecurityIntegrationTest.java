package com.example.security;

import com.example.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = HealthController.class,
        properties = {
                "app.security.username=test-admin",
                "app.security.password=test-password"
        }
)
@Import({
        SecurityConfig.class,
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

    @Autowired
    private MockMvc mockMvc;

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
                        .with(httpBasic("test-admin", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowValidCredentials() throws Exception {

        mockMvc.perform(get("/test/protected")
                        .with(httpBasic("test-admin", "test-password")))
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
                        .with(httpBasic("test-admin", "test-password")))
                .andExpect(status().isOk());
    }
}
