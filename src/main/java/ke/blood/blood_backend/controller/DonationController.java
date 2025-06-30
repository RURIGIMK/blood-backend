package ke.blood.blood_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ke.blood.blood_backend.model.*;
import ke.blood.blood_backend.repository.BloodRequestRepository;
import ke.blood.blood_backend.repository.DonationRecordRepository;
import ke.blood.blood_backend.repository.InventoryRepository;
import ke.blood.blood_backend.repository.MatchRecordRepository;
import ke.blood.blood_backend.repository.UserRepository;
import ke.blood.blood_backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/donations")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class DonationController {

    private final UserRepository userRepository;
    private final BloodRequestRepository requestRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final DonationRecordRepository donationRecordRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditService auditService;

    @Operation(summary = "Donor confirms donation for a matched request")
    @PostMapping("/confirm/{requestId}")
    @PreAuthorize("hasAuthority('ROLE_DONOR')")
    public ResponseEntity<?> confirmDonation(@PathVariable Long requestId, Principal principal) {
        String username = principal.getName();
        Optional<User> donorOpt = userRepository.findByUsername(username);
        if (donorOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }
        User donor = donorOpt.get();

        Optional<BloodRequest> reqOpt = requestRepository.findById(requestId);
        if (reqOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Request not found"));
        }
        BloodRequest request = reqOpt.get();

        if (request.getMatchedDonor() == null || !request.getMatchedDonor().getId().equals(donor.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "You are not the matched donor for this request"));
        }
        if (request.getStatus() != RequestStatus.MATCHED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Request is not in MATCHED status"));
        }

        DonationRecord record = DonationRecord.builder()
                .donor(donor)
                .bloodType(request.getBloodType())
                .quantity(request.getQuantity())
                .build();
        DonationRecord savedRecord = donationRecordRepository.save(record);

        String bt = request.getBloodType();
        inventoryRepository.findByBloodType(bt).ifPresentOrElse(inv -> {
            inv.setQuantity(inv.getQuantity() + request.getQuantity());
            inventoryRepository.save(inv);
        }, () -> {
            Inventory newInv = Inventory.builder()
                    .bloodType(bt)
                    .quantity(request.getQuantity())
                    .build();
            inventoryRepository.save(newInv);
        });

        request.setStatus(RequestStatus.FULFILLED);
        requestRepository.save(request);

        matchRecordRepository.findByDonor(donor).stream()
                .filter(mr -> mr.getBloodRequest().getId().equals(requestId))
                .findFirst()
                .ifPresent(mr -> {
                    mr.setStatus(MatchStatus.CONFIRMED);
                    matchRecordRepository.save(mr);
                });

        donor.setAvailable(false);
        userRepository.save(donor);

        auditService.logEvent("DONATION_CONFIRMED",
                "Donor " + donor.getUsername() + " confirmed donation for request ID " + requestId,
                donor);

        return ResponseEntity.ok(savedRecord);
    }
}
