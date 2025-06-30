package ke.blood.blood_backend.repository;

import ke.blood.blood_backend.model.DonationRecord;
import ke.blood.blood_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DonationRecordRepository extends JpaRepository<DonationRecord, Long> {
    List<DonationRecord> findByDonor(User donor);
}
