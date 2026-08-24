package com.heraim.eco.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RetrievalServiceTest {

    @Test
    void testRetrieve() {
        AtomicReference<SearchRequest> capturedRequest = new AtomicReference<>();
        List<Document> expectedDocuments = List.of(new Document("Regulation clause 1"));

        VectorStore fakeVectorStore = new VectorStore() {
            @Override
            public void add(List<Document> documents) {}

            @Override
            public void delete(List<String> idList) {}

            @Override
            public void delete(Filter.Expression filterExpression) {}

            @Override
            public List<Document> similaritySearch(SearchRequest request) {
                capturedRequest.set(request);
                return expectedDocuments;
            }
        };

        RetrievalService retrievalService = new RetrievalService(fakeVectorStore);
        List<Document> result = retrievalService.retrieve("hazardous waste compliance");

        assertEquals(expectedDocuments, result);
        SearchRequest req = capturedRequest.get();
        assertNotNull(req);
        assertEquals("hazardous waste compliance", req.getQuery());
        assertEquals(3, req.getTopK());
    }
}
