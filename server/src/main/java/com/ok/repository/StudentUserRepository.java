package com.ok.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ok.entity.UserEntity;

import jakarta.persistence.LockModeType;

public interface StudentUserRepository
        extends JpaRepository<UserEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT student
            FROM UserEntity student
            WHERE LOWER(student.email) = LOWER(:email)
            """)
    Optional<UserEntity> findByEmailForUpdate(@Param("email") String email);
}
