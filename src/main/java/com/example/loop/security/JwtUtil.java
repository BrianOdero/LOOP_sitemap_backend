package com.example.loop.security;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component // This class will contain utility methods for generating and validating JWT tokens
public class JwtUtil {

    @Value("${jwt.secret}") 
    private String secret;

    @Value("${jwt.expiration-ms}")
    private Long expirationMs;

    //TOKEN GENERATION
    public String generateToken(UserDetails userDetails) {
        
        Map<String, Object> claims = new HashMap<>(); // this is used to store any additional information you want to include in the token

        claims.put("Role", userDetails.getAuthorities().stream()
        .findFirst()
        .map(a -> a.getAuthority().replace("ROLE_", ""))
        .orElse("USER")); // this is used to store the user's role in the token, you can modify this to include more roles if needed

        return buildToken(claims, userDetails.getUsername()); 
        
    }

    //buildtoken helper method to create the token using the claims and subject (username)
    private String buildToken(Map<String, Object> extraClaims, String subject) {
        
        return Jwts.builder()
            .claims(extraClaims)
            .subject(subject)
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(getSigningKey())
            .compact(); // this will create the token using the claims, subject, issued date, expiration date, and signing it with the secret key
    }

    // TOKEN VALIDATION
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token); 
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // CLAIM EXTRACTIONS

    // claims helper functions are the extraClaims and extractAllClaims
    private <T>  T extractClaim(String token, Function<Claims, T> claimsResolver){
        final Claims claims = extractAllClaims(token); 
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token){
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public String extractUsername(String token){
        return extractClaim(token , Claims::getSubject);
    }

    public String extractRole(String token){
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    private boolean isTokenExpired(String token){
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    // KEY
    private SecretKey getSigningKey() {
        // The secret is stored as a plain UTF-8 string in properties.
        // Keys.hmacShaKeyFor requires the raw bytes — encode to Base64 first,
        // or use the bytes directly as below.
        byte[] keyBytes = secret.getBytes(); // Convert the secret string to bytes
        return Keys.hmacShaKeyFor(keyBytes); // Generate the signing key using the byte array
    }
    
}
