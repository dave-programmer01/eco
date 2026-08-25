package com.heraim.eco.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "risk_flag")
public class RiskFlag {

    @Id
    @Column(name = "flag_id")
    private String flagId = UUID.randomUUID().toString();

    @Enumerated(EnumType.STRING)
    @Column(name = "level")
    private Level level;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;       // plain-English why it's risky

    @Column(name = "quoted_span", columnDefinition = "TEXT")
    private String quotedSpan;   // the exact clause text

    @Enumerated(EnumType.STRING)
    @Column(name = "decision")
    private Decision decision = Decision.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audit_id")
    @JsonIgnore
    private AuditContext audit;

    public RiskFlag() {
    }

    public RiskFlag(Level level, String reason, String quotedSpan) {
        this.level = level;
        this.reason = reason;
        this.quotedSpan = quotedSpan;
    }

    public RiskFlag(String flagId, Level level, String reason, String quotedSpan, Decision decision) {
        this.flagId = flagId;
        this.level = level;
        this.reason = reason;
        this.quotedSpan = quotedSpan;
        this.decision = decision;
    }

    public String getFlagId() {
        return flagId;
    }

    public void setFlagId(String flagId) {
        this.flagId = flagId;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getQuotedSpan() {
        return quotedSpan;
    }

    public void setQuotedSpan(String quotedSpan) {
        this.quotedSpan = quotedSpan;
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    public AuditContext getAudit() {
        return audit;
    }

    public void setAudit(AuditContext audit) {
        this.audit = audit;
    }

    // the check the state machine will use to decide whether to pause
    public boolean requiresHumanReview() {
        return level == Level.HIGH && decision == Decision.PENDING;
    }

}
