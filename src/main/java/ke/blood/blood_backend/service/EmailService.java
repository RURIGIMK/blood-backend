package ke.blood.blood_backend.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import ke.blood.blood_backend.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    /**
     * Send an email to the given recipient.
     *
     * @param to      recipient email
     * @param subject subject
     * @param body    body (HTML or plain text)
     * @throws MessagingException
     */
    public void sendEmail(String to, String subject, String body) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(body, true); // true = isHtml
        mailSender.send(message);
    }

    /**
     * Send match notification to donor.
     *
     * @param donor   the User to notify
     * @param request details about the request
     */
    public void sendMatchNotification(User donor, ke.blood.blood_backend.model.BloodRequest request) {
        if (donor.getEmail() == null || donor.getEmail().isBlank()) {
            System.out.println("Donor " + donor.getUsername() + " has no email; skipping notification.");
            return;
        }
        String subject = "Blood Donation Request Match Found";
        StringBuilder body = new StringBuilder();
        body.append("<p>Dear ").append(donor.getFullName()).append(",</p>");
        body.append("<p>A blood donation request matching your blood type (")
                .append(request.getBloodType())
                .append(") has been found.</p>");
        body.append("<p>Details:</p>");
        body.append("<ul>");
        body.append("<li>Quantity needed: ").append(request.getQuantity()).append("</li>");
        body.append("<li>Urgency level: ").append(request.getUrgencyLevel()).append("</li>");
        body.append("<li>Hospital: ").append(request.getHospitalName()).append("</li>");
        if (request.getLocationDescription() != null) {
            body.append("<li>Location: ").append(request.getLocationDescription()).append("</li>");
        }
        if (request.getHospitalLatitude() != null && request.getHospitalLongitude() != null) {
            body.append(String.format("<li>Coordinates: %.6f, %.6f</li>",
                    request.getHospitalLatitude(), request.getHospitalLongitude()));
        }
        body.append("</ul>");
        body.append("<p>If you can donate, please click the link below to confirm:</p>");
        String confirmUrl = "http://localhost:3000/confirm-donation/" + request.getId();
        body.append("<p><a href=\"").append(confirmUrl).append("\">Confirm Donation</a></p>");
        body.append("<p>Thank you for your generosity!</p>");

        try {
            sendEmail(donor.getEmail(), subject, body.toString());
            System.out.println("Sent match notification to " + donor.getEmail());
        } catch (MessagingException e) {
            e.printStackTrace();
        }
    }
}
