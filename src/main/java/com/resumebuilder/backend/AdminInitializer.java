package com.resumebuilder.backend;

import com.resumebuilder.backend.model.AppUser;
import com.resumebuilder.backend.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // Admin Bootstrapping
        Optional<AppUser> adminOpt = userRepository.findByEmail("admin@resumebuilder.com");
        if (adminOpt.isEmpty()) {
            AppUser admin = AppUser.builder()
                    .email("admin@resumebuilder.com")
                    .name("Admin")
                    .password(passwordEncoder.encode("admin"))
                    .provider("LOCAL")
                    .role("ADMIN")
                    .subscriptionTier("PAID")
                    .verified(true)
                    .build();
            userRepository.save(admin);
            System.out.println("Admin user bootstrapped: admin@resumebuilder.com / admin");
        } else {
            AppUser admin = adminOpt.get();
            boolean changed = false;
            if (!"ADMIN".equals(admin.getRole())) {
                admin.setRole("ADMIN");
                changed = true;
            }
            if (!admin.getVerified()) {
                admin.setVerified(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(admin);
            }
        }

        // Standard User Bootstrapping
        Optional<AppUser> userOpt = userRepository.findByEmail("user@resumebuilder.com");
        if (userOpt.isEmpty()) {
            AppUser user = AppUser.builder()
                    .email("user@resumebuilder.com")
                    .name("Regular User")
                    .password(passwordEncoder.encode("user"))
                    .provider("LOCAL")
                    .role("USER")
                    .subscriptionTier("FREE")
                    .verified(true)
                    .build();
            userRepository.save(user);
            System.out.println("Standard user bootstrapped: user@resumebuilder.com / user");
        } else {
            AppUser user = userOpt.get();
            boolean changed = false;
            if (!"USER".equals(user.getRole())) {
                user.setRole("USER");
                changed = true;
            }
            if (!user.getVerified()) {
                user.setVerified(true);
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
            }
        }
    }
}
