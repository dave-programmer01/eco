package com.heraim.eco.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AuditContext {

    private final List<RiskFlag> flags = new ArrayList<>();
    private AuditState state = AuditState.INGEST;
    private String contractText;
    private String contractId = UUID.randomUUID().toString();

    public AuditContext(String contractText) {
        this.contractText = contractText;
    }

    public List<RiskFlag> getFlags() {
        return flags;
    }

    public AuditState getState() {
        return state;
    }

    public void setState(AuditState state) {
        this.state = state;
    }

    public String getContractText() {
        return contractText;
    }

    public void setContractText(String contractText) {
        this.contractText = contractText;
    }

    public String getContractId() {
        return contractId;
    }

    public boolean isAwaitingHuman(){
        for (RiskFlag flag : flags) {
            if (flag.requiresHumanReview()) {
                return true;
            }
        }
        return false;
    }

    public void decide(String flagId, Decision decision) {
        for (RiskFlag flag : flags) {
            if (flag.getFlagId().equals(flagId)) {
                flag.setDecision(decision);
                return;
            }
        }
        throw new IllegalArgumentException("No such flag: " + flagId);
    }

}
