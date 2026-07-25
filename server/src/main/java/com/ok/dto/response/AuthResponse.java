package com.ok.dto.response;

import com.ok.domain.enums.Role;

public record AuthResponse(
        String token,
        String tokenType,
        Long userId,
        String fullName,
        String email,
        Role role
) {
}

