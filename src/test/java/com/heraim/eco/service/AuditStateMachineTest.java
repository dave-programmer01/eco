package com.heraim.eco.service;

import com.heraim.eco.entity.EntryType;
import com.heraim.eco.entity.LedgerEntry;
import com.heraim.eco.model.AuditContext;
import com.heraim.eco.model.AuditState;
import com.heraim.eco.model.Level;
import com.heraim.eco.repository.LedgerRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditStateMachineTest {

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
    void testRunWithHighRiskFlagTransitionsToHumanReviewAndSavesLedgerEntry() {
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
        FakeLedgerRepository fakeRepo = new FakeLedgerRepository();
        AuditStateMachine stateMachine = new AuditStateMachine(builder, fakeRetrieval, fakeRepo);

        AuditContext context = new AuditContext("Party A shall be liable for any and all damages without limitation");
        AuditContext result = stateMachine.run(context);

        assertEquals(AuditState.HUMAN_REVIEW, result.getState());
        assertEquals(1, result.getFlags().size());
        assertEquals(Level.HIGH, result.getFlags().getFirst().getLevel());
        assertEquals("Unlimited liability", result.getFlags().getFirst().getReason());
        assertEquals("Party A shall be liable for any and all damages without limitation", result.getFlags().getFirst().getQuotedSpan());

        // Verify ledger entry
        assertEquals(1, fakeRepo.entries.size());
        LedgerEntry entry = fakeRepo.entries.getFirst();
        assertEquals(context.getContractId(), entry.getAuditId());
        assertEquals(EntryType.FLAG_RAISED, entry.getType());
        assertEquals(Level.HIGH, entry.getLevel());
        assertEquals("Unlimited liability", entry.getReason());
        assertEquals("Party A shall be liable for any and all damages without limitation", entry.getQuotedSpan());
    }

    @Test
    void testRecordDecisionSavesDecisionMadeEntry() {
        FakeLedgerRepository fakeRepo = new FakeLedgerRepository();
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
        }, fakeRepo);

        AuditContext context = new AuditContext("Sample contract");
        com.heraim.eco.model.RiskFlag flag = new com.heraim.eco.model.RiskFlag(Level.HIGH, "High risk clause", "Unlimited liability clause");
        context.addFlag(flag);

        stateMachine.recordDecision(context, flag.getFlagId(), com.heraim.eco.model.Decision.APPROVED);

        assertEquals(com.heraim.eco.model.Decision.APPROVED, flag.getDecision());
        assertEquals(1, fakeRepo.entries.size());
        LedgerEntry entry = fakeRepo.entries.getFirst();
        assertEquals(context.getContractId(), entry.getAuditId());
        assertEquals(EntryType.DECISION_MADE, entry.getType());
        assertEquals(Level.HIGH, entry.getLevel());
        assertEquals("APPROVED", entry.getReason());
        assertEquals("Unlimited liability clause", entry.getQuotedSpan());
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
        FakeLedgerRepository fakeRepo = new FakeLedgerRepository();
        AuditStateMachine stateMachine = new AuditStateMachine(builder, fakeRetrieval, fakeRepo);

        AuditContext context = new AuditContext("Party A will notify Party B");
        AuditContext result = stateMachine.run(context);

        assertEquals(AuditState.APPLY, result.getState());
        assertEquals(1, result.getFlags().size());
        assertEquals(Level.LOW, result.getFlags().getFirst().getLevel());
    }

    @Test
    void testResumeBlockedWhenAwaitingHumanReview() {
        FakeLedgerRepository fakeRepo = new FakeLedgerRepository();
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
        }, fakeRepo);

        AuditContext context = new AuditContext("Contract text");
        context.setState(AuditState.HUMAN_REVIEW);
        context.addFlag(new com.heraim.eco.model.RiskFlag(Level.HIGH, "High risk clause", "Unlimited liability"));

        AuditContext result = stateMachine.resume(context);
        assertEquals(AuditState.HUMAN_REVIEW, result.getState());
    }

    @Test
    void testResumeCompletesToDoneWhenHumanDecisionProvided() {
        FakeLedgerRepository fakeRepo = new FakeLedgerRepository();
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
        }, fakeRepo);

        AuditContext context = new AuditContext("Contract text");
        context.setState(AuditState.HUMAN_REVIEW);
        com.heraim.eco.model.RiskFlag flag = new com.heraim.eco.model.RiskFlag(Level.HIGH, "High risk clause", "Unlimited liability");
        context.addFlag(flag);

        // Make decision
        context.decide(flag.getFlagId(), com.heraim.eco.model.Decision.APPROVED);

        AuditContext result = stateMachine.resume(context);
        assertEquals(AuditState.DONE, result.getState());
    }

    @Test
    void testStreamAnalysisReturnsFluxOfReasoningChunks() {
        RetrievalService fakeRetrieval = new RetrievalService(null) {
            @Override
            public List<Document> retrieve(String query) {
                return List.of(new Document("Regulation: Liability limits required."));
            }
        };

        ChatModel fakeChatModel = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("Response"))));
            }

            @Override
            public reactor.core.publisher.Flux<ChatResponse> stream(Prompt prompt) {
                return reactor.core.publisher.Flux.just(
                        new ChatResponse(List.of(new Generation(new AssistantMessage("Clause 1: ")))),
                        new ChatResponse(List.of(new Generation(new AssistantMessage("High risk detected due to unlimited liability."))))
                );
            }
        };

        ChatClient.Builder builder = ChatClient.builder(fakeChatModel);
        FakeLedgerRepository fakeRepo = new FakeLedgerRepository();
        AuditStateMachine stateMachine = new AuditStateMachine(builder, fakeRetrieval, fakeRepo);

        reactor.core.publisher.Flux<String> streamFlux = stateMachine.streamAnalysis("Party A shall be liable for any and all damages.");
        List<String> chunks = streamFlux.collectList().block();

        assertEquals(List.of("Clause 1: ", "High risk detected due to unlimited liability."), chunks);
    }
}
