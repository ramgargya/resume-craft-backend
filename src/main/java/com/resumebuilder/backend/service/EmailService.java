package com.resumebuilder.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp, String purpose) {
        String subject = "Your OTP Verification Code";
        
        if (mailSender != null) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom("ramsharmaperfectmoker@gmail.com");
                helper.setTo(toEmail);
                helper.setSubject(subject);

                String htmlContent = "<!DOCTYPE html>"
                    + "<html>"
                    + "<head>"
                    + "  <meta charset='UTF-8'>"
                    + "  <style>"
                    + "    body { font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #f8fafc; margin: 0; padding: 0; -webkit-font-smoothing: antialiased; }"
                    + "    .container { max-width: 560px; margin: 50px auto; background-color: #ffffff; border-radius: 16px; box-shadow: 0 10px 30px rgba(0,0,0,0.04); overflow: hidden; border: 1px solid #e2e8f0; }"
                    + "    .header { background: linear-gradient(135deg, #dc2626 0%, #991b1b 100%); padding: 35px 30px; text-align: center; }"
                    + "    .header h1 { color: #ffffff; margin: 0; font-size: 28px; font-weight: 800; letter-spacing: -0.03em; }"
                    + "    .content { padding: 40px 35px; text-align: center; color: #1e293b; }"
                    + "    .purpose { font-size: 13px; color: #dc2626; margin-bottom: 20px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.08em; }"
                    + "    .greeting { font-size: 18px; font-weight: 600; margin-bottom: 12px; color: #0f172a; }"
                    + "    .otp-card { background: #f8fafc; border-radius: 12px; padding: 24px; margin: 28px 0; border: 1px solid #e2e8f0; display: inline-block; }"
                    + "    .otp-code { font-size: 38px; font-weight: 800; color: #dc2626; letter-spacing: 8px; font-family: 'JetBrains Mono', 'Courier New', monospace; line-height: 1; margin-left: 8px; }"
                    + "    .info { font-size: 14px; color: #64748b; line-height: 1.6; margin-top: 24px; }"
                    + "    .footer { background-color: #f8fafc; padding: 24px; text-align: center; border-top: 1px solid #e2e8f0; font-size: 12px; color: #94a3b8; line-height: 1.5; }"
                    + "  </style>"
                    + "</head>"
                    + "<body>"
                    + "  <div class='container'>"
                    + "    <div class='header'>"
                    + "      <h1>Resume Craft</h1>"
                    + "    </div>"
                    + "    <div class='content'>"
                    + "      <div class='purpose'>" + purpose + "</div>"
                    + "      <div class='greeting'>Verification Code</div>"
                    + "      <p style='font-size: 15px; line-height: 1.6; margin: 0; color: #475569;'>Please use the security code below to complete your verification. This code is strictly valid for <strong>5 minutes</strong> and should not be shared with anyone.</p>"
                    + "      <div class='otp-card'>"
                    + "        <div class='otp-code'>" + otp + "</div>"
                    + "      </div>"
                    + "      <p class='info'>If you did not initiate this request, you can safely ignore this email. Your account security remains intact.</p>"
                    + "    </div>"
                    + "    <div class='footer'>"
                    + "      &copy; 2026 Resume Craft. Crafted for Career Excellence.<br>"
                    + "      This is an automated system email. Please do not reply directly."
                    + "    </div>"
                    + "  </div>"
                    + "</body>"
                    + "</html>";

                helper.setText(htmlContent, true);
                mailSender.send(message);
                logger.info("SMTP email sent successfully.");
            } catch (Exception e) {
                logger.error("Failed to send email via SMTP: {}", e.getMessage());
            }
        } else {
            logger.warn("JavaMailSender is not initialized. Unable to send OTP email.");
        }
    }
}
