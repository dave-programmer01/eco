package com.heraim.eco.dto;

import com.heraim.eco.model.Role;

public record RegisterRequest(
        String username,
        String email,
        String password,
        Role role
) {
    public RegisterRequest(String username, String email, String password) {
        this(username, email, password, Role.USER);
    }
}
