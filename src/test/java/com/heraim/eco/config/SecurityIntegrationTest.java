package com.heraim.eco.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heraim.eco.dto.LoginRequest;
import com.heraim.eco.dto.RegisterRequest;
import com.heraim.eco.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public VectorStore testVectorStore() {
            return new VectorStore() {
                @Override public void add(List<Document> documents) {}
                @Override public void delete(List<String> idList) {}
                @Override public void delete(Filter.Expression filterExpression) {}
                @Override
                public List<Document> similaritySearch(SearchRequest request) {
                    return List.of();
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testSecurityFilterPublicAndProtectedEndpoints() throws Exception {
        // 1. Protected endpoint without token -> 401 or 403 (Unauthorized/Forbidden)
        mockMvc.perform(get("/api/v1/audit"))
                .andExpect(status().isForbidden());

        // 2. Register new user (public endpoint)
        RegisterRequest registerReq = new RegisterRequest("integration_user", "integration@example.com", "Password123!", Role.USER);
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("integration_user"))
                .andReturn();

        // 3. Login with credentials (public endpoint)
        LoginRequest loginReq = new LoginRequest("integration_user", "Password123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        String responseJson = loginResult.getResponse().getContentAsString();
        String token = objectMapper.readTree(responseJson).get("token").asText();

        // 4. Access protected endpoint with invalid token -> 403
        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isForbidden());

        // 5. Access protected endpoint with valid JWT token -> 200 OK
        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
