package com.heraim.eco.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Component
public class CorpusLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CorpusLoader.class);

    @Value("classpath:regulations.md")
    private Resource resource;

    private final VectorStore vectorStore;

    public CorpusLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) throws Exception {
        String corpus = resource.getContentAsString(StandardCharsets.UTF_8);

        // Rules are separated by blank lines and each begins with "[ID]".
        // Filtering on that prefix drops the "#" comment header.
        List<Document> documents = Arrays.stream(corpus.split("\\R\\s*\\R"))
            .map(String::trim)
            .filter(block -> block.startsWith("["))
            .map(Document::new)
            .toList();

        vectorStore.add(documents);
        log.info("ingested {} rules", documents.size());
    }
}
