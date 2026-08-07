package com.ok.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        
        @NotBlank(message = "Email không được để trống")
        @Email(message = "Email không đúng định dạng") 
        String email,

        @NotBlank(message = "Mật khẩu không được để trống")
        String password
) {
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
