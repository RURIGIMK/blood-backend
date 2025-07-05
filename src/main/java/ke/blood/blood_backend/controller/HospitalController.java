package ke.blood.blood_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ke.blood.blood_backend.model.DonationRecord;
import ke.blood.blood_backend.model.Inventory;
import ke.blood.blood_backend.repository.DonationRecordRepository;
import ke.blood.blood_backend.repository.InventoryRepository;
import ke.blood.blood_backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/hospital")
@RequiredArgsConstructor
@SecurityRequirement(name="BearerAuth")
public class HospitalController {
    private final DonationRecordRepository donationRepo;
    private final InventoryRepository inventoryRepo;
    private final AuditService auditService;

    @Operation(summary="Verify donation and update inventory")
    @PostMapping("/verify/{donationId}")
    @PreAuthorize("hasAuthority('ROLE_HOSPITAL')")
    public ResponseEntity<?> verify(@PathVariable Long donationId) {
        DonationRecord d = donationRepo.findById(donationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Inventory inv = inventoryRepo.findByBloodType(d.getBloodType())
                .orElseGet(() -> Inventory.builder().bloodType(d.getBloodType()).quantity(0).build());
        inv.setQuantity(inv.getQuantity() + d.getQuantity());
        inventoryRepo.save(inv);
        auditService.logEvent("DONATION_VERIFIED","Donation "+donationId+" verified", null);
        return ResponseEntity.ok(Map.of("message","Verified & inventory updated"));
    }

    @Operation(summary="Manual inventory update")
    @PutMapping("/inventory/{bloodType}")
    @PreAuthorize("hasAuthority('ROLE_HOSPITAL')")
    public ResponseEntity<Inventory> updateInv(
            @PathVariable String bloodType,
            @RequestParam int quantity) {
        Inventory inv = inventoryRepo.findByBloodType(bloodType)
                .map(i -> { i.setQuantity(quantity); return inventoryRepo.save(i); })
                .orElseGet(() -> inventoryRepo.save(
                        Inventory.builder().bloodType(bloodType).quantity(quantity).build()
                ));
        auditService.logEvent("HOSPITAL_INVENTORY_UPDATE",
                "Hospital set "+bloodType+"="+quantity, null);
        return ResponseEntity.ok(inv);
    }
}
