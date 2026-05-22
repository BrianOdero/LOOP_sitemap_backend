package com.example.loop.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email); // Find a user by their email address
    boolean existsByEmail(String email); // Check if a user with the given email already exists
}   
