package com.heraim.eco.service;

import com.heraim.eco.model.Role;
import com.heraim.eco.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String testSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private final long expirationMs = 3600000; // 1 hour

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(testSecret, expirationMs);
    }

    @Test
    void testGenerateAndValidateToken() {
        User user = new User("sarah_auditor", "sarah@example.com", "hash", Role.USER);
        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertFalse(token.isBlank());

        String extractedUsername = jwtService.extractUsername(token);
        assertEquals("sarah_auditor", extractedUsername);

        assertTrue(jwtService.isTokenValid(token, user));
        assertTrue(jwtService.isTokenValid(token));
    }

    @Test
    void testTokenWithExtraClaims() {
        User user = new User("admin_user", "admin@example.com", "hash", Role.ADMIN);
        String token = jwtService.generateToken(Map.of("role", "ROLE_ADMIN", "customField", "12345"), user);

        assertNotNull(token);
        assertEquals("admin_user", jwtService.extractUsername(token));

        String customField = jwtService.extractClaim(token, claims -> claims.get("customField", String.class));
        assertEquals("12345", customField);
    }

    @Test
    void testTokenValidationFailsForDifferentUser() {
        User user1 = new User("user1", "user1@example.com", "hash", Role.USER);
        User user2 = new User("user2", "user2@example.com", "hash", Role.USER);

        String token = jwtService.generateToken(user1);
        assertFalse(jwtService.isTokenValid(token, user2));
    }

    @Test
    void testExpiredTokenValidationFails() {
        // JwtService with -1000ms expiration (already expired)
        JwtService expiredJwtService = new JwtService(testSecret, -1000);
        User user = new User("user1", "user1@example.com", "hash", Role.USER);

        String token = expiredJwtService.generateToken(user);
        assertFalse(expiredJwtService.isTokenValid(token));
    }

    @Test
    void testTamperedTokenValidationFails() {
        User user = new User("user1", "user1@example.com", "hash", Role.USER);
        String token = jwtService.generateToken(user);
        String tamperedToken = token + "tampered";

        assertFalse(jwtService.isTokenValid(tamperedToken));
    }
}
