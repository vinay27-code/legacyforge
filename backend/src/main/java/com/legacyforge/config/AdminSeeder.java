package com.legacyforge.config;

import com.legacyforge.auth.entity.User;
import com.legacyforge.auth.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a default admin user on startup if it does not exist.
 * Configurable via app.admin.email and app.admin.password.
 */
@Configuration
public class AdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    @Bean
    public CommandLineRunner seedAdmin(
            UserRepository users,
            PasswordEncoder encoder,
            @Value("${app.admin.email:admin@legacyforge.dev}") String email,
            @Value("${app.admin.password:admin12345}") String password
    ) {
        return args -> ensureAdmin(users, encoder, email, password);
    }

    @Transactional
    void ensureAdmin(UserRepository users, PasswordEncoder encoder, String email, String password) {
        String normalized = email.toLowerCase().trim();
        if (users.existsByEmail(normalized)) {
            log.info("Admin user {} already exists; skipping seed", normalized);
            return;
        }
        User admin = new User(normalized, encoder.encode(password), User.Role.ADMIN);
        users.save(admin);
        log.info("Seeded ADMIN user: {}", normalized);
    }
}
