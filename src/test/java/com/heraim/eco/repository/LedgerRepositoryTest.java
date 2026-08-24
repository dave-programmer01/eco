package com.heraim.eco.repository;

import com.heraim.eco.entity.EntryType;
import com.heraim.eco.entity.LedgerEntry;
import com.heraim.eco.model.Level;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
class LedgerRepositoryTest {

    @Autowired
    private LedgerRepository ledgerRepository;

    @Test
    void testSaveAndFindByAuditIdOrderByTimestamp() throws InterruptedException {
        String auditId = "audit-123";

        LedgerEntry entry1 = new LedgerEntry(
                null,
                auditId,
                EntryType.FLAG_RAISED,
                Level.HIGH,
                "Unlimited liability clause",
                "Party A is liable for all damages",
                Instant.now().minusSeconds(10)
        );
        ledgerRepository.save(entry1);

        LedgerEntry entry2 = new LedgerEntry(
                null,
                auditId,
                EntryType.DECISION_MADE,
                Level.HIGH,
                "APPROVED",
                "Party A is liable for all damages",
                Instant.now()
        );
        ledgerRepository.save(entry2);

        // Save another entry for a different auditId
        LedgerEntry otherEntry = new LedgerEntry(
                "other-audit",
                EntryType.FLAG_RAISED,
                Level.LOW,
                "Minor issue",
                "Some clause"
        );
        ledgerRepository.save(otherEntry);

        List<LedgerEntry> results = ledgerRepository.findByAuditIdOrderByTimestamp(auditId);

        assertEquals(2, results.size());
        assertNotNull(results.get(0).getId());
        assertEquals(EntryType.FLAG_RAISED, results.get(0).getType());
        assertEquals("Unlimited liability clause", results.get(0).getReason());

        assertNotNull(results.get(1).getId());
        assertEquals(EntryType.DECISION_MADE, results.get(1).getType());
        assertEquals("APPROVED", results.get(1).getReason());
    }
}
