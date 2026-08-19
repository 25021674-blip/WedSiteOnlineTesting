package com.ok.repository;

import com.ok.entity.UserEntity;
import com.ok.domain.enums.Role;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long>{
    Optional<UserEntity> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<UserEntity> findByRoleOrderByFullNameAscIdAsc(Role role);
    List<UserEntity> findAllByOrderByFullNameAscIdAsc();
} 
