package ke.blood.blood_backend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ke.blood.blood_backend.model.User;
import ke.blood.blood_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class UserController {

    private final UserRepository repo;

    // Admin-only: list all users
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<User> getAll() {
        return repo.findAll();
    }

    // Get own profile
    @GetMapping("/me")
    public ResponseEntity<?> getMe(Principal principal) {
        String username = principal.getName();
        return repo.findByUsername(username)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }

    // Get any user by ID
    @GetMapping("/{id}")
    public ResponseEntity<?> getOne(@PathVariable Long id) {
        return repo.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }

    // Update: only self or admin
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody User req,
                                    Principal principal) {
        return repo.findById(id).map(u -> {
            String currentUser = principal.getName();
            boolean isAdmin = repo.findByUsername(currentUser)
                    .map(user -> user.getRoles().stream().anyMatch(r -> r.name().equals("ROLE_ADMIN")))
                    .orElse(false);
            if (!isAdmin && !u.getUsername().equals(currentUser)) {
                return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
            }
            u.setFullName(req.getFullName());
            u.setEmail(req.getEmail());
            u.setBloodType(req.getBloodType());
            u.setAvailable(req.getAvailable());
            repo.save(u);
            return ResponseEntity.ok(u);
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "User not found")));
    }

    // Delete: admin only
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        if (!repo.existsById(id)) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        repo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "User deleted"));
    }
}
