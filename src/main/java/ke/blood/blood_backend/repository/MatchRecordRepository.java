package ke.blood.blood_backend.repository;

import ke.blood.blood_backend.model.MatchRecord;
import ke.blood.blood_backend.model.MatchStatus;
import ke.blood.blood_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {
    List<MatchRecord> findByDonor(User donor);
    List<MatchRecord> findByStatus(MatchStatus status);
}
