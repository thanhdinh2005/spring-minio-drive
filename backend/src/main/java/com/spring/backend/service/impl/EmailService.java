package com.spring.backend.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Gửi email xác thực. Nếu chưa cấu hình SMTP (MAIL_HOST trống)
 * thì chỉ log magic link ra console để dev test được ngay.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailService {

  private final ObjectProvider<JavaMailSender> mailSenderProvider;

  @Value("${spring.mail.host:}")
  private String mailHost;

  @Value("${spring.mail.username:}")
  private String mailUsername;

  @Value("${app.mail.from:${spring.mail.username:}}")
  private String mailFrom;

  @Value("${app.verify-email-base-url:http://localhost:5173/verify-email}")
  private String verifyEmailBaseUrl;

  public void sendVerificationEmail(String email, String rawToken) {
    String link = verifyEmailBaseUrl + "?token=" + rawToken;

    JavaMailSender sender = mailSenderProvider.getIfAvailable();
    if (sender == null || mailHost == null || mailHost.isBlank()) {
      log.info("[DEV] Email verification link for {}: {}", email, link);
      return;
    }

    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(mailFrom.isBlank() ? mailUsername : mailFrom);
      message.setTo(email);
      message.setSubject("Xác thực email - Spring Mini Drive");
      message.setText(
          "Chào bạn,\n\n"
              + "Nhấn vào link sau để xác thực email và kích hoạt tài khoản:\n\n"
              + link + "\n\n"
              + "Link có hiệu lực trong 24 giờ.\n\n"
              + "Nếu bạn không đăng ký tài khoản này, hãy bỏ qua email."
      );
      sender.send(message);
      log.info("Verification email sent to {}", email);
    } catch (Exception e) {
      // Lỗi gửi mail không được làm hỏng luồng register — user có thể resend
      log.warn("Failed to send verification email to {}: {}", email, e.getMessage());
    }
  }
}
