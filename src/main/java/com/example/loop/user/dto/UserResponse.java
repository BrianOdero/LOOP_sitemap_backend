package com.example.loop.user.dto;

import java.time.LocalDateTime;

import com.example.loop.user.Role;

import lombok.Builder;
import lombok.Data;

@Data
@Builder

public class UserResponse {
    private Long id;
    private String email;
    private Role role;
    private LocalDateTime createdAt;
}
