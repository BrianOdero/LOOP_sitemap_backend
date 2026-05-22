package com.example.loop.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.loop.user.User;
import com.example.loop.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor // this annotation generates a constructor with required arguments (final fields) for dependency injection
public class CustomUserDetailService implements UserDetailsService {

    private final UserRepository userRepository; // this is the repository to access user data from the database

    @Override // this method is used by Spring Security to load user details during authentication
    public UserDetails loadUserByUsername (String email) throws UsernameNotFoundException{
        User user = userRepository.findByEmail(email) // find the user by email
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email)); // if user is not found, throw an exception

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                // spring security expects roles prefixed wirh "ROLE_", so we need to add that prefix to each role
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build(); // build and return a UserDetails object with the user's email, password hash, and authorities (roles)
    }
}
