package com.heraim.eco.repository;

import com.heraim.eco.model.AuditContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditRepository extends JpaRepository<AuditContext, String> {
    List<AuditContext> findByOwnerId(String ownerId);
    List<AuditContext> findByOwnerIdOrderByContractIdDesc(String ownerId);
}
