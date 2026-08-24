package com.heraim.eco.model;

import java.util.UUID;

public class RiskFlag {

    private String flagId = UUID.randomUUID().toString();
    private final Level level;
    private final String reason;       // plain-English why it's risky
    private final String quotedSpan;   // the exact clause text
    private Decision decision = Decision.PENDING;

    public RiskFlag(Level level, String reason, String quotedSpan) {
        this.level = level;
        this.reason = reason;
        this.quotedSpan = quotedSpan;
    }

    public String getFlagId() {
        return flagId;
    }

    public Level getLevel() {
        return level;
    }

    public String getReason() {
        return reason;
    }

    public String getQuotedSpan() {
        return quotedSpan;
    }

    public Decision getDecision() {
        return decision;
    }

    public void setDecision(Decision decision) {
        this.decision = decision;
    }

    // the check the state machine will use to decide whether to pause
    public boolean requiresHumanReview() {
        return level == Level.HIGH && decision == Decision.PENDING;
    }

}
