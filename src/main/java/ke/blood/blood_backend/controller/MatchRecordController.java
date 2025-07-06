package ke.blood.blood_backend.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ke.blood.blood_backend.model.MatchRecord;
import ke.blood.blood_backend.repository.MatchRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/matches")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class MatchRecordController {

    private final MatchRecordRepository matchRecordRepository;

    /**
     * Admin: view all match records.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<List<MatchRecord>> getAll() {
        return ResponseEntity.ok(matchRecordRepository.findAll());
    }

    /**
     * Donor: view only my match records.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ROLE_DONOR')")
    public ResponseEntity<List<MatchRecord>> getMine(Principal principal) {
        // We need to look up User by username; we can do that here or via a custom repo method.
        // For simplicity, assume MatchRecord has a donor.username in JSON.
        List<MatchRecord> mine = matchRecordRepository.findAll().stream()
                .filter(m -> m.getDonor().getUsername().equals(principal.getName()))
                .toList();
        return ResponseEntity.ok(mine);
    }
}
