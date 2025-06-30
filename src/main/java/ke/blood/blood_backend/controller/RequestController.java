package ke.blood.blood_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ke.blood.blood_backend.model.*;
import ke.blood.blood_backend.repository.BloodRequestRepository;
import ke.blood.blood_backend.repository.UserRepository;
import ke.blood.blood_backend.service.AuditService;
import ke.blood.blood_backend.service.MatchingService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/requests")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class RequestController {

    private final UserRepository userRepository;
    private final BloodRequestRepository requestRepository;
    private final MatchingService matchingService;
    private final AuditService auditService;

    @Operation(summary = "Submit a new blood request (role RECIPIENT or HOSPITAL)")
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_RECIPIENT') or hasAuthority('ROLE_HOSPITAL')")
    public ResponseEntity<?> submitRequest(@RequestBody RequestDTO dto, Principal principal) {
        String username = principal.getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        User requester = userOpt.get();

        BloodRequest req = BloodRequest.builder()
                .requester(requester)
                .bloodType(dto.getBloodType())
                .quantity(dto.getQuantity())
                .urgencyLevel(dto.getUrgencyLevel())
                .hospitalName(dto.getHospitalName())
                .hospitalLatitude(dto.getHospitalLatitude())
                .hospitalLongitude(dto.getHospitalLongitude())
                .locationDescription(dto.getLocationDescription())
                .status(RequestStatus.PENDING)
                .build();
        BloodRequest saved = requestRepository.save(req);

        auditService.logEvent("REQUEST_SUBMIT",
                "User " + username + " submitted request ID " + saved.getId(),
                requester);

        matchingService.matchRequest(saved);

        return ResponseEntity.ok(saved);
    }

    @Operation(summary = "Get all requests (admin only)")
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<BloodRequest> getAllRequests() {
        return requestRepository.findAll();
    }

    @Operation(summary = "Get my own requests")
    @GetMapping("/me")
    public List<BloodRequest> getMyRequests(Principal principal) {
        String username = principal.getName();
        return userRepository.findByUsername(username)
                .map(user -> requestRepository.findAll().stream()
                        .filter(r -> r.getRequester().getId().equals(user.getId()))
                        .toList())
                .orElse(List.of());
    }

    @Operation(summary = "Get request by ID")
    @GetMapping("/{id}")
    public ResponseEntity<?> getRequest(@PathVariable Long id) {
        return requestRepository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(404).body(Map.of("error", "Request not found")));
    }

    @Operation(summary = "Cancel a pending request (only requester or admin)")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelRequest(@PathVariable Long id, Principal principal) {
        String username = principal.getName();
        return requestRepository.findById(id).map(r -> {
            boolean isAdmin = userRepository.findByUsername(username)
                    .map(u -> u.getRoles().stream().anyMatch(role -> role.name().equals("ROLE_ADMIN")))
                    .orElse(false);
            if (!isAdmin && !r.getRequester().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
            }
            if (r.getStatus() != RequestStatus.PENDING) {
                return ResponseEntity.badRequest().body(Map.of("error", "Only pending requests can be cancelled"));
            }
            r.setStatus(RequestStatus.CANCELLED);
            requestRepository.save(r);
            auditService.logEvent("REQUEST_CANCEL",
                    "Request ID " + id + " cancelled by " + username,
                    userRepository.findByUsername(username).orElse(null));
            return ResponseEntity.ok(Map.of("message", "Request cancelled"));
        }).orElse(ResponseEntity.status(404).body(Map.of("error", "Request not found")));
    }

    @Getter @Setter
    static class RequestDTO {
        private String bloodType;
        private Integer quantity;
        private UrgencyLevel urgencyLevel;
        private String hospitalName;
        private Double hospitalLatitude;
        private Double hospitalLongitude;
        private String locationDescription;
    }
}
