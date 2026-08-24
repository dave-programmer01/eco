package com.heraim.eco.service;

import com.heraim.eco.model.AuditContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuditRegistry {

     private final Map<String, AuditContext> contextMap = new ConcurrentHashMap<>();

     public void save(AuditContext context) {
         contextMap.put(context.getContractId(), context);
     }

     public AuditContext get(String contractId) {
         return contextMap.get(contractId);
     }
}
