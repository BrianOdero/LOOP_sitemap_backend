package com.example.loop.auth;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.loop.auth.dto.AuthResponse;
import com.example.loop.auth.dto.LoginRequest;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor


public class AuthController {
    private final AuthService authService;

    //--------POST API/AUTH/LOGIN----------
    // this endpoint will be called by the frontend when the user tries to log in
    // It will return email and role in the respomnse body, and set the JWT token in an httpOnly cookie
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
 
        AuthResponse authResponse = authService.Login(request, response);
        return ResponseEntity.ok(authResponse);
    }

    //--------POST API/AUTH/LOGOUT----------
    // clears the JWT token from the httpOnly cookie by setting the cookie with max age 0
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response){
        authService.Logout(response);
        return ResponseEntity.noContent().build();
    }

    //--------GET API/AUTH/ME----------
    // this endpoint can be used by the frontend to get the current user's info (email and role) by reading the JWT token from the cookie and extracting the info from it and will return 401 if the token is missing or invalid
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        AuthResponse authResponse = authService.getCurrentUser(userDetails.getUsername());
        return ResponseEntity.ok(authResponse);
    }
}
