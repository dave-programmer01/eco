package com.heraim.eco.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.heraim.eco.dto.AuditRequest;
import com.heraim.eco.dto.DecisionRequest;
import com.heraim.eco.dto.LoginRequest;
import com.heraim.eco.dto.RegisterRequest;
import com.heraim.eco.model.Decision;
import com.heraim.eco.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
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
        "spring.datasource.password=",
        "security.jwt.secret=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970",
        "security.jwt.expiration-ms=86400000"
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

        @Bean
        @Primary
        public ChatClient.Builder testChatClientBuilder() {
            ChatModel fakeChatModel = new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    String json = "{\"flags\":[{\"level\":\"HIGH\",\"reason\":\"Unlimited liability\",\"quotedSpan\":\"Party A is liable\"}]}";
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
                }
            };
            return ChatClient.builder(fakeChatModel);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testSecurityFilterPublicAndProtectedEndpoints() throws Exception {
        // 1. Protected endpoint without token -> 403 Forbidden
        mockMvc.perform(get("/api/v1/audit"))
                .andExpect(status().isForbidden());

        // 2. Register new user (public endpoint)
        RegisterRequest registerReq = new RegisterRequest("integration_user", "integration@example.com", "Password123!", Role.USER);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.username").value("integration_user"));

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

    @Test
    void testPerRecordOwnershipAndCrossUserIsolation() throws Exception {
        // Register User A
        RegisterRequest userAReq = new RegisterRequest("alice", "alice@example.com", "Secret123!", Role.USER);
        MvcResult resA = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userAReq)))
                .andExpect(status().isOk())
                .andReturn();
        String tokenA = objectMapper.readTree(resA.getResponse().getContentAsString()).get("token").asText();

        // Register User B
        RegisterRequest userBReq = new RegisterRequest("bob", "bob@example.com", "Secret123!", Role.USER);
        MvcResult resB = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(userBReq)))
                .andExpect(status().isOk())
                .andReturn();
        String tokenB = objectMapper.readTree(resB.getResponse().getContentAsString()).get("token").asText();

        // Alice creates an audit
        AuditRequest createReq = new AuditRequest("Party A is liable for all damages.");
        MvcResult createRes = mockMvc.perform(post("/api/v1/audit")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").isNotEmpty())
                .andExpect(jsonPath("$.ownerId").value("alice"))
                .andReturn();

        String auditId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("contractId").asText();

        // Alice can access her own audit and ledger
        mockMvc.perform(get("/api/v1/audit/" + auditId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value(auditId));

        mockMvc.perform(get("/api/v1/audit/" + auditId + "/ledger")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());

        // Bob CANNOT access Alice's audit (403 Forbidden)
        mockMvc.perform(get("/api/v1/audit/" + auditId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Bob CANNOT access Alice's ledger (403 Forbidden)
        mockMvc.perform(get("/api/v1/audit/" + auditId + "/ledger")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Bob CANNOT resume Alice's audit (403 Forbidden)
        mockMvc.perform(post("/api/v1/audit/" + auditId + "/resume")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        // Bob CANNOT make a decision on Alice's audit (403 Forbidden)
        DecisionRequest decReq = new DecisionRequest("someFlag", Decision.APPROVED);
        mockMvc.perform(post("/api/v1/audit/" + auditId + "/decision")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testOpenApiEndpointsArePubliclyAccessible() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
