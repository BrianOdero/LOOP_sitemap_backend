package com.example.loop.config;

import com.example.loop.user.Role;
import com.example.loop.user.User;
import com.example.loop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        seedAdminUser();
    }

    private void seedAdminUser() {
        if (userRepository.count() > 0) {
            log.info("DevDataSeeder — users table already has data, skipping.");
            return;
        }

        User admin = User.builder()
                .email("admin@loop.com")
                .passwordHash(passwordEncoder.encode("Loop@Admin1"))
                .role(Role.ADMIN)
                .build();

        userRepository.save(admin);

        log.info("=================================================");
        log.info("DevDataSeeder — admin user created successfully.");
        log.info("  Email    : admin@loop.com");
        log.info("  Password : Loop@Admin1");
        log.info("=================================================");
    }
}