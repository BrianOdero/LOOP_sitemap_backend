package com.example.loop.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
// ResponseEntity is a class that represents the entire HTTP response, including the status code, headers, and body. It allows you to customize the response sent back to the client when an exception occurs.
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException; // This exception is thrown when authentication fails due to invalid credentials, such as an incorrect username or password. It is commonly used in Spring Security to indicate that the provided credentials are not valid for authentication.
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.loop.common.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice // This annotation indicates that this class will handle exceptions globally for all controllers in the application.

public class GlobalExceptionHandler {

    // 404 Not Found
    @ExceptionHandler(ResourceNotFoundException.class) 
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request){

            // Build and return a custom error response with the appropriate HTTP status code and error message.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request)
            );
    }

    // 400 BAd request @valid features
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request){

        // Extract validation error messages
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining(", "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            buildError (HttpStatus.BAD_REQUEST, message, request)
        );
    }

    // 401 Unauthorized - bad credentials
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredential(BadCredentialsException ex, HttpServletRequest request){

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(), request)
        );
    }

    // 500 Internal Server Error - catch-all for unhandled exceptions
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, HttpServletRequest request){

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            buildError(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request)
        );
    }

    // ── Helper ───────────────────────────────────────────────
    private ErrorResponse buildError(HttpStatus status, String message,HttpServletRequest request) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .message(message)
                .path(request.getRequestURI())
                .build();
    }


}
