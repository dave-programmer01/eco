package com.heraim.eco.controller;

import com.heraim.eco.dto.AuditRequest;
import com.heraim.eco.dto.DecisionRequest;
import com.heraim.eco.entity.EntryType;
import com.heraim.eco.entity.LedgerEntry;
import com.heraim.eco.model.AuditContext;
import com.heraim.eco.model.AuditState;
import com.heraim.eco.model.Decision;
import com.heraim.eco.model.Level;
import com.heraim.eco.repository.LedgerRepository;
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
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuditControllerTest {

    static class FakeLedgerRepository implements LedgerRepository {
        final List<LedgerEntry> entries = new ArrayList<>();

        @Override
        public <S extends LedgerEntry> S save(S entity) {
            entries.add(entity);
            return entity;
        }

        @Override
        public List<LedgerEntry> findByAuditIdOrderByTimestamp(String auditId) {
            return entries.stream().filter(e -> auditId.equals(e.getAuditId())).toList();
        }

        @Override
        public List<LedgerEntry> findByAuditIdOrderByTimestampAsc(String auditId) {
            return findByAuditIdOrderByTimestamp(auditId);
        }

        @Override public void flush() {}
        @Override public <S extends LedgerEntry> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends LedgerEntry> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<LedgerEntry> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public LedgerEntry getOne(Long aLong) { return null; }
        @Override public LedgerEntry getById(Long aLong) { return null; }
        @Override public LedgerEntry getReferenceById(Long aLong) { return null; }
        @Override public <S extends LedgerEntry> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends LedgerEntry> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends LedgerEntry> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends LedgerEntry> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends LedgerEntry> long count(Example<S> example) { return 0; }
        @Override public <S extends LedgerEntry> boolean exists(Example<S> example) { return false; }
        @Override public <S extends LedgerEntry, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends LedgerEntry> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public Optional<LedgerEntry> findById(Long aLong) { return Optional.empty(); }
        @Override public boolean existsById(Long aLong) { return false; }
        @Override public List<LedgerEntry> findAll() { return entries; }
        @Override public List<LedgerEntry> findAllById(Iterable<Long> longs) { return List.of(); }
        @Override public long count() { return entries.size(); }
        @Override public void deleteById(Long aLong) {}
        @Override public void delete(LedgerEntry entity) {}
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends LedgerEntry> entities) {}
        @Override public void deleteAll() {}
        @Override public List<LedgerEntry> findAll(Sort sort) { return entries; }
        @Override public Page<LedgerEntry> findAll(Pageable pageable) { return Page.empty(); }
    }

    @Test
    void testSearchEndpoint() {
        RetrievalService fakeRetrievalService = new RetrievalService(null) {
            @Override
            public List<Document> retrieve(String query) {
                return List.of(new Document("Result for " + query));
            }
        };

        AuditController controller = new AuditController(null, null, fakeRetrievalService, new FakeLedgerRepository());
        List<Document> results = controller.search("test query");

        assertEquals(1, results.size());
        assertEquals("Result for test query", results.getFirst().getText());
    }

    @Test
    void testFullHumanInTheLoopAuditCycleAndLedgerEndpoint() {
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
        FakeLedgerRepository fakeRepo = new FakeLedgerRepository();
        AuditStateMachine stateMachine = new AuditStateMachine(builder, fakeRetrieval, fakeRepo);
        AuditRegistry registry = new AuditRegistry();
        AuditController controller = new AuditController(stateMachine, registry, fakeRetrieval, fakeRepo);

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

        // Check ledger after start (FLAG_RAISED)
        List<LedgerEntry> ledgerAfterStart = controller.getLedger(contractId);
        assertEquals(1, ledgerAfterStart.size());
        assertEquals(EntryType.FLAG_RAISED, ledgerAfterStart.getFirst().getType());
        assertEquals(Level.HIGH, ledgerAfterStart.getFirst().getLevel());
        assertEquals("Unlimited liability risk", ledgerAfterStart.getFirst().getReason());

        // 2. POST /api/v1/audit/{id}/decision (approve the flag)
        DecisionRequest decisionRequest = new DecisionRequest(flagId, Decision.APPROVED);
        ResponseEntity<AuditContext> decisionResponse = controller.decide(contractId, decisionRequest);

        assertNotNull(decisionResponse.getBody());
        assertEquals(Decision.APPROVED, decisionResponse.getBody().getFlags().getFirst().getDecision());

        // Check ledger after decision (FLAG_RAISED + DECISION_MADE)
        List<LedgerEntry> ledgerAfterDecision = controller.getLedger(contractId);
        assertEquals(2, ledgerAfterDecision.size());
        assertEquals(EntryType.FLAG_RAISED, ledgerAfterDecision.get(0).getType());
        assertEquals(EntryType.DECISION_MADE, ledgerAfterDecision.get(1).getType());
        assertEquals(Level.HIGH, ledgerAfterDecision.get(1).getLevel());
        assertEquals("APPROVED", ledgerAfterDecision.get(1).getReason());
        assertEquals("Party A shall be liable for any and all damages without limitation", ledgerAfterDecision.get(1).getQuotedSpan());

        // 3. POST /api/v1/audit/{id}/resume (resume audit after decision)
        ResponseEntity<AuditContext> resumeResponse = controller.resume(contractId);

        assertNotNull(resumeResponse.getBody());
        assertEquals(AuditState.DONE, resumeResponse.getBody().getState());
    }
}
