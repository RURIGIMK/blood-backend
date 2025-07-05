package ke.blood.blood_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ke.blood.blood_backend.model.*;
import ke.blood.blood_backend.repository.*;
import ke.blood.blood_backend.service.AuditService;
import ke.blood.blood_backend.service.InventoryService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class AdminController {

    private final InventoryService inventoryService;
    private final UserRepository userRepository;
    private final DonationRecordRepository donationRecordRepository;
    private final BloodRequestRepository bloodRequestRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final AuditService auditService;

    @Operation(summary = "Get all inventory levels")
    @GetMapping("/inventory")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<Inventory> getInventory() {
        return inventoryService.getAllInventory();
    }

    @Operation(summary = "Set inventory for a blood type")
    @PutMapping("/inventory")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> setInventory(@RequestBody InventoryDTO dto, Principal principal) {
        Inventory inv = inventoryService.setInventory(dto.getBloodType(), dto.getQuantity());
        auditService.logEvent("INVENTORY_UPDATE",
                String.format("Admin set inventory: type=%s, qty=%d", dto.getBloodType(), dto.getQuantity()),
                userRepository.findByUsername(principal.getName()).orElse(null));
        return ResponseEntity.ok(inv);
    }

    @Getter @Setter
    static class InventoryDTO {
        private String bloodType;
        private Integer quantity;
    }

    @Operation(summary = "Get donor engagement metrics: donation frequency, new vs returning donors")
    @GetMapping("/metrics/donors")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> getDonorMetrics() {
        List<User> donors = userRepository.findAll().stream()
                .filter(u -> u.getRoles().contains(Role.ROLE_DONOR))
                .collect(Collectors.toList());

        int totalDonors = donors.size();
        Map<Long, Long> counts = donors.stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> (long) donationRecordRepository.findByDonor(u).size()
                ));
        long newDonors = counts.values().stream().filter(c -> c <= 1).count();
        long returning = counts.values().stream().filter(c -> c > 1).count();

        Map<String, Long> byDonor = new HashMap<>();
        for (User d : donors) {
            byDonor.put(d.getUsername(), counts.getOrDefault(d.getId(), 0L));
        }

        return Map.of(
                "totalDonors", totalDonors,
                "newDonors", newDonors,
                "returningDonors", returning,
                "donationCountsByDonor", byDonor
        );
    }

    @Operation(summary = "Get request fulfillment analytics: time-to-match for fulfilled requests")
    @GetMapping("/metrics/requests")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> getRequestMetrics() {
        List<BloodRequest> matched = bloodRequestRepository.findAll().stream()
                .filter(r -> r.getStatus() == RequestStatus.MATCHED || r.getStatus() == RequestStatus.FULFILLED)
                .collect(Collectors.toList());

        List<Long> times = matched.stream()
                .filter(r -> r.getMatchedAt() != null && r.getCreatedAt() != null)
                .map(r -> Duration.between(r.getCreatedAt(), r.getMatchedAt()).toMinutes())
                .collect(Collectors.toList());

        double avg = times.isEmpty() ? 0.0 : times.stream().mapToLong(Long::longValue).average().orElse(0.0);

        return Map.of(
                "totalMatchedRequests", matched.size(),
                "averageTimeToMatchMinutes", avg,
                "timesToMatchMinutes", times
        );
    }

    @Operation(summary = "Get basic system usage statistics")
    @GetMapping("/metrics/system")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> getSystemMetrics() {
        long users = userRepository.count();
        long requests = bloodRequestRepository.count();
        long donations = donationRecordRepository.count();
        long matches = matchRecordRepository.count();

        long available = userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getAvailable()))
                .count();

        return Map.of(
                "totalUsers", users,
                "totalRequests", requests,
                "totalDonations", donations,
                "totalMatches", matches,
                "availableDonors", available
        );
    }

    @Operation(summary = "Get audit logs")
    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<AuditLog> getAuditLogs() {
        return auditService.getAllLogs();
    }

    @Operation(summary = "Get real-time analytics (users/requests/donations/avg match time)")
    @GetMapping("/analytics")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> analytics() {
        long u = userRepository.count();
        long r = bloodRequestRepository.count();
        long d = donationRecordRepository.count();
        double avgM = bloodRequestRepository.findAll().stream()
                .filter(req -> req.getMatchedAt() != null && req.getCreatedAt() != null)
                .mapToLong(req -> Duration.between(req.getCreatedAt(), req.getMatchedAt()).toMinutes())
                .average()
                .orElse(0.0);

        return Map.of(
                "totalUsers", u,
                "totalRequests", r,
                "totalDonations", d,
                "avgMatchMinutes", avgM
        );
    }
}
