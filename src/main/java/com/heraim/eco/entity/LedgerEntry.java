package com.heraim.eco.entity;

import com.heraim.eco.model.Level;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String auditId;

    @Enumerated(EnumType.STRING)
    private EntryType type;

    @Enumerated(EnumType.STRING)
    private Level level;

    @Column(length = 2000)
    private String reason;

    @Column(length = 4000)
    private String quotedSpan;

    private Instant timestamp;

    public LedgerEntry() {
    }

    public LedgerEntry(String auditId, EntryType type, Level level, String reason, String quotedSpan) {
        this(null, auditId, type, level, reason, quotedSpan, Instant.now());
    }

    public LedgerEntry(Long id, String auditId, EntryType type, Level level, String reason, String quotedSpan, Instant timestamp) {
        this.id = id;
        this.auditId = auditId;
        this.type = type;
        this.level = level;
        this.reason = reason;
        this.quotedSpan = quotedSpan;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    public EntryType getType() {
        return type;
    }

    public void setType(EntryType type) {
        this.type = type;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
