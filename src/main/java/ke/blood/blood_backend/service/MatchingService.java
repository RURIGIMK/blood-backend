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
     * ABO + Rh compatibility chart.
     */
    private static final Map<String, List<String>> COMPATIBILITY = Map.of(
            "O-",  List.of("O-"),
            "O+",  List.of("O-", "O+"),
            "A-",  List.of("O-", "A-"),
            "A+",  List.of("O-", "O+", "A-", "A+"),
            "B-",  List.of("O-", "B-"),
            "B+",  List.of("O-", "O+", "B-", "B+"),
            "AB-", List.of("O-", "A-", "B-", "AB-"),
            "AB+", List.of("O-", "O+", "A-", "A+", "B-", "B+", "AB-", "AB+")
    );

    @Transactional
    public void matchRequest(BloodRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            return;
        }

        List<String> compatibleTypes = COMPATIBILITY
                .getOrDefault(request.getBloodType(), List.of());

        // Fetch only truly available donors (available=true), with ROLE_DONOR, matching bloodType,
        // not the requester, ordered by createdAt
        List<User> donors = userRepository.findAvailableDonors(
                Role.ROLE_DONOR,
                compatibleTypes,
                request.getRequester().getId()
        );

        if (donors.isEmpty()) {
            auditService.logEvent(
                    "MATCH_ATTEMPT_NO_DONORS",
                    "No available donors for request ID " + request.getId(),
                    request.getRequester()
            );
            return;
        }

        User chosenDonor = donors.get(0);

        // Update request to MATCHED
        request.setMatchedDonor(chosenDonor);
        request.setStatus(RequestStatus.MATCHED);
        request.setMatchedAt(Instant.now());
        bloodRequestRepository.save(request);

        // Create match record
        MatchRecord record = MatchRecord.builder()
                .bloodRequest(request)
                .donor(chosenDonor)
                .status(MatchStatus.NOTIFIED)
                .notificationSent(false)
                .build();
        record = matchRecordRepository.save(record);

        // Send email notification and mark donor unavailable
        try {
            emailService.sendMatchNotification(chosenDonor, request);
            record.setNotificationSent(true);
            record.setNotificationSentAt(Instant.now());
            matchRecordRepository.save(record);

            chosenDonor.setAvailable(false);
            userRepository.save(chosenDonor);

        } catch (Exception ex) {
            auditService.logEvent(
                    "MATCH_EMAIL_FAILED",
                    "Failed to notify donor " + chosenDonor.getUsername(),
                    request.getRequester()
            );
        }

        // Final audit
        auditService.logEvent(
                "MATCH_SUCCESS",
                "Request ID " + request.getId() + " matched to donor " + chosenDonor.getUsername(),
                request.getRequester()
        );
    }
}
