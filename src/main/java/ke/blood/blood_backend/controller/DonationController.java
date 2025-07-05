package ke.blood.blood_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ke.blood.blood_backend.model.*;
import ke.blood.blood_backend.repository.*;
import ke.blood.blood_backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;                // <— added
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/donations")
@RequiredArgsConstructor
@SecurityRequirement(name="BearerAuth")
public class DonationController {
    private final UserRepository userRepo;
    private final BloodRequestRepository requestRepo;
    private final MatchRecordRepository matchRepo;
    private final DonationRecordRepository donationRepo;
    private final InventoryRepository inventoryRepo;
    private final AuditService auditService;

    @Operation(summary="Donor views donation history")
    @GetMapping("/history")
    @PreAuthorize("hasAuthority('ROLE_DONOR')")
    public ResponseEntity<?> history(Principal p) {
        User donor = userRepo.findByUsername(p.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return ResponseEntity.ok(donationRepo.findByDonor(donor));
    }

    @Operation(summary="Donor claims a matched request")
    @PostMapping("/claim/{requestId}")
    @PreAuthorize("hasAuthority('ROLE_DONOR')")
    public ResponseEntity<MatchRecord> claim(@PathVariable Long requestId, Principal p) {
        User donor = userRepo.findByUsername(p.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        MatchRecord mr = matchRepo.findByBloodRequest_IdAndDonor_Id(requestId, donor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No matching record"));

        if (mr.getStatus() != MatchStatus.NOTIFIED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request not in a claimable state");
        }

        mr.setStatus(MatchStatus.CLAIMED);
        matchRepo.save(mr);

        auditService.logEvent("REQUEST_CLAIMED",
                "Donor " + donor.getUsername() + " claimed request " + requestId,
                donor);

        return ResponseEntity.ok(mr);
    }

    @Operation(summary="Donor confirms actual donation")
    @PostMapping("/confirm/{requestId}")
    @PreAuthorize("hasAuthority('ROLE_DONOR')")
    public ResponseEntity<DonationRecord> confirm(@PathVariable Long requestId, Principal p) {
        User donor = userRepo.findByUsername(p.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        BloodRequest request = requestRepo.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));

        if (request.getMatchedDonor() == null || !request.getMatchedDonor().getId().equals(donor.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not the matched donor");
        }
        if (request.getStatus() != RequestStatus.MATCHED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is not in MATCHED status");
        }

        // Create donation record
        DonationRecord record = DonationRecord.builder()
                .donor(donor)
                .bloodType(request.getBloodType())
                .quantity(request.getQuantity())
                .build();
        DonationRecord saved = donationRepo.save(record);

        // Update inventory
        inventoryRepo.findByBloodType(request.getBloodType()).ifPresentOrElse(inv -> {
            inv.setQuantity(inv.getQuantity() + request.getQuantity());
            inventoryRepo.save(inv);
        }, () -> {
            Inventory inv = Inventory.builder()
                    .bloodType(request.getBloodType())
                    .quantity(request.getQuantity())
                    .build();
            inventoryRepo.save(inv);
        });

        // Mark request fulfilled
        request.setStatus(RequestStatus.FULFILLED);
        requestRepo.save(request);

        // Update match record status
        matchRepo.findByBloodRequest_IdAndDonor_Id(requestId, donor.getId())
                .ifPresent(mr -> {
                    mr.setStatus(MatchStatus.CONFIRMED);
                    matchRepo.save(mr);
                });

        // Set donor unavailable
        donor.setAvailable(false);
        userRepo.save(donor);

        auditService.logEvent("DONATION_CONFIRMED",
                "Donor " + donor.getUsername() + " confirmed donation for request " + requestId,
                donor);

        return ResponseEntity.ok(saved);
    }

    @Operation(summary="Hospital verifies donation (legacy endpoint)")
    @PostMapping("/verify/{donationId}")
    @PreAuthorize("hasAuthority('ROLE_HOSPITAL')")
    public ResponseEntity<Map<String, String>> verifyLegacy(@PathVariable Long donationId) {
        DonationRecord donation = donationRepo.findById(donationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donation not found"));

        inventoryRepo.findByBloodType(donation.getBloodType()).ifPresentOrElse(inv -> {
            inv.setQuantity(inv.getQuantity() + donation.getQuantity());
            inventoryRepo.save(inv);
        }, () -> {
            Inventory inv = Inventory.builder()
                    .bloodType(donation.getBloodType())
                    .quantity(donation.getQuantity())
                    .build();
            inventoryRepo.save(inv);
        });

        auditService.logEvent("DONATION_VERIFIED",
                "Legacy verify for donation " + donationId,
                null);

        return ResponseEntity.ok(Map.of("message", "Donation verified"));
    }
}
