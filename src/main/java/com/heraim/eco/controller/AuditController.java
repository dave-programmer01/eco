package com.heraim.eco.controller;

import com.heraim.eco.dto.AuditRequest;
import com.heraim.eco.dto.DecisionRequest;
import com.heraim.eco.entity.LedgerEntry;
import com.heraim.eco.model.AuditContext;
import com.heraim.eco.repository.LedgerRepository;
import com.heraim.eco.service.AuditRegistry;
import com.heraim.eco.service.AuditStateMachine;
import com.heraim.eco.service.RetrievalService;
import org.springframework.ai.document.Document;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditStateMachine auditStateMachine;
    private final AuditRegistry auditRegistry;
    private final RetrievalService retrievalService;
    private final LedgerRepository ledgerRepository;

    public AuditController(AuditStateMachine auditStateMachine,
                           AuditRegistry auditRegistry,
                           RetrievalService retrievalService,
                           LedgerRepository ledgerRepository) {
        this.auditStateMachine = auditStateMachine;
        this.auditRegistry = auditRegistry;
        this.retrievalService = retrievalService;
        this.ledgerRepository = ledgerRepository;
    }

    @PostMapping
    public ResponseEntity<AuditContext> start(@RequestBody AuditRequest request) {
        AuditContext context = new AuditContext(request.contractText());
        auditRegistry.save(context);
        return ResponseEntity.ok(auditStateMachine.run(context));
    }

    @PostMapping("/{id}/resume")
    public ResponseEntity<AuditContext> resume(@PathVariable String id) {
        AuditContext context = auditRegistry.get(id);
        return ResponseEntity.ok(auditStateMachine.resume(context));
    }

    @PostMapping("/{id}/decision")
    public ResponseEntity<AuditContext> decide (
        @PathVariable String id,
        @RequestBody DecisionRequest request
        ){
        AuditContext context = auditRegistry.get(id);
        auditStateMachine.recordDecision(context, request.flagId(), request.decision());
        return ResponseEntity.ok(context);
    }

    @GetMapping("/search")
    public List<Document> search(@RequestParam String q) {
        return retrievalService.retrieve(q);
    }

    @GetMapping("/{id}/ledger")
    public List<LedgerEntry> getLedger(@PathVariable String id) {
        return ledgerRepository.findByAuditIdOrderByTimestamp(id);
    }
}
