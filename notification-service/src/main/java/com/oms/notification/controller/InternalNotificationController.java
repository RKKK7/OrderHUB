package com.oms.notification.controller;

import com.oms.notification.dto.NotificationRequest;
import com.oms.notification.model.Notification;
import com.oms.notification.repository.NotificationRepository;
import com.oms.notification.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal API — called by order-service via Feign.
 * NOT exposed through the API Gateway (no /internal/** route).
 *
 * This is the single entry point for ALL notification creation in the system.
 * Every order status change (placed, confirmed, shipped, delivered, cancelled)
 * flows through here.
 */
@RestController
@RequestMapping("/internal/notifications")
@Slf4j
public class InternalNotificationController {

    private final NotificationRepository notifRepo;
    private final EmailService emailService;

    public InternalNotificationController(NotificationRepository notifRepo, EmailService emailService) {
        this.notifRepo = notifRepo;
        this.emailService = emailService;
    }

    @PostMapping("/send")
    public Map<String, Object> send(@RequestBody NotificationRequest req) {
        try {
            // 1. Save in-app notification
            Notification n = new Notification();
            n.setUserId(req.getUserId());
            n.setUserEmail(req.getUserEmail() != null ? req.getUserEmail() : "");
            n.setTitle(req.getTitle());
            n.setMessage(req.getMessage());
            n.setType(req.getType() != null ? req.getType() : "SYSTEM");
            n.setLink(req.getLink() != null ? req.getLink() : "");
            n = notifRepo.save(n);

            // 2. Send email (if email address provided and not blank)
            if (req.getUserEmail() != null && !req.getUserEmail().isBlank()) {
                String html = buildEmailHtml(req);
                emailService.sendHtml(req.getUserEmail(), "OMS: " + req.getTitle(), html);
            }

            return Map.of("id", n.getId(), "message", "Notification sent");

        } catch (Exception e) {
            log.error("Notification send error: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    private String buildEmailHtml(NotificationRequest req) {
        return """
            <div style="font-family:sans-serif;max-width:500px;margin:0 auto;padding:20px">
                <h2 style="color:#2563eb">📦 Order Management System</h2>
                <h3>%s</h3>
                <p style="color:#374151;line-height:1.6">%s</p>
                %s
                <hr style="margin-top:24px;border:none;border-top:1px solid #e5e7eb"/>
                <p style="color:#9ca3af;font-size:12px">Order Management System</p>
            </div>
            """.formatted(
                req.getTitle(),
                req.getMessage(),
                (req.getLink() != null && !req.getLink().isBlank())
                    ? "<a href=\"http://localhost:5173" + req.getLink() + "\" style=\"display:inline-block;margin-top:12px;background:#2563eb;color:#fff;padding:10px 20px;border-radius:6px;text-decoration:none;font-weight:600\">View Details</a>"
                    : ""
        );
    }
}
