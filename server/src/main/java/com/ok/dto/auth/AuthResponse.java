package com.ok.dto.auth;

import com.ok.dto.common.Role;

public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String fullName,
        String email,
        Role role
) {
}

