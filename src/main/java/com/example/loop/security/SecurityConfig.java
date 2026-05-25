package com.example.loop.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration // This class will contain security configurations for the application
@EnableWebSecurity // This annotation enables Spring Security's web security support and provides the Spring MVC integration
@EnableMethodSecurity // This annotation allows us to use method-level security annotations like @PreAuthorize and @PostAuthorize
@RequiredArgsConstructor // This annotation generates a constructor with required arguments (final fields)



public class SecurityConfig {
    
    private final JwtAuthFilter jwtAuthFilter; // This is a custom filter that will handle JWT authentication
    private final CustomUserDetailService userDetailsService; // This service will load user-specific data during authentication

    // -------------------- Security Filter Chain ----------------------------
    @Bean 
    // A bean is a managed object that is created, configured, and managed by the Spring container. By annotating a method with @Bean, you are telling Spring to execute that method and register its return value as a bean in the application context. This allows you to define and configure beans in a centralized way, and they can be injected into other parts of the application as needed.

    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable) // This line disables Cross-Site Request Forgery (CSRF) protection, which is often necessary for stateless APIs that use tokens for authentication instead of cookies.
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) 
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) 
            // auth endpoint are fully public
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/markets").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/segments").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/catalogue/**").permitAll()
                // all other endpoints require authentication
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvidor())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
            
    }

    // -------------------- Authentication Provider ----------------------------

    @Bean
    public AuthenticationProvider authenticationProvidor() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean // This method defines the password encoder that will be used to encode and verify passwords during authentication. In this case, it uses BCryptPasswordEncoder, which is a strong hashing function that is widely used for password hashing.
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // -------------Password Encoder ----------------------------
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // -------------------- CORS Configuration ----------------------------
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        
         CorsConfiguration config = new CorsConfiguration();

        // Allowing the frontend vite servers 
        config.addAllowedOrigin("http://localhost:5173"); // vite dev server
        config.addAllowedOrigin("http://localhost:3000"); // React dev server

        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        //Allowing the http cookie to flow with the request
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(); // This class is used to define CORS configuration based on URL patterns
        source.registerCorsConfiguration("/**", config); // This method registers the CORS configuration for all URL patterns (/**)
        return source;
    }

    
}
