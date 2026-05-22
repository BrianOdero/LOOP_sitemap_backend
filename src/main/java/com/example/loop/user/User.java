package com.example.loop.user;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;

import jakarta.persistence.*;
import lombok.*;
import lombok.Builder.Default;

@Entity
@Table(name = "users")
@Data // setters and getters declaration using lombok
@Builder 

// empoty and value filled constructors using lombok
@AllArgsConstructor
@NoArgsConstructor

// Declaring the user entity for postgres usage
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 20)
    @Default
    private Role role = Role.USER;

    @Column(name = "created_at",nullable = false, updatable = false)

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

}
