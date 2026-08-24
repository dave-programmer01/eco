package com.heraim.eco.repository;

import com.heraim.eco.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByAuditIdOrderByTimestamp(String auditId);

    List<LedgerEntry> findByAuditIdOrderByTimestampAsc(String auditId);
}
