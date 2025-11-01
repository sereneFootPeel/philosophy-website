package com.philosophy.security;

import com.philosophy.model.User;
import com.philosophy.service.UserService;
import com.philosophy.util.UserInfoCollector;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录成功监听器，用于记录用户登录信息
 */
@Component
public class LoginSuccessListener {

    private static final Logger logger = LoggerFactory.getLogger(LoginSuccessListener.class);

    private final UserInfoCollector userInfoCollector;
    private final UserService userService;

    public LoginSuccessListener(UserInfoCollector userInfoCollector, UserService userService) {
        this.userInfoCollector = userInfoCollector;
        this.userService = userService;
    }

    @EventListener
    public void handleAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        if (authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String username = userDetails.getUsername();
            
            try {
                User user = userService.findByUsername(username);
                
                // 获取当前HTTP请求
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    userInfoCollector.recordLoginInfo(user, request);
                }
            } catch (Exception e) {
                // 记录失败但不影响登录流程
                logger.error("记录用户登录信息失败: {}", e.getMessage(), e);
            }
        }
    }
}