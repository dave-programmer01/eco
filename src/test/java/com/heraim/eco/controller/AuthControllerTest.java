package com.heraim.eco.controller;

import com.heraim.eco.dto.AuthResponse;
import com.heraim.eco.dto.LoginRequest;
import com.heraim.eco.dto.RegisterRequest;
import com.heraim.eco.model.Role;
import com.heraim.eco.model.User;
import com.heraim.eco.repository.UserRepository;
import com.heraim.eco.service.AuthService;
import com.heraim.eco.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AuthControllerTest {

    static class FakeUserRepository implements UserRepository {
        final Map<Long, User> byId = new ConcurrentHashMap<>();
        final AtomicLong counter = new AtomicLong(1);

        @Override
        public <S extends User> S save(S entity) {
            if (entity.getId() == null) {
                entity.setId(counter.getAndIncrement());
            }
            byId.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public Optional<User> findById(Long aLong) {
            return Optional.ofNullable(byId.get(aLong));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return byId.values().stream().filter(u -> username.equals(u.getUsername())).findFirst();
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return byId.values().stream().filter(u -> email.equalsIgnoreCase(u.getEmail())).findFirst();
        }

        @Override
        public Optional<User> findByUsernameOrEmail(String username, String email) {
            return byId.values().stream()
                    .filter(u -> username.equals(u.getUsername()) || email.equalsIgnoreCase(u.getEmail()))
                    .findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return byId.values().stream().anyMatch(u -> username.equals(u.getUsername()));
        }

        @Override
        public boolean existsByEmail(String email) {
            return byId.values().stream().anyMatch(u -> email.equalsIgnoreCase(u.getEmail()));
        }

        @Override public boolean existsById(Long aLong) { return byId.containsKey(aLong); }
        @Override public List<User> findAll() { return new ArrayList<>(byId.values()); }
        @Override public List<User> findAllById(Iterable<Long> longs) { return List.of(); }
        @Override public long count() { return byId.size(); }
        @Override public void deleteById(Long aLong) { byId.remove(aLong); }
        @Override public void delete(User entity) { byId.remove(entity.getId()); }
        @Override public void deleteAllById(Iterable<? extends Long> longs) {}
        @Override public void deleteAll(Iterable<? extends User> entities) {}
        @Override public void deleteAll() { byId.clear(); }
        @Override public void flush() {}
        @Override public <S extends User> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends User> List<S> saveAllAndFlush(Iterable<S> entities) { return List.of(); }
        @Override public void deleteAllInBatch(Iterable<User> entities) {}
        @Override public void deleteAllByIdInBatch(Iterable<Long> longs) {}
        @Override public void deleteAllInBatch() {}
        @Override public User getOne(Long aLong) { return byId.get(aLong); }
        @Override public User getById(Long aLong) { return byId.get(aLong); }
        @Override public User getReferenceById(Long aLong) { return byId.get(aLong); }
        @Override public <S extends User> Optional<S> findOne(Example<S> example) { return Optional.empty(); }
        @Override public <S extends User> List<S> findAll(Example<S> example) { return List.of(); }
        @Override public <S extends User> List<S> findAll(Example<S> example, Sort sort) { return List.of(); }
        @Override public <S extends User> Page<S> findAll(Example<S> example, Pageable pageable) { return Page.empty(); }
        @Override public <S extends User> long count(Example<S> example) { return 0; }
        @Override public <S extends User> boolean exists(Example<S> example) { return false; }
        @Override public <S extends User, R> R findBy(Example<S> example, Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public <S extends User> List<S> saveAll(Iterable<S> entities) { return List.of(); }
        @Override public List<User> findAll(Sort sort) { return findAll(); }
        @Override public Page<User> findAll(Pageable pageable) { return Page.empty(); }
    }

    private FakeUserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthenticationManager authenticationManager;
    private AuthService authService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        userRepository = new FakeUserRepository();
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService("404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970", 3600000);
        authenticationManager = new AuthenticationManager() {
            @Override
            public Authentication authenticate(Authentication authentication) throws AuthenticationException {
                String principal = authentication.getName();
                String credentials = (String) authentication.getCredentials();
                User user = userRepository.findByUsernameOrEmail(principal, principal)
                        .orElseThrow(() -> new BadCredentialsException("User not found"));
                if (!passwordEncoder.matches(credentials, user.getPassword())) {
                    throw new BadCredentialsException("Invalid password");
                }
                return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            }
        };

        authService = new AuthService(userRepository, passwordEncoder, jwtService, authenticationManager);
        authController = new AuthController(authService);
    }

    @Test
    void testRegisterAndLoginFlow() {
        // 1. Register
        RegisterRequest registerRequest = new RegisterRequest("auditor1", "auditor1@example.com", "Secret123!", Role.USER);
        ResponseEntity<AuthResponse> registerResponse = authController.register(registerRequest);

        assertEquals(200, registerResponse.getStatusCode().value());
        assertNotNull(registerResponse.getBody());
        assertEquals("auditor1", registerResponse.getBody().username());
        assertEquals("auditor1@example.com", registerResponse.getBody().email());
        assertEquals(Role.USER, registerResponse.getBody().role());
        assertNotNull(registerResponse.getBody().token());
        assertEquals("Bearer", registerResponse.getBody().tokenType());

        // Verify user in repo has hashed password
        User savedUser = userRepository.findByUsername("auditor1").orElse(null);
        assertNotNull(savedUser);
        assertNotEquals("Secret123!", savedUser.getPassword());
        assertTrue(passwordEncoder.matches("Secret123!", savedUser.getPassword()));

        // 2. Login with valid credentials
        LoginRequest loginRequest = new LoginRequest("auditor1", "Secret123!");
        ResponseEntity<AuthResponse> loginResponse = authController.login(loginRequest);

        assertEquals(200, loginResponse.getStatusCode().value());
        assertNotNull(loginResponse.getBody());
        assertEquals("auditor1", loginResponse.getBody().username());
        assertNotNull(loginResponse.getBody().token());

        // Validate token with jwtService
        assertTrue(jwtService.isTokenValid(loginResponse.getBody().token(), savedUser));
    }

    @Test
    void testDuplicateUsernameRegistrationFails() {
        RegisterRequest req1 = new RegisterRequest("john_doe", "john@example.com", "pass123");
        authController.register(req1);

        RegisterRequest req2 = new RegisterRequest("john_doe", "john2@example.com", "pass456");
        assertThrows(ResponseStatusException.class, () -> authController.register(req2));
    }

    @Test
    void testDuplicateEmailRegistrationFails() {
        RegisterRequest req1 = new RegisterRequest("user_a", "shared@example.com", "pass123");
        authController.register(req1);

        RegisterRequest req2 = new RegisterRequest("user_b", "shared@example.com", "pass456");
        assertThrows(ResponseStatusException.class, () -> authController.register(req2));
    }

    @Test
    void testLoginWithWrongPasswordFails() {
        RegisterRequest registerRequest = new RegisterRequest("user_x", "user_x@example.com", "correctPassword");
        authController.register(registerRequest);

        LoginRequest loginRequest = new LoginRequest("user_x", "wrongPassword");
        assertThrows(BadCredentialsException.class, () -> authController.login(loginRequest));
    }
}
