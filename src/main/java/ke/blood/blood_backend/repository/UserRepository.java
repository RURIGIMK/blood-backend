package ke.blood.blood_backend.repository;

import ke.blood.blood_backend.model.Role;
import ke.blood.blood_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    @Query("""
      SELECT u
      FROM User u
      JOIN u.roles r
      WHERE u.available = true
        AND r = :donorRole
        AND u.bloodType IN :types
        AND u.id <> :requesterId
      ORDER BY u.createdAt
      """)
    List<User> findAvailableDonors(
            @Param("donorRole") Role donorRole,
            @Param("types") List<String> types,
            @Param("requesterId") Long requesterId
    );
}
