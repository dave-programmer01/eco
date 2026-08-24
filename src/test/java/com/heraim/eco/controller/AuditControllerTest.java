package com.heraim.eco.controller;

import com.heraim.eco.service.AuditRegistry;
import com.heraim.eco.service.AuditStateMachine;
import com.heraim.eco.service.RetrievalService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditControllerTest {

    @Test
    void testSearchEndpoint() {
        RetrievalService fakeRetrievalService = new RetrievalService(null) {
            @Override
            public List<Document> retrieve(String query) {
                return List.of(new Document("Result for " + query));
            }
        };

        AuditController controller = new AuditController(null, null, fakeRetrievalService);
        List<Document> results = controller.search("test query");

        assertEquals(1, results.size());
        assertEquals("Result for test query", results.getFirst().getText());
    }
}
