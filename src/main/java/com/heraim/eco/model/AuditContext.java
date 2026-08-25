package com.heraim.eco.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "audit_context")
public class AuditContext {

    @Id
    @Column(name = "contract_id")
    private String contractId = UUID.randomUUID().toString();

    @Column(name = "contract_text", columnDefinition = "TEXT")
    private String contractText;

    @Column(name = "owner_id")
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state")
    private AuditState state = AuditState.INGEST;

    @OneToMany(mappedBy = "audit", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<RiskFlag> flags = new ArrayList<>();

    public AuditContext() {
    }

    public AuditContext(String contractText) {
        this.contractText = contractText;
    }

    public AuditContext(String contractText, String ownerId) {
        this.contractText = contractText;
        this.ownerId = ownerId;
    }

    public List<RiskFlag> getFlags() {
        return flags;
    }

    public void setFlags(List<RiskFlag> flags) {
        this.flags = flags;
        if (flags != null) {
            for (RiskFlag flag : flags) {
                flag.setAudit(this);
            }
        }
    }

    public void addFlag(RiskFlag flag) {
        if (flag != null) {
            this.flags.add(flag);
            flag.setAudit(this);
        }
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

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getContractId() {
        return contractId;
    }

    public void setContractId(String contractId) {
        this.contractId = contractId;
    }

    public boolean isAwaitingHuman() {
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
