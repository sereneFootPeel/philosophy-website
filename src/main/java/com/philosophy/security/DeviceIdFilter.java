package com.philosophy.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Component
public class DeviceIdFilter extends OncePerRequestFilter {

    private static final String COOKIE_NAME = "did";
    private static final String COOKIE_SIG_NAME = "did_sig";
    private static final int COOKIE_MAX_AGE = (int) Duration.ofDays(365).getSeconds();

    @Value("${security.device.secret:change-this-secret}")
    private String deviceSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String deviceId = null;
        String deviceSig = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (COOKIE_NAME.equals(c.getName())) deviceId = c.getValue();
                if (COOKIE_SIG_NAME.equals(c.getName())) deviceSig = c.getValue();
            }
        }

        boolean valid = deviceId != null && deviceSig != null && verifySignature(deviceId, deviceSig);
        if (!valid) {
            deviceId = generateDeviceId();
            deviceSig = sign(deviceId);
            addHttpOnlyCookie(response, COOKIE_NAME, deviceId);
            addHttpOnlyCookie(response, COOKIE_SIG_NAME, deviceSig);
        }

        request.setAttribute("__device_id__", deviceId);
        filterChain.doFilter(request, response);
    }

    private void addHttpOnlyCookie(HttpServletResponse response, String name, String value) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_MAX_AGE);
        cookie.setSecure(false);
        response.addCookie(cookie);
    }

    private String generateDeviceId() {
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String sign(String deviceId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(deviceSecret.getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(deviceId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean verifySignature(String deviceId, String signature) {
        return sign(deviceId).equals(signature);
    }
}


