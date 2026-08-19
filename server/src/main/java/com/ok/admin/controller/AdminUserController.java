package com.ok.admin.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ok.admin.service.AdminUserServiceDemo;
import com.ok.dto.response.admin.AdminUserResponseDemo;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserControllerDemo {

    private final AdminUserServiceDemo adminUserService;

    @GetMapping
    public List<AdminUserResponseDemo> getUsers(Principal principal) {
        return adminUserService.getUsers(principal.getName());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long userId, Principal principal) {
        adminUserService.deleteUser(userId, principal.getName());
    }
}
