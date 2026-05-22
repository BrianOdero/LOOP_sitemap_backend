package com.example.loop.common;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder

public class ErrorResponse {
    
    private LocalDateTime timestamp;
    private String message;
    private String path;
    private int status;
}
