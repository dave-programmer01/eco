package com.heraim.eco.service;

import com.heraim.eco.dto.AnalysisResult;
import com.heraim.eco.dto.FlagDto;
import com.heraim.eco.entity.EntryType;
import com.heraim.eco.entity.LedgerEntry;
import com.heraim.eco.model.AuditContext;
import com.heraim.eco.model.AuditState;
import com.heraim.eco.model.Decision;
import com.heraim.eco.model.Level;
import com.heraim.eco.model.RiskFlag;
import com.heraim.eco.repository.LedgerRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditStateMachine {

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;
    private final LedgerRepository ledgerRepository;

    public AuditStateMachine(ChatClient.Builder chatClient, RetrievalService retrievalService, LedgerRepository ledgerRepository) {
        this.chatClient = chatClient.build();
        this.retrievalService = retrievalService;
        this.ledgerRepository = ledgerRepository;
    }

    public AuditContext run (AuditContext context) {
        if (context.getState() == AuditState.INGEST){
            context.setState(AuditState.ANALYZE);
        }

        if (context.getState() == AuditState.ANALYZE) {
            analyze(context);
            if(context.isAwaitingHuman()){
                context.setState(AuditState.HUMAN_REVIEW);
                return context;
            }else{
                context.setState(AuditState.APPLY);
            }
        }
        return context;
    }

    private void analyze(AuditContext context) {
        List<Document> rules = retrievalService.retrieve(context.getContractText());
        String rulesText = rules.stream().map(Document::getText).collect(Collectors.joining("\n"));

        String promptString = """
        You are a senior enterprise compliance auditor reviewing a contract for legal and financial risk.

        Assess the contract ONLY against the regulations provided below. Do not rely on outside rules.

        === REGULATIONS ===
        %s

        === CONTRACT ===
        %s

        === TASK ===
        Identify each clause in the contract that violates, conflicts with, or creates material risk
        under the regulations above. For every issue, produce one flag with:
          - level: exactly one of HIGH, MEDIUM, or LOW (uppercase).
              HIGH = clear violation or severe financial/legal exposure;
              MEDIUM = ambiguous or moderate risk warranting review;
              LOW = minor or stylistic concern.
          - reason: one or two sentences, specific to this contract, citing which regulation concern applies.
          - quotedSpan: the exact text copied verbatim from the contract that triggered the flag.

        Rules:
          - Only flag text that appears verbatim in the contract. Never invent or paraphrase clauses.
          - If no clause presents a risk, return an empty list of flags.
        """.formatted(rulesText, context.getContractText());

        AnalysisResult result = chatClient.prompt()
                .user(promptString)
                .call()
                .entity(AnalysisResult.class);

        if (result != null && result.flags() != null) {
            for (FlagDto dto : result.flags()) {
                RiskFlag flag = new RiskFlag(dto.level(), dto.reason(), dto.quotedSpan());
                context.getFlags().add(flag);
                ledgerRepository.save(new LedgerEntry(
                        context.getContractId(),
                        EntryType.FLAG_RAISED,
                        flag.getLevel(),
                        flag.getReason(),
                        flag.getQuotedSpan()
                ));
            }
        }
    }

    public void recordDecision(AuditContext context, String flagId, Decision decision) {
        context.decide(flagId, decision);
        RiskFlag flag = context.getFlags().stream()
                .filter(f -> f.getFlagId().equals(flagId))
                .findFirst()
                .orElse(null);

        Level level = flag != null ? flag.getLevel() : null;
        String quotedSpan = flag != null ? flag.getQuotedSpan() : null;

        ledgerRepository.save(new LedgerEntry(
                context.getContractId(),
                EntryType.DECISION_MADE,
                level,
                decision.name(),
                quotedSpan
        ));
    }

    public void decide(AuditContext context, String flagId, Decision decision) {
        recordDecision(context, flagId, decision);
    }

    public AuditContext resume(AuditContext context){
        if(context.isAwaitingHuman()) return context;

        if(context.getState() == AuditState.HUMAN_REVIEW){
            context.setState(AuditState.APPLY);
        }

        if(context.getState() == AuditState.APPLY){
            context.setState(AuditState.LOG);
        }

        if(context.getState() == AuditState.LOG){
            context.setState(AuditState.DONE);
        }
        return context;
    }
}

