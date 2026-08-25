package com.heraim.eco.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorsConfigTest {

    @Test
    void testCorsMappingsConfiguration() {
        SecurityConfig securityConfig = new SecurityConfig(null, null);
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/audit");
        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        assertTrue(config.getAllowedOrigins().contains("http://localhost:3000"));
        assertTrue(config.getAllowedOrigins().contains("http://127.0.0.1:3000"));
        assertTrue(config.getAllowedMethods().contains("GET"));
        assertTrue(config.getAllowedMethods().contains("POST"));
        assertTrue(config.getAllowedMethods().contains("PUT"));
        assertTrue(config.getAllowedMethods().contains("DELETE"));
        assertTrue(config.getAllowedMethods().contains("PATCH"));
        assertTrue(config.getAllowedMethods().contains("OPTIONS"));
        assertTrue(config.getAllowedHeaders().contains("*"));
        assertEquals(Boolean.TRUE, config.getAllowCredentials());
        assertEquals(3600L, config.getMaxAge());
    }
}
