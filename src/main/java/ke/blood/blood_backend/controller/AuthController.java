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

    @Operation(summary = "Register a new user (donor, recipient, hospital)")
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (repo.existsByUsername(req.getUsername())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username exists"));
        }
        String inputRole = req.getRole().trim().toUpperCase();
        Role role;
        try {
            role = inputRole.startsWith("ROLE_")
                    ? Role.valueOf(inputRole)
                    : Role.valueOf("ROLE_" + inputRole);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error","Valid roles: donor, recipient, hospital"));
        }

        User user = User.builder()
                .username(req.getUsername())
                .password(encoder.encode(req.getPassword()))
                .fullName(req.getFullName())
                .email(req.getEmail())
                .bloodType(req.getBloodType())
                .available(true)  // now always true
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .locationDescription(req.getLocationDescription())
                .roles(Set.of(role))
                .build();

        User saved = repo.save(user);
        auditService.logEvent("USER_REGISTER",
                "Registered " + saved.getUsername(), saved);
        return ResponseEntity.ok(Map.of("message","User registered"));
    }

    @Operation(summary = "Login user and obtain JWT token")
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        var userOpt = repo.findByUsername(req.getUsername());
        if (userOpt.isEmpty() ||
                !encoder.matches(req.getPassword(),userOpt.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error","Invalid credentials"));
        }
        String token = jwtUtil.generateToken(req.getUsername());
        auditService.logEvent("USER_LOGIN","Login "+req.getUsername(), userOpt.get());
        return ResponseEntity.ok(Map.of("token", token));
    }

    @Getter @Setter static class RegisterRequest {
        private String username, password, fullName, email, bloodType, role;
        private Double latitude, longitude;
        private String locationDescription;
    }
    @Getter @Setter static class LoginRequest {
        private String username, password;
    }
}
