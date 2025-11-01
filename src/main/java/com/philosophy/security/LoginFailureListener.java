package com.philosophy.security;

import com.philosophy.model.User;
import com.philosophy.model.UserLoginInfo;
import com.philosophy.repository.UserLoginInfoRepository;
import com.philosophy.repository.UserRepository;
import com.philosophy.service.UserService;
import com.philosophy.util.UserInfoCollector;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class LoginFailureListener {

    private static final Logger logger = LoggerFactory.getLogger(LoginFailureListener.class);

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserLoginInfoRepository userLoginInfoRepository;
    private final UserInfoCollector userInfoCollector;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    public LoginFailureListener(UserService userService,
                              UserRepository userRepository,
                              UserLoginInfoRepository userLoginInfoRepository,
                              UserInfoCollector userInfoCollector) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.userLoginInfoRepository = userLoginInfoRepository;
        this.userInfoCollector = userInfoCollector;
    }

    @EventListener
    public void handleAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        String username = (String) event.getAuthentication().getPrincipal();
        
        try {
            User user = userService.findByUsername(username);
            
            if (user != null) {
                int failedAttempts = user.getFailedLoginAttempts() + 1;
                user.setFailedLoginAttempts(failedAttempts);
                
                // 获取当前HTTP请求
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                HttpServletRequest request = null;
                String currentIpAddress = null;
                String currentDeviceType = null;
                String currentDeviceId = null;
                
                if (attributes != null) {
                    request = attributes.getRequest();
                    currentIpAddress = getClientIpAddress(request);
                    String userAgent = userInfoCollector.getUserAgent(request);
                    currentDeviceType = userInfoCollector.getDeviceType(userAgent);
                    Object didAttr = request.getAttribute("__device_id__");
                    currentDeviceId = didAttr != null ? didAttr.toString() : null;
                }
                
                // 检查是否应该锁定账户（仅在IP或设备变化时）
                boolean shouldLockAccount = false;
                LocalDateTime lockTime = null;
                LocalDateTime lockExpireTime = null;
                
                // 仅当失败次数严格大于阈值时才触发（>5）
                if (failedAttempts > MAX_FAILED_ATTEMPTS) {
                    // 获取用户最后一次登录信息
                    UserLoginInfo lastLogin = userLoginInfoRepository.findFirstByUserIdOrderByLoginTimeDesc(user.getId());
                    
                    if (lastLogin != null && currentIpAddress != null && currentDeviceType != null) {
                        String lastIpAddress = lastLogin.getIpAddress();
                        String lastDeviceType = lastLogin.getDeviceType();
                        String lastDeviceId = lastLogin.getDeviceId();
                        
                        // 检查IP或设备是否发生变化
                        boolean ipChanged = !currentIpAddress.equals(lastIpAddress);
                        boolean deviceChanged = !currentDeviceType.equals(lastDeviceType);
                        boolean deviceIdChanged = (currentDeviceId != null || lastDeviceId != null) && (lastDeviceId == null || currentDeviceId == null || !currentDeviceId.equals(lastDeviceId));
                        
                        // 只有当 设备ID、IP、设备类型 都发生变化时才锁定账户
                        shouldLockAccount = deviceIdChanged && ipChanged && deviceChanged;
                        
                        if (shouldLockAccount) {
                            lockTime = LocalDateTime.now();
                            lockExpireTime = LocalDateTime.now().plusHours(24);
                            
                            logger.warn("账户 {} 因登录失败次数过多且设备ID/IP/设备类型均变化被锁定24小时，" +
                                    "IP变化: {} ({} -> {}), 设备变化: {} ({} -> {}), 设备ID变化: {} ({} -> {})", 
                                    username, ipChanged, lastIpAddress, currentIpAddress, 
                                    deviceChanged, lastDeviceType, currentDeviceType,
                                    deviceIdChanged, lastDeviceId, currentDeviceId);
                        } else {
                            logger.info("账户 {} 登录失败次数达到{}次，但IP和设备未变化，暂不锁定账户", username, failedAttempts);
                        }
                    } else {
                        // 如果没有历史登录记录（理论上注册时已记录），为避免误伤，不进行锁定
                        logger.warn("账户 {} 登录失败次数达到{}次，但无历史登录记录，跳过锁定", username, failedAttempts);
                    }
                }
                
                // 使用直接更新，避免触发实体验证
                if (shouldLockAccount) {
                    userRepository.updateAccountLockStatus(user.getId(), failedAttempts, true, lockTime, lockExpireTime);
                } else {
                    userRepository.updateFailedLoginAttempts(user.getId(), failedAttempts);
                }
                
                // 记录失败日志
                if (currentIpAddress != null) {
                    logger.info("用户 {} 登录失败，当前失败次数: {}，IP地址: {}，设备类型: {}，设备ID: {}", 
                        username, failedAttempts, currentIpAddress, currentDeviceType, currentDeviceId);
                }
            }
        } catch (UsernameNotFoundException e) {
            // 用户不存在，记录日志但不处理
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String ipAddress = getClientIpAddress(request);
                logger.warn("尝试登录不存在的用户: {}，IP地址: {}", username, ipAddress);
            }
        }
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}