package com.heraim.eco.service;

import com.heraim.eco.dto.AnalysisResult;
import com.heraim.eco.dto.FlagDto;
import com.heraim.eco.model.AuditContext;
import com.heraim.eco.model.AuditState;
import com.heraim.eco.model.RiskFlag;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuditStateMachine {

    private final ChatClient chatClient;
    private final RetrievalService retrievalService;

    public AuditStateMachine(ChatClient.Builder chatClient, RetrievalService retrievalService) {
        this.chatClient = chatClient.build();
        this.retrievalService = retrievalService;
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
                context.getFlags().add(new RiskFlag(dto.level(), dto.reason(), dto.quotedSpan()));
            }
        }
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

