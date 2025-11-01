package com.philosophy.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class VerificationCodeService {

    private static final Logger logger = LoggerFactory.getLogger(VerificationCodeService.class);
    private static class CodeRecord {
        String code;
        LocalDateTime expiresAt;
        LocalDateTime lastSentAt;
        int verifyAttempts;
    }

    private final ConcurrentHashMap<String, CodeRecord> emailToCode = new ConcurrentHashMap<>();

    /**
     * Generate a 6-digit code with 10 minutes validity and 60s resend cooldown.
     * Throws IllegalStateException with remaining seconds if cooldown not passed.
     */
    public synchronized String generateAndStoreCode(String email) {
        LocalDateTime now = LocalDateTime.now();
        CodeRecord existing = emailToCode.get(email);
        if (existing != null && existing.lastSentAt != null) {
            long secondsSinceLast = Duration.between(existing.lastSentAt, now).getSeconds();
            if (secondsSinceLast < 60) {
                long remaining = 60 - secondsSinceLast;
                throw new IllegalStateException(String.valueOf(remaining));
            }
        }

        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        CodeRecord record = new CodeRecord();
        record.code = code;
        record.expiresAt = now.plusMinutes(10);
        record.lastSentAt = now;
        record.verifyAttempts = 0;
        emailToCode.put(email, record);
        logger.info("Generated and stored code for email: {}. Current store size: {}", email, emailToCode.size());
        return code;
    }

    /**
     * Verify the code. On success or too many failures/expired, the record is removed.
     */
    public boolean verifyCode(String email, String inputCode) {
        logger.info("Attempting to verify code for email: {}. Current store size: {}. Keys: {}", email, emailToCode.size(), emailToCode.keySet());
        CodeRecord record = emailToCode.get(email);
        if (record == null) {
            logger.warn("Verification failed: No code record found for email: {}", email);
            return false;
        }
        if (record.expiresAt != null && LocalDateTime.now().isAfter(record.expiresAt)) {
            emailToCode.remove(email);
            logger.warn("Verification failed: Code expired for email: {}", email);
            return false;
        }
        record.verifyAttempts++;
        boolean ok = record.code != null && record.code.equals(inputCode);
        if (ok) {
            logger.info("Verification successful for email: {}", email);
        } else {
            logger.warn("Verification failed: Incorrect code for email: {}. Attempt {} of 5.", email, record.verifyAttempts);
        }

        if (ok || record.verifyAttempts >= 5) {
            emailToCode.remove(email);
            logger.info("Code record removed for email: {}. Reason: {}", email, ok ? "successful verification" : "max attempts reached");
        }
        return ok;
    }

    /**
     * Seconds remaining until resend is allowed. 0 means allowed now.
     */
    public long getSecondsUntilResendAllowed(String email) {
        CodeRecord record = emailToCode.get(email);
        if (record == null || record.lastSentAt == null) {
            return 0;
        }
        long elapsed = Duration.between(record.lastSentAt, LocalDateTime.now()).getSeconds();
        long remaining = 60 - elapsed;
        return Math.max(0, remaining);
    }

    public void clear(String email) {
        emailToCode.remove(email);
    }
}



