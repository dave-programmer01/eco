package com.heraim.eco.repository;

import com.heraim.eco.model.Role;
import com.heraim.eco.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void testSaveAndRetrieveUserWithHashedPassword() {
        String rawPassword = "superSecretPassword123!";
        String hashedPassword = passwordEncoder.encode(rawPassword);

        assertNotEquals(rawPassword, hashedPassword);
        assertTrue(passwordEncoder.matches(rawPassword, hashedPassword));

        User user = new User("compliance_officer", "officer@example.com", hashedPassword, Role.USER);
        User saved = userRepository.save(user);

        assertNotNull(saved.getId());
        assertEquals("compliance_officer", saved.getUsername());
        assertEquals("officer@example.com", saved.getEmail());
        assertEquals(hashedPassword, saved.getPassword());
        assertEquals(Role.USER, saved.getRole());
        assertNotNull(saved.getCreatedAt());

        // Retrieve by username
        Optional<User> byUsername = userRepository.findByUsername("compliance_officer");
        assertTrue(byUsername.isPresent());
        assertTrue(passwordEncoder.matches(rawPassword, byUsername.get().getPassword()));

        // Retrieve by email
        Optional<User> byEmail = userRepository.findByEmail("officer@example.com");
        assertTrue(byEmail.isPresent());
        assertEquals(saved.getId(), byEmail.get().getId());

        // Retrieve by username or email
        Optional<User> byEither = userRepository.findByUsernameOrEmail("compliance_officer", "compliance_officer");
        assertTrue(byEither.isPresent());

        // Check existence
        assertTrue(userRepository.existsByUsername("compliance_officer"));
        assertTrue(userRepository.existsByEmail("officer@example.com"));
        assertFalse(userRepository.existsByUsername("non_existent"));
    }
}
