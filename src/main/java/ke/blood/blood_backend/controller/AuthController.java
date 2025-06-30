package ke.blood.blood_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import ke.blood.blood_backend.model.Role;
import ke.blood.blood_backend.model.User;
import ke.blood.blood_backend.repository.UserRepository;
import ke.blood.blood_backend.security.JwtUtil;
import ke.blood.blood_backend.service.AuditService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;

    @Operation(summary = "Register a new user (donor, recipient, hospital, or admin)")
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (repo.existsByUsername(req.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }

        String inputRole = req.getRole().trim().toUpperCase();
        Role role;
        try {
            if (inputRole.startsWith("ROLE_")) {
                role = Role.valueOf(inputRole);
            } else {
                role = Role.valueOf("ROLE_" + inputRole);
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Invalid role. Valid roles: admin, donor, recipient, hospital")
            );
        }

        User user = User.builder()
                .username(req.getUsername())
                .password(encoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .email(req.getEmail())
                .bloodType(req.getBloodType())
                .available(Boolean.TRUE.equals(req.getAvailable()))
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .locationDescription(req.getLocationDescription())
                .roles(Set.of(role))
                .build();

        User saved = repo.save(user);

        auditService.logEvent("USER_REGISTER",
                "New user registered: " + saved.getUsername() + " with role " + role.name(),
                saved);

        return ResponseEntity.ok(Map.of("message", "User registered"));
    }

    @Operation(summary = "Login user and obtain JWT token")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        var userOpt = repo.findByUsername(req.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        User user = userOpt.get();
        if (!encoder.matches(req.getPassword(), user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
        String token = jwtUtil.generateToken(user.getUsername());

        auditService.logEvent("USER_LOGIN", "User logged in: " + user.getUsername(), user);

        return ResponseEntity.ok(Map.of("token", token));
    }

    @Getter @Setter
    static class RegisterRequest {
        private String username;
        private String password;
        private String fullName;
        private String email;
        private String bloodType;
        private String role;
        private Boolean available;
        private Double latitude;
        private Double longitude;
        private String locationDescription;
    }

    @Getter @Setter
    static class LoginRequest {
        private String username;
        private String password;
    }
}
