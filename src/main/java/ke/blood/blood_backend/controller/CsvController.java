package ke.blood.blood_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import ke.blood.blood_backend.model.*;
import ke.blood.blood_backend.repository.*;
import ke.blood.blood_backend.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class CsvController {

    private final UserRepository userRepository;
    private final BloodRequestRepository requestRepository;
    private final DonationRecordRepository donationRecordRepository;
    private final InventoryRepository inventoryRepository;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "Import users from CSV")
    @PostMapping(value = "/import/users", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public Map<String, Object> importUsers(@RequestParam("file") MultipartFile file) throws IOException {
        Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
        Iterable<CSVRecord> records = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()
                .parse(reader);
        int count = 0;
        for (CSVRecord record : records) {
            String username = record.get("username");
            if (username == null || username.isBlank()) continue;
            if (userRepository.existsByUsername(username)) {
                continue; // skip existing
            }
            String password = record.get("password");
            String fullName = record.get("fullName");
            String email = record.get("email");
            String bloodType = record.get("bloodType");
            String roleStr = record.get("role");
            String availableStr = record.get("available");
            String latStr = record.get("latitude");
            String lonStr = record.get("longitude");
            String locDesc = record.get("locationDescription");

            Role role;
            try {
                String ir = roleStr.trim().toUpperCase();
                if (ir.startsWith("ROLE_")) role = Role.valueOf(ir);
                else role = Role.valueOf("ROLE_" + ir);
            } catch (Exception e) {
                continue;
            }

            Boolean available = null;
            if (availableStr != null && !availableStr.isBlank()) {
                available = Boolean.parseBoolean(availableStr);
            }

            Double lat = null, lon = null;
            try {
                if (latStr != null && !latStr.isBlank()) lat = Double.parseDouble(latStr);
                if (lonStr != null && !lonStr.isBlank()) lon = Double.parseDouble(lonStr);
            } catch (NumberFormatException ignored) {}

            User user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password != null && !password.isBlank() ? password : "defaultPass123"))
                    .fullName(fullName)
                    .email(email)
                    .bloodType(bloodType)
                    .available(Boolean.TRUE.equals(available))
                    .latitude(lat)
                    .longitude(lon)
                    .locationDescription(locDesc)
                    .roles(Set.of(role))
                    .build();
            userRepository.save(user);
            auditService.logEvent("USER_IMPORT", "Imported user " + username, user);
            count++;
        }
        return Map.of("imported", count);
    }

    @Operation(summary = "Export users to CSV")
    @GetMapping("/export/users")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public void exportUsers(HttpServletResponse response) throws IOException {
        String filename = "users.csv";
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
                     "id", "username", "fullName", "email", "bloodType", "role", "available", "latitude", "longitude", "locationDescription", "createdAt", "updatedAt"
             ))) {
            List<User> users = userRepository.findAll();
            for (User u : users) {
                String roles = u.getRoles().stream().map(Enum::name).collect(Collectors.joining(";"));
                csvPrinter.printRecord(
                        u.getId(),
                        u.getUsername(),
                        u.getFullName(),
                        u.getEmail(),
                        u.getBloodType(),
                        roles,
                        u.getAvailable(),
                        u.getLatitude(),
                        u.getLongitude(),
                        u.getLocationDescription(),
                        u.getCreatedAt(),
                        u.getUpdatedAt()
                );
            }
            csvPrinter.flush();
        }
        auditService.logEvent("USER_EXPORT", "Exported users CSV", null);
    }

    @Operation(summary = "Export blood requests to CSV")
    @GetMapping("/export/requests")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public void exportRequests(HttpServletResponse response) throws IOException {
        String filename = "requests.csv";
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
                     "id", "requesterUsername", "bloodType", "quantity", "urgencyLevel", "hospitalName", "hospitalLatitude", "hospitalLongitude", "locationDescription", "status", "matchedDonorUsername", "createdAt", "matchedAt", "updatedAt"
             ))) {
            List<BloodRequest> requests = requestRepository.findAll();
            for (BloodRequest r : requests) {
                String requester = r.getRequester() != null ? r.getRequester().getUsername() : "";
                String matchedDonor = r.getMatchedDonor() != null ? r.getMatchedDonor().getUsername() : "";
                csvPrinter.printRecord(
                        r.getId(),
                        requester,
                        r.getBloodType(),
                        r.getQuantity(),
                        r.getUrgencyLevel(),
                        r.getHospitalName(),
                        r.getHospitalLatitude(),
                        r.getHospitalLongitude(),
                        r.getLocationDescription(),
                        r.getStatus(),
                        matchedDonor,
                        r.getCreatedAt(),
                        r.getMatchedAt(),
                        r.getUpdatedAt()
                );
            }
            csvPrinter.flush();
        }
        auditService.logEvent("REQUEST_EXPORT", "Exported requests CSV", null);
    }

    @Operation(summary = "Export donation records to CSV")
    @GetMapping("/export/donations")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public void exportDonations(HttpServletResponse response) throws IOException {
        String filename = "donations.csv";
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
                     "id", "donorUsername", "bloodType", "quantity", "donationDate"
             ))) {
            List<DonationRecord> recs = donationRecordRepository.findAll();
            for (DonationRecord d : recs) {
                String donorName = d.getDonor() != null ? d.getDonor().getUsername() : "";
                csvPrinter.printRecord(
                        d.getId(),
                        donorName,
                        d.getBloodType(),
                        d.getQuantity(),
                        d.getDonationDate()
                );
            }
            csvPrinter.flush();
        }
        auditService.logEvent("DONATION_EXPORT", "Exported donations CSV", null);
    }

    @Operation(summary = "Export inventory to CSV")
    @GetMapping("/export/inventory")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public void exportInventory(HttpServletResponse response) throws IOException {
        String filename = "inventory.csv";
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
             CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
                     "id", "bloodType", "quantity", "updatedAt"
             ))) {
            List<Inventory> invs = inventoryRepository.findAll();
            for (Inventory inv : invs) {
                csvPrinter.printRecord(
                        inv.getId(),
                        inv.getBloodType(),
                        inv.getQuantity(),
                        inv.getUpdatedAt()
                );
            }
            csvPrinter.flush();
        }
        auditService.logEvent("INVENTORY_EXPORT", "Exported inventory CSV", null);
    }
}
