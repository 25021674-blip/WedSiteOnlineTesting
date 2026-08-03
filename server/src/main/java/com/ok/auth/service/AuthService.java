package com.ok.auth.service;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import com.ok.domain.enums.Role;
import com.ok.dto.request.LoginRequest;
import com.ok.dto.request.RegisterRequest;
import com.ok.dto.response.AuthResponse;
import com.ok.repository.UserRepository;
import com.ok.entity.UserEntity;

import lombok.AllArgsConstructor;

@AllArgsConstructor
@Service
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse registerStudent(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(CONFLICT, "Email đã được sử dụng");
        }

        UserEntity user = new UserEntity(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Role.STUDENT
        );
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public AuthResponse registerTeacher(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(CONFLICT, "Email đã được sử dụng");
        }

        UserEntity teacher = new UserEntity(
                request.fullName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                Role.TEACHER
        );

        return toResponse(userRepository.save(teacher));
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "Email hoặc mật khẩu không đúng"
                ));

        return new AuthResponse(
                jwtService.generateToken(user),
                "Bearer",
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole()
        );
    }

    private AuthResponse toResponse(UserEntity user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer", user.getId(), user.getFullName(), user.getEmail(), user.getRole());
    }
}
