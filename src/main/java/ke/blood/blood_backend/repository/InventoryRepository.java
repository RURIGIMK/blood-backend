package ke.blood.blood_backend.repository;

import ke.blood.blood_backend.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByBloodType(String bloodType);
}
