package ke.blood.blood_backend.service;

import ke.blood.blood_backend.model.Inventory;
import ke.blood.blood_backend.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    public List<Inventory> getAllInventory() {
        return inventoryRepository.findAll();
    }

    public Optional<Inventory> getByBloodType(String bloodType) {
        return inventoryRepository.findByBloodType(bloodType);
    }

    public Inventory setInventory(String bloodType, Integer quantity) {
        return inventoryRepository.findByBloodType(bloodType)
                .map(inv -> {
                    inv.setQuantity(quantity);
                    return inventoryRepository.save(inv);
                })
                .orElseGet(() -> inventoryRepository.save(
                        Inventory.builder()
                                .bloodType(bloodType)
                                .quantity(quantity)
                                .build()
                ));
    }
}
