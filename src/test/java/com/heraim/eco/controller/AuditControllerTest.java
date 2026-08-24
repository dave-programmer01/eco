package com.heraim.eco.controller;

import com.heraim.eco.dto.AuditRequest;
import com.heraim.eco.dto.DecisionRequest;
import com.heraim.eco.model.AuditContext;
import com.heraim.eco.model.AuditState;
import com.heraim.eco.model.Decision;
import com.heraim.eco.model.Level;
import com.heraim.eco.service.AuditRegistry;
import com.heraim.eco.service.AuditStateMachine;
import com.heraim.eco.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuditControllerTest {

    @Test
    void testSearchEndpoint() {
        RetrievalService fakeRetrievalService = new RetrievalService(null) {
            @Override
            public List<Document> retrieve(String query) {
                return List.of(new Document("Result for " + query));
            }
        };

        AuditController controller = new AuditController(null, null, fakeRetrievalService);
        List<Document> results = controller.search("test query");

        assertEquals(1, results.size());
        assertEquals("Result for test query", results.getFirst().getText());
    }

    @Test
    void testFullHumanInTheLoopAuditCycle() {
        RetrievalService fakeRetrieval = new RetrievalService(null) {
            @Override
            public List<Document> retrieve(String query) {
                return List.of(new Document("Regulation: Unlimited liability is prohibited."));
            }
        };

        ChatModel fakeChatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                String json = "{\"flags\":[{\"level\":\"HIGH\",\"reason\":\"Unlimited liability risk\",\"quotedSpan\":\"Party A shall be liable for any and all damages without limitation\"}]}";
                return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
            }
        };

        ChatClient.Builder builder = ChatClient.builder(fakeChatModel);
        AuditStateMachine stateMachine = new AuditStateMachine(builder, fakeRetrieval);
        AuditRegistry registry = new AuditRegistry();
        AuditController controller = new AuditController(stateMachine, registry, fakeRetrieval);

        // 1. POST /api/v1/audit (start)
        AuditRequest startRequest = new AuditRequest("Party A shall be liable for any and all damages without limitation");
        ResponseEntity<AuditContext> startResponse = controller.start(startRequest);

        assertNotNull(startResponse.getBody());
        AuditContext context = startResponse.getBody();
        String contractId = context.getContractId();
        assertEquals(AuditState.HUMAN_REVIEW, context.getState());
        assertEquals(1, context.getFlags().size());
        assertEquals(Level.HIGH, context.getFlags().getFirst().getLevel());
        assertEquals(Decision.PENDING, context.getFlags().getFirst().getDecision());

        String flagId = context.getFlags().getFirst().getFlagId();

        // 2. POST /api/v1/audit/{id}/decision (approve the flag)
        DecisionRequest decisionRequest = new DecisionRequest(flagId, Decision.APPROVED);
        ResponseEntity<AuditContext> decisionResponse = controller.decide(contractId, decisionRequest);

        assertNotNull(decisionResponse.getBody());
        assertEquals(Decision.APPROVED, decisionResponse.getBody().getFlags().getFirst().getDecision());

        // 3. POST /api/v1/audit/{id}/resume (resume audit after decision)
        ResponseEntity<AuditContext> resumeResponse = controller.resume(contractId);

        assertNotNull(resumeResponse.getBody());
        assertEquals(AuditState.DONE, resumeResponse.getBody().getState());
    }
}
