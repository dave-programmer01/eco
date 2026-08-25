package com.heraim.eco.repository;

import com.heraim.eco.model.AuditContext;
import com.heraim.eco.model.AuditState;
import com.heraim.eco.model.Decision;
import com.heraim.eco.model.Level;
import com.heraim.eco.model.RiskFlag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class AuditRepositoryTest {

    @Autowired
    private AuditRepository auditRepository;

    @Test
    void testSaveAndRetrieveAuditContextWithFlags() {
        AuditContext context = new AuditContext("Party A shall be liable for all damages without limitation.");
        RiskFlag flag1 = new RiskFlag(Level.HIGH, "Unlimited liability", "Party A shall be liable for all damages without limitation.");
        RiskFlag flag2 = new RiskFlag(Level.LOW, "Minor typo", "Party A");
        context.addFlag(flag1);
        context.addFlag(flag2);
        context.setState(AuditState.HUMAN_REVIEW);

        AuditContext saved = auditRepository.save(context);

        assertNotNull(saved.getContractId());
        Optional<AuditContext> retrievedOpt = auditRepository.findById(saved.getContractId());
        assertTrue(retrievedOpt.isPresent());

        AuditContext retrieved = retrievedOpt.get();
        assertEquals("Party A shall be liable for all damages without limitation.", retrieved.getContractText());
        assertEquals(AuditState.HUMAN_REVIEW, retrieved.getState());
        assertEquals(2, retrieved.getFlags().size());
        assertTrue(retrieved.isAwaitingHuman());
        assertNotNull(retrieved.getFlags().getFirst().getAudit());
        assertEquals(retrieved.getContractId(), retrieved.getFlags().getFirst().getAudit().getContractId());

        // Update decision
        retrieved.decide(flag1.getFlagId(), Decision.APPROVED);
        AuditContext updated = auditRepository.save(retrieved);

        Optional<AuditContext> reloadedOpt = auditRepository.findById(updated.getContractId());
        assertTrue(reloadedOpt.isPresent());
        AuditContext reloaded = reloadedOpt.get();
        assertEquals(Decision.APPROVED, reloaded.getFlags().getFirst().getDecision());
    }

    @Test
    void testFindAllAudits() {
        AuditContext context1 = new AuditContext("Contract 1");
        AuditContext context2 = new AuditContext("Contract 2");

        auditRepository.save(context1);
        auditRepository.save(context2);

        List<AuditContext> all = auditRepository.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    void testFindByOwnerId() {
        AuditContext user1Audit = new AuditContext("User 1 Contract", "user-123");
        AuditContext user2Audit = new AuditContext("User 2 Contract", "user-456");

        auditRepository.save(user1Audit);
        auditRepository.save(user2Audit);

        List<AuditContext> user1Audits = auditRepository.findByOwnerId("user-123");
        assertEquals(1, user1Audits.size());
        assertEquals("User 1 Contract", user1Audits.getFirst().getContractText());
        assertEquals("user-123", user1Audits.getFirst().getOwnerId());
    }
}
