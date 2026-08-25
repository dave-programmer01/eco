package com.heraim.eco;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

@SpringBootTest(properties = {
        "spring.ai.openai.api-key=test-key",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class EcoApplicationTests {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public VectorStore testVectorStore() {
            return new VectorStore() {
                @Override
                public void add(List<Document> documents) {}

                @Override
                public void delete(List<String> idList) {}

                @Override
                public void delete(Filter.Expression filterExpression) {}

                @Override
                public List<Document> similaritySearch(SearchRequest request) {
                    return List.of();
                }
            };
        }
    }

    @Test
    void contextLoads() {
    }

}
