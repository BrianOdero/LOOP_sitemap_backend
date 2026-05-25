package com.example.loop.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data 
@Builder 
public class AuthResponse {
    public String email;
    public String role;
    public String message;
}
