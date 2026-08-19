package com.ok.dto.response.admin;

import com.ok.domain.enums.Role;

public record AdminUserResponseDemo(
        Long userId,
        String fullName,
        String email,
        Role role
) {
}
