package ke.blood.blood_backend.service;

import ke.blood.blood_backend.model.AuditLog;
import ke.blood.blood_backend.model.User;
import ke.blood.blood_backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void logEvent(String eventType, String description, User user) {
        AuditLog log = AuditLog.builder()
                .eventType(eventType)
                .description(description)
                .user(user)
                .build();
        auditLogRepository.save(log);
    }

    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAll();
    }
}
