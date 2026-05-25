package com.example.loop.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.example.loop.auth.dto.AuthResponse;
import com.example.loop.auth.dto.LoginRequest;
import com.example.loop.security.JwtUtil;
import com.example.loop.user.User;
import com.example.loop.user.UserRepository;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;


@Service
@RequiredArgsConstructor


public class AuthService {
    // these are needed to perform authentication and generate JWT tokens
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Value("${app.cookie.name}")
    private String cookieName; // the name of the cookie to store the JWT token

    @Value("${app.cookie.max-age-seconds}")
    private int cookieMaxAge; // the max age of the cookie in seconds

    //-------LOGIN----------
   public AuthResponse Login(LoginRequest request, HttpServletResponse response) {

        // 1. Authenticate
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );

        // 2. Generate JWT
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails);  // ← lowercase jwtUtil

        // 3. Set httpOnly cookie
        Cookie cookie = buildCookie(token, cookieMaxAge);   // ← use the helper
        response.addCookie(cookie);

        // 4. Return response body
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow();

        return AuthResponse.builder()
            .email(user.getEmail())
            .role(user.getRole().name())
            .message("Login successful")
            .build();
    }

    //-------LOGOUT----------
    public void Logout(HttpServletResponse response){
        Cookie cookie = buildCookie("", 0); // create a cookie with null value and max age 0 to delete it
        response.addCookie(cookie);
    }

    // helper method to build a cookie with the given value and max age
    private Cookie buildCookie(String value, int maxAge) {
        Cookie cookie = new Cookie(cookieName, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/"); // set the path to the root of the application
        cookie.setMaxAge(maxAge);
        return cookie;
    }

    //-------GET CURRENT USER----------
    public AuthResponse getCurrentUser(String email){
        User user = userRepository.findByEmail(email).orElseThrow();

        return AuthResponse.builder()
            .email(user.getEmail())
            .role(user.getRole().name())
            .build();
    }


}
