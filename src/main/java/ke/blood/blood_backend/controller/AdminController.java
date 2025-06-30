package ke.blood.blood_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ke.blood.blood_backend.model.*;
import ke.blood.blood_backend.repository.BloodRequestRepository;
import ke.blood.blood_backend.repository.DonationRecordRepository;
import ke.blood.blood_backend.service.InventoryService;
import ke.blood.blood_backend.repository.MatchRecordRepository;
import ke.blood.blood_backend.repository.UserRepository;
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
                .filter(u -> u.getRoles().stream().anyMatch(r -> r.name().equals("ROLE_DONOR")))
                .collect(Collectors.toList());
        int totalDonors = donors.size();

        Map<Long, Long> donorDonationCounts = donors.stream()
                .collect(Collectors.toMap(
                        u -> u.getId(),
                        u -> (long) donationRecordRepository.findByDonor(u).size()
                ));
        long newDonors = donorDonationCounts.values().stream().filter(c -> c <= 1).count();
        long returningDonors = donorDonationCounts.values().stream().filter(c -> c > 1).count();

        Map<String, Long> freqMap = new HashMap<>();
        for (User donor : donors) {
            freqMap.put(donor.getUsername(), donorDonationCounts.getOrDefault(donor.getId(), 0L));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("totalDonors", totalDonors);
        result.put("newDonors", newDonors);
        result.put("returningDonors", returningDonors);
        result.put("donationCountsByDonor", freqMap);
        return result;
    }

    @Operation(summary = "Get request fulfillment analytics: time-to-match for fulfilled requests")
    @GetMapping("/metrics/requests")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> getRequestMetrics() {
        List<BloodRequest> matchedRequests = bloodRequestRepository.findAll().stream()
                .filter(r -> r.getStatus() == RequestStatus.MATCHED || r.getStatus() == RequestStatus.FULFILLED)
                .collect(Collectors.toList());

        List<Long> timesToMatch = matchedRequests.stream()
                .filter(r -> r.getMatchedAt() != null && r.getCreatedAt() != null)
                .map(r -> Duration.between(r.getCreatedAt(), r.getMatchedAt()).toMinutes())
                .collect(Collectors.toList());

        double averageMinutes = timesToMatch.isEmpty() ? 0.0 :
                timesToMatch.stream().mapToLong(Long::longValue).average().orElse(0.0);

        Map<String, Object> result = new HashMap<>();
        result.put("totalMatchedRequests", matchedRequests.size());
        result.put("averageTimeToMatchMinutes", averageMinutes);
        result.put("timesToMatchMinutes", timesToMatch);
        return result;
    }

    @Operation(summary = "Get basic system usage statistics")
    @GetMapping("/metrics/system")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> getSystemMetrics() {
        long totalUsers = userRepository.count();
        long totalRequests = bloodRequestRepository.count();
        long totalDonations = donationRecordRepository.count();
        long totalMatches = matchRecordRepository.count();

        long availableDonors = userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getAvailable()))
                .count();

        Map<String, Object> result = new HashMap<>();
        result.put("totalUsers", totalUsers);
        result.put("totalRequests", totalRequests);
        result.put("totalDonations", totalDonations);
        result.put("totalMatches", totalMatches);
        result.put("availableDonors", availableDonors);
        return result;
    }

    @Operation(summary = "Get audit logs")
    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public List<AuditLog> getAuditLogs() {
        return auditService.getAllLogs();
    }
}
