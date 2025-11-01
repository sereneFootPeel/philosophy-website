package com.philosophy.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.io.File;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;
    
    @Autowired
    private TemplateEngine templateEngine;
    
    @Value("${spring.mail.username}")
    private String fromEmail;
    
    public void sendVerificationCode(String toEmail, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject("【哲学】注册验证码");
            helper.setFrom(fromEmail);

            Context context = new Context();
            context.setVariable("code", code);

            String htmlContent = templateEngine.process("verification-code", context);
            helper.setText(htmlContent, true);

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("发送验证码邮件失败", e);
        }
    }
    
    public void sendDailyReport(String toEmail, Map<String, Object> reportData) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(toEmail);
            helper.setSubject("哲学网站每日数据报告 - " + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            helper.setFrom(fromEmail);
            
            Context context = new Context();
            context.setVariables(reportData);
            
            String htmlContent = templateEngine.process("daily-report", context);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            
        } catch (MessagingException e) {
            throw new RuntimeException("发送邮件失败", e);
        }
    }
    
    public void sendSimpleReport(String toEmail, String subject, String content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(content, true);
            helper.setFrom(fromEmail);
            
            mailSender.send(message);
            
        } catch (MessagingException e) {
            throw new RuntimeException("发送邮件失败", e);
        }
    }

    public void sendEmailWithAttachment(String to, String subject, String text, File file) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text);
            helper.addAttachment(file.getName(), file);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("发送带附件的邮件失败", e);
        }
    }

    public void sendReportWithAttachment(String toEmail, String subject, String htmlContent,
                                         byte[] attachmentBytes, String attachmentFilename) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom(fromEmail);

            if (attachmentBytes != null && attachmentBytes.length > 0) {
                helper.addAttachment(attachmentFilename != null ? attachmentFilename : "report.csv",
                        new ByteArrayResource(attachmentBytes));
            }

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("发送带附件的邮件失败", e);
        }
    }
}