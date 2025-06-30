package ke.blood.blood_backend;

import ke.blood.blood_backend.model.Role;
import ke.blood.blood_backend.model.User;
import ke.blood.blood_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        String adminUsername = "admin";
        String adminPassword = "adminpass";
        if (userRepository.existsByUsername(adminUsername)) {
            System.out.println("Admin user already exists.");
        } else {
            User admin = User.builder()
                    .username(adminUsername)
                    .password(passwordEncoder.encode(adminPassword))
                    .fullName("Administrator")
                    .email("admin@example.com")
                    .bloodType("")
                    .available(false)
                    .latitude(null)
                    .longitude(null)
                    .locationDescription("N/A")
                    .roles(Set.of(Role.ROLE_ADMIN))
                    .build();
            userRepository.save(admin);
            System.out.println("Created default admin user: " + adminUsername);
        }
    }
}
