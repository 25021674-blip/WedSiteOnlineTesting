package com.ok.admin.service;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.ok.domain.enums.Role;
import com.ok.dto.response.admin.AdminUserResponse;
import com.ok.entity.UserEntity;
import com.ok.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> getUsers(String adminEmail) {
        requireAdmin(adminEmail);

        return userRepository.findAllByOrderByFullNameAscIdAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteUser(Long userId, String adminEmail) {
        UserEntity admin = requireAdmin(adminEmail);

        if (admin.getId().equals(userId)) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Quản trị viên không thể tự xóa tài khoản đang đăng nhập"
            );
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Không tìm thấy người dùng"
                ));

        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Không thể xóa người dùng đang có dữ liệu bài kiểm tra liên quan",
                    exception
            );
        }
    }

    private UserEntity requireAdmin(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "Yêu cầu đăng nhập");
        }

        UserEntity user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(
                        UNAUTHORIZED,
                        "Không tìm thấy tài khoản đang đăng nhập"
                ));

        if (user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Chỉ quản trị viên được quản lý người dùng"
            );
        }

        return user;
    }

    private AdminUserResponse toResponse(UserEntity user) {
        return new AdminUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
