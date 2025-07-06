package ke.blood.blood_backend.service;

import ke.blood.blood_backend.model.*;
import ke.blood.blood_backend.repository.*;
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
        // 1) Only try to match PENDING requests
        if (request.getStatus() != RequestStatus.PENDING) return;

        // 2) Determine compatible donor blood types
        List<String> types = COMPATIBILITY
                .getOrDefault(request.getBloodType(), List.of());

        // 3) One single DB query: only available=true, ROLE_DONOR, correct types, not the requester
        List<User> donors = userRepository.findAvailableDonors(
                Role.ROLE_DONOR,
                types,
                request.getRequester().getId()
        );

        // 4) If none, audit & exit
        if (donors.isEmpty()) {
            auditService.logEvent(
                    "MATCH_ATTEMPT_NO_DONORS",
                    "No available donors for request ID " + request.getId(),
                    request.getRequester()
            );
            return;
        }

        // 5) Pick the earliest-registered donor
        User donor = donors.get(0);

        // 6) Update the request record
        request.setMatchedDonor(donor);
        request.setStatus(RequestStatus.MATCHED);
        request.setMatchedAt(Instant.now());
        bloodRequestRepository.save(request);

        // 7) Create and save the MatchRecord
        MatchRecord record = MatchRecord.builder()
                .bloodRequest(request)
                .donor(donor)
                .status(MatchStatus.NOTIFIED)
                .notificationSent(false)
                .build();
        record = matchRecordRepository.save(record);

        // 8) Send notification, mark donor unavailable
        try {
            emailService.sendMatchNotification(donor, request);
            record.setNotificationSent(true);
            record.setNotificationSentAt(Instant.now());
            matchRecordRepository.save(record);

            donor.setAvailable(false);
            userRepository.save(donor);

        } catch (Exception e) {
            auditService.logEvent(
                    "MATCH_EMAIL_FAILED",
                    "Failed to notify donor " + donor.getUsername(),
                    request.getRequester()
            );
        }

        // 9) Final audit
        auditService.logEvent(
                "MATCH_SUCCESS",
                "Request ID " + request.getId() + " matched to donor " + donor.getUsername(),
                request.getRequester()
        );
    }
}
