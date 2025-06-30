package ke.blood.blood_backend.service;

import ke.blood.blood_backend.model.*;
import ke.blood.blood_backend.repository.BloodRequestRepository;
import ke.blood.blood_backend.repository.MatchRecordRepository;
import ke.blood.blood_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatchingService {

    private final UserRepository userRepository;
    private final BloodRequestRepository bloodRequestRepository;
    private final MatchRecordRepository matchRecordRepository;
    private final EmailService emailService;
    private final AuditService auditService;

    /**
     * Simplified first‑come, first‑serve match:
     *  - Pick the AVAILABLE donor with matching bloodType who registered earliest.
     */
    @Transactional
    public void matchRequest(BloodRequest request) {
        // only match pending
        if (request.getStatus() != RequestStatus.PENDING) {
            return;
        }

        // fetch, filter, then sort by createdAt asc
        List<User> donors = userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getAvailable()))
                .filter(u -> u.getBloodType() != null
                        && u.getBloodType().equalsIgnoreCase(request.getBloodType()))
                .filter(u -> !u.getId().equals(request.getRequester().getId()))
                .sorted(Comparator.comparing(User::getCreatedAt))
                .collect(Collectors.toList());

        if (donors.isEmpty()) {
            auditService.logEvent(
                    "MATCH_ATTEMPT_NO_DONORS",
                    "No available donors for request ID " + request.getId(),
                    request.getRequester()
            );
            return;
        }

        // pick the first (earliest-registered) donor
        User chosenDonor = donors.get(0);

        // update request
        request.setMatchedDonor(chosenDonor);
        request.setStatus(RequestStatus.MATCHED);
        request.setMatchedAt(Instant.now());
        bloodRequestRepository.save(request);

        // record match
        MatchRecord record = MatchRecord.builder()
                .bloodRequest(request)
                .donor(chosenDonor)
                .status(MatchStatus.NOTIFIED)
                .notificationSent(false)
                .build();
        record = matchRecordRepository.save(record);

        // send email
        emailService.sendMatchNotification(chosenDonor, request);

        // update notification flag
        record.setNotificationSent(true);
        record.setNotificationSentAt(Instant.now());
        matchRecordRepository.save(record);

        // audit
        auditService.logEvent(
                "MATCH_SUCCESS",
                "Request ID " + request.getId() + " matched to donor " + chosenDonor.getUsername(),
                request.getRequester()
        );
    }
}
