package com.example.loop.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component // This filter will intercept incoming HTTP requests and check for the presence of a JWT token in the Authorization header. If a valid token is found, it will set the authentication in the security context so that the user is authenticated for that request.

@RequiredArgsConstructor // this annotation generates a constructor with required arguments (final fields) for dependency injection

public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil; // this is the utility class for handling JWT token operations, it will be injected by Spring
    private final CustomUserDetailService userDetailsService; // this is the service to load user details from the database, it will be injected by Spring

    @Value("${app.cookie.name}")
    private String cookieName;

   

    
    private Optional <String> extractTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty(); 

        return Arrays.stream(request.getCookies())
            .filter(cookie -> cookieName.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst();
    }



    
    // This method is called for each incoming HTTP request. It checks for the presence of a JWT token in the cookie, validates it, and sets the authentication in the security context if the token is valid.
    @Override
    protected void doFilterInternal( HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 1. Extract JWT token from the cookie
        Optional<String> tokenOpt = extractTokenFromCookie(request);

        if(tokenOpt.isEmpty()){
                    filterChain.doFilter(request, response); // if no token is found, continue the filter chain without setting authentication
                    return;
        }

            String token = tokenOpt.get(); // if a token is found, validate it and set authentication

            try {
                // 2. Extract username from the token
                String email = jwtUtil.extractUsername(token);

                // 3. Only set authentication if the security context does not already have an authenticated user
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null){
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email); // Load user details from the database using the email extracted from the token

                // 4. Validate the token against the user details
                    if (jwtUtil.validateToken(token, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, 
                            null, 
                            userDetails.getAuthorities()); // Create an authentication token with the user details and authorities
                        
                        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // 5. Set Authentication in context
                        SecurityContextHolder.getContext().setAuthentication(authToken); // Set the authentication in the security context

                    }
                }
            } catch (Exception e) {
                // for invalid or expired tokens 
                SecurityContextHolder.clearContext();
            }

            filterChain.doFilter(request, response);
    }



}
