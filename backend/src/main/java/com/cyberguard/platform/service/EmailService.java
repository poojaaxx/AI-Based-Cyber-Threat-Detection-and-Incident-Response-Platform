package com.cyberguard.platform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendCriticalAlertEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("[CyberGuard Critical Alert] " + subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception ex) {
            // Email delivery failures must never block threat detection / incident response.
            log.warn("Failed to send critical alert email to {}: {}", to, ex.getMessage());
        }
    }
}
