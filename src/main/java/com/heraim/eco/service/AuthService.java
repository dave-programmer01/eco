package com.heraim.eco.service;

import com.heraim.eco.dto.AuthResponse;
import com.heraim.eco.dto.LoginRequest;
import com.heraim.eco.dto.RegisterRequest;
import com.heraim.eco.model.Role;
import com.heraim.eco.model.User;
import com.heraim.eco.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest request) {
        if (request.username() == null || request.username().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username cannot be empty");
        }
        if (request.email() == null || request.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email cannot be empty");
        }
        if (request.password() == null || request.password().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password cannot be empty");
        }
        if (userRepository.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is already in use");
        }

        Role role = request.role() != null ? request.role() : Role.USER;
        String encodedPassword = passwordEncoder.encode(request.password());
        User user = new User(request.username(), request.email(), encodedPassword, role);
        User savedUser = userRepository.save(user);

        String token = jwtService.generateToken(savedUser);
        return new AuthResponse(token, savedUser.getUsername(), savedUser.getEmail(), savedUser.getRole());
    }

    public AuthResponse login(LoginRequest request) {
        if (request.username() == null || request.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password are required");
        }

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        User user = userRepository.findByUsernameOrEmail(request.username(), request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getEmail(), user.getRole());
    }
}
