package ke.blood.blood_backend.repository;

import ke.blood.blood_backend.model.BloodRequest;
import ke.blood.blood_backend.model.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BloodRequestRepository extends JpaRepository<BloodRequest, Long> {
    List<BloodRequest> findByStatus(RequestStatus status);
}
