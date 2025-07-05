package ke.blood.blood_backend.service;

import ke.blood.blood_backend.model.BloodRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {
    public void notifyUrgent(BloodRequest req) {
        // stub for future queue integration
        System.out.println("URGENT: request " + req.getId());
    }
}
