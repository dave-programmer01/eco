package com.heraim.eco.service;

import com.heraim.eco.model.AuditContext;
import com.heraim.eco.model.AuditState;
import com.heraim.eco.model.Level;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditStateMachineTest {

    @Test
    void testRunWithHighRiskFlagTransitionsToHumanReview() {
        RetrievalService fakeRetrieval = new RetrievalService(null) {
            @Override
            public List<Document> retrieve(String query) {
                return List.of(new Document("Regulation: Unlimited liability is prohibited."));
            }
        };

        ChatModel fakeChatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                String json = "{\"flags\":[{\"level\":\"HIGH\",\"reason\":\"Unlimited liability\",\"quotedSpan\":\"Party A shall be liable for any and all damages without limitation\"}]}";
                return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
            }
        };

        ChatClient.Builder builder = ChatClient.builder(fakeChatModel);
        AuditStateMachine stateMachine = new AuditStateMachine(builder, fakeRetrieval);

        AuditContext context = new AuditContext("Party A shall be liable for any and all damages without limitation");
        AuditContext result = stateMachine.run(context);

        assertEquals(AuditState.HUMAN_REVIEW, result.getState());
        assertEquals(1, result.getFlags().size());
        assertEquals(Level.HIGH, result.getFlags().getFirst().getLevel());
        assertEquals("Unlimited liability", result.getFlags().getFirst().getReason());
        assertEquals("Party A shall be liable for any and all damages without limitation", result.getFlags().getFirst().getQuotedSpan());
    }

    @Test
    void testRunWithLowRiskFlagTransitionsToApply() {
        RetrievalService fakeRetrieval = new RetrievalService(null) {
            @Override
            public List<Document> retrieve(String query) {
                return List.of(new Document("Regulation: Standard clauses."));
            }
        };

        ChatModel fakeChatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                String json = "{\"flags\":[{\"level\":\"LOW\",\"reason\":\"Minor wording issue\",\"quotedSpan\":\"Party A will notify Party B\"}]}";
                return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
            }
        };

        ChatClient.Builder builder = ChatClient.builder(fakeChatModel);
        AuditStateMachine stateMachine = new AuditStateMachine(builder, fakeRetrieval);

        AuditContext context = new AuditContext("Party A will notify Party B");
        AuditContext result = stateMachine.run(context);

        assertEquals(AuditState.APPLY, result.getState());
        assertEquals(1, result.getFlags().size());
        assertEquals(Level.LOW, result.getFlags().getFirst().getLevel());
    }

    @Test
    void testResumeBlockedWhenAwaitingHumanReview() {
        AuditStateMachine stateMachine = new AuditStateMachine(ChatClient.builder(new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"flags\":[]}"))));
            }
        }), new RetrievalService(null) {
            @Override
            public List<Document> retrieve(String query) {
                return List.of();
            }
        });

        AuditContext context = new AuditContext("Contract text");
        context.setState(AuditState.HUMAN_REVIEW);
        context.getFlags().add(new com.heraim.eco.model.RiskFlag(Level.HIGH, "High risk clause", "Unlimited liability"));

        AuditContext result = stateMachine.resume(context);
        assertEquals(AuditState.HUMAN_REVIEW, result.getState());
    }

    @Test
    void testResumeCompletesToDoneWhenHumanDecisionProvided() {
        AuditStateMachine stateMachine = new AuditStateMachine(ChatClient.builder(new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("{\"flags\":[]}"))));
            }
        }), new RetrievalService(null) {
            @Override
            public List<Document> retrieve(String query) {
                return List.of();
            }
        });

        AuditContext context = new AuditContext("Contract text");
        context.setState(AuditState.HUMAN_REVIEW);
        com.heraim.eco.model.RiskFlag flag = new com.heraim.eco.model.RiskFlag(Level.HIGH, "High risk clause", "Unlimited liability");
        context.getFlags().add(flag);

        // Make decision
        context.decide(flag.getFlagId(), com.heraim.eco.model.Decision.APPROVED);

        AuditContext result = stateMachine.resume(context);
        assertEquals(AuditState.DONE, result.getState());
    }
}
