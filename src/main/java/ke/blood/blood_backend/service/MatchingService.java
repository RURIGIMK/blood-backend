package ke.blood.blood_backend.service;

import ke.blood.blood_backend.model.BloodRequest;
import ke.blood.blood_backend.model.MatchRecord;
import ke.blood.blood_backend.model.MatchStatus;
import ke.blood.blood_backend.model.RequestStatus;
import ke.blood.blood_backend.model.Role;
import ke.blood.blood_backend.model.User;
import ke.blood.blood_backend.repository.BloodRequestRepository;
import ke.blood.blood_backend.repository.MatchRecordRepository;
import ke.blood.blood_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MatchingService {

    private final UserRepository userRepository;
    private final BloodRequestRepository bloodRequestRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    /**
     * ABO + Rh compatibility map.
     */
    private static final Map<String, List<String>> COMPATIBILITY = Map.of(
            "O-", List.of("O-"),
            "O+", List.of("O-", "O+"),
            "A-", List.of("O-", "A-"),
            "A+", List.of("O-", "O+", "A-", "A+"),
            "B-", List.of("O-", "B-"),
            "B+", List.of("O-", "O+", "B-", "B+"),
            "AB-", List.of("O-", "A-", "B-", "AB-"),
            "AB+", List.of("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+")
    );

    @Transactional
    public void matchRequest(BloodRequest request) {
        // Only match pending requests
        if (request.getStatus() != RequestStatus.PENDING) {
            return;
        }

        // Determine compatible donor blood types
        List<String> compatibleTypes = COMPATIBILITY
                .getOrDefault(request.getBloodType(), List.of());

        // Fetch and filter donors:
        // 1) must be available (true)
        // 2) must have ROLE_DONOR
        // 3) must match blood type
        // 4) must not be the requester
        // 5) must have a non-null createdAt for sorting
        List<User> donors = userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getAvailable()))                          // only truly available donors
                .filter(u -> u.getRoles().contains(Role.ROLE_DONOR))                         // only users with donor role
                .filter(u -> u.getBloodType() != null && compatibleTypes.contains(u.getBloodType()))
                .filter(u -> !u.getId().equals(request.getRequester().getId()))              // no self-matching
                .filter(u -> u.getCreatedAt() != null)                                       // ensure sortable
                .sorted(Comparator.comparing(User::getCreatedAt))                            // earliest-registered first
                .toList();

        if (donors.isEmpty()) {
            auditService.logEvent(
                    "MATCH_ATTEMPT_NO_DONORS",
                    "No available donors for request ID " + request.getId(),
                    request.getRequester()
            );
            return;
        }

        // Pick the earliest-registered donor
        User chosenDonor = donors.get(0);

        // Update the request
        request.setMatchedDonor(chosenDonor);
        request.setStatus(RequestStatus.MATCHED);
        request.setMatchedAt(Instant.now());
        bloodRequestRepository.save(request);

        // Create the match record
        MatchRecord record = MatchRecord.builder()
                .bloodRequest(request)
                .donor(chosenDonor)
                .status(MatchStatus.NOTIFIED)
                .notificationSent(false)
                .build();
        record = matchRecordRepository.save(record);

        // Attempt email notification (DB is already updated)
        try {
            emailService.sendMatchNotification(chosenDonor, request);
            record.setNotificationSent(true);
            record.setNotificationSentAt(Instant.now());
            matchRecordRepository.save(record);

            // Now mark donor unavailable
            chosenDonor.setAvailable(false);
            userRepository.save(chosenDonor);

        } catch (Exception ex) {
            // Log but do not roll back the DB changes
            auditService.logEvent(
                    "MATCH_EMAIL_FAILED",
                    "Failed to notify donor " + chosenDonor.getUsername(),
                    request.getRequester()
            );
        }

        // Final audit of success
        auditService.logEvent(
                "MATCH_SUCCESS",
                "Request ID " + request.getId() + " matched to donor " + chosenDonor.getUsername(),
                request.getRequester()
        );
    }
}
