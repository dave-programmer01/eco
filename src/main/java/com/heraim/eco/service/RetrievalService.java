package com.heraim.eco.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalService {

    private final VectorStore vectorStore;

    public RetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> retrieve(String query) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(3)
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }
}
