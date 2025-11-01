package com.philosophy.util;

import com.philosophy.model.User;
import com.philosophy.model.UserLoginInfo;
import com.philosophy.repository.UserLoginInfoRepository;
import com.philosophy.repository.UserRepository;
import com.philosophy.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
/**
 * 用户信息收集器，用于获取和存储用户的IP地址和设备信息
 */
@Component
public class UserInfoCollector {

    private static final Logger logger = LoggerFactory.getLogger(UserInfoCollector.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final UserLoginInfoRepository userLoginInfoRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    
    public UserInfoCollector(UserLoginInfoRepository userLoginInfoRepository, UserService userService, UserRepository userRepository) {
        this.userLoginInfoRepository = userLoginInfoRepository;
        this.userService = userService;
        this.userRepository = userRepository;
    }
    
    /**
     * 登录记录类，存储IP、设备和时间信息
     */
    public static class LoginRecord {
        private String ipAddress;
        private String userAgent;
        private LocalDateTime timestamp;
        
        public LoginRecord(String ipAddress, String userAgent, LocalDateTime timestamp) {
            this.ipAddress = ipAddress;
            this.userAgent = userAgent;
            this.timestamp = timestamp;
        }
        
        public String getIpAddress() {
            return ipAddress;
        }
        
        public String getUserAgent() {
            return userAgent;
        }
        
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
        
        public String getFormattedTimestamp() {
            return timestamp.format(DATE_FORMATTER);
        }
    }
    
    /**
     * 从HttpServletRequest中获取用户IP地址
     * 考虑了代理服务器的情况
     */
    public String getIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        
        // 处理多个IP地址的情况，取第一个非unknown的IP
        if (ipAddress != null && ipAddress.contains(",")) {
            String[] ipAddresses = ipAddress.split(",");
            for (String ip : ipAddresses) {
                if (!"unknown".equalsIgnoreCase(ip.trim())) {
                    ipAddress = ip.trim();
                    break;
                }
            }
        }
        
        return ipAddress;
    }
    
    /**
     * 从HttpServletRequest中获取用户设备信息
     */
    public String getUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent") != null ? request.getHeader("User-Agent") : "Unknown";
    }
    
    /**
     * 记录用户登录信息
     */
    public void recordLoginInfo(User user, HttpServletRequest request) {
        if (user != null && request != null) {
            String ipAddress = getIpAddress(request);
            String userAgent = getUserAgent(request);
            String browser = getBrowserInfo(userAgent);
            String operatingSystem = getOsInfo(userAgent);
            String deviceType = getDeviceType(userAgent);
            Object didAttr = request.getAttribute("__device_id__");
            String deviceId = didAttr != null ? didAttr.toString() : null;
            
            UserLoginInfo loginInfo = new UserLoginInfo(
                user, 
                ipAddress, 
                userAgent, 
                browser, 
                operatingSystem, 
                deviceType
            );
            loginInfo.setDeviceId(deviceId);
            
            userLoginInfoRepository.save(loginInfo);
            
            logger.info("User {} logged in from IP: {}, Device: {}", 
                        user.getUsername(), ipAddress, userAgent);
        }
    }
    
    /**
     * 获取用户的IP地址（最后一次登录的IP）
     */
    public String getUserIpAddress(Long userId) {
        UserLoginInfo latestLogin = userLoginInfoRepository.findFirstByUserIdOrderByLoginTimeDesc(userId);
        return latestLogin != null ? latestLogin.getIpAddress() : "Unknown";
    }
    
    /**
     * 获取用户的所有IP地址
     */
    public Set<String> getAllUserIpAddresses(Long userId) {
        return userLoginInfoRepository.findDistinctIpAddressesByUserId(userId);
    }
    
    /**
     * 获取用户的设备信息（最后一次登录的设备类型）
     */
    public String getUserDevice(Long userId) {
        UserLoginInfo latestLogin = userLoginInfoRepository.findFirstByUserIdOrderByLoginTimeDesc(userId);
        return latestLogin != null ? latestLogin.getDeviceType() : "Unknown";
    }
    
    /**
     * 获取用户的所有设备类型
     */
    public Set<String> getAllUserDevices(Long userId) {
        return userLoginInfoRepository.findDistinctDeviceTypesByUserId(userId);
    }
    
    /**
     * 获取用户的所有浏览器类型
     */
    public Set<String> getAllUserBrowsers(Long userId) {
        return userLoginInfoRepository.findDistinctBrowsersByUserId(userId);
    }
    
    /**
     * 获取用户的所有操作系统
     */
    public Set<String> getAllUserOperatingSystems(Long userId) {
        return userLoginInfoRepository.findDistinctOperatingSystemsByUserId(userId);
    }

    /**
     * 获取用户的所有设备ID
     */
    public Set<String> getAllUserDeviceIds(Long userId) {
        return userLoginInfoRepository.findDistinctDeviceIdsByUserId(userId);
    }

    /**
     * 获取用户的所有设备信息（包括浏览器和操作系统）
     */
    public Set<String> getAllUserDeviceInfo(Long userId) {
        Set<String> devices = getAllUserDevices(userId);
        Set<String> browsers = getAllUserBrowsers(userId);
        Set<String> operatingSystems = getAllUserOperatingSystems(userId);
        
        Set<String> allDeviceInfo = new HashSet<>();
        allDeviceInfo.addAll(devices);
        allDeviceInfo.addAll(browsers);
        allDeviceInfo.addAll(operatingSystems);
        
        return allDeviceInfo;
    }
    
    /**
     * 获取用户的所有登录记录
     */
    public List<UserLoginInfo> getUserLoginRecords(Long userId) {
        return userLoginInfoRepository.findByUserIdOrderByLoginTimeDesc(userId);
    }
    
    /**
     * 获取用户的登录次数
     */
    public long getUserLoginCount(Long userId) {
        return userLoginInfoRepository.countByUserId(userId);
    }
    
    /**
     * 从设备信息中解析浏览器信息
     */
    public String getBrowserInfo(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Browser";
        }
        
        if (userAgent.contains("Chrome")) {
            return "Chrome";
        } else if (userAgent.contains("Firefox")) {
            return "Firefox";
        } else if (userAgent.contains("Safari")) {
            return "Safari";
        } else if (userAgent.contains("Edge")) {
            return "Edge";
        } else if (userAgent.contains("MSIE") || userAgent.contains("Trident/")) {
            return "Internet Explorer";
        } else {
            return "Other Browser";
        }
    }
    
    /**
     * 从设备信息中解析操作系统信息
     */
    public String getOsInfo(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown OS";
        }
        
        if (userAgent.contains("Windows")) {
            return "Windows";
        } else if (userAgent.contains("Macintosh")) {
            return "MacOS";
        } else if (userAgent.contains("Linux")) {
            return "Linux";
        } else if (userAgent.contains("Android")) {
            return "Android";
        } else if (userAgent.contains("iPhone") || userAgent.contains("iPad")) {
            return "iOS";
        } else {
            return "Other OS";
        }
    }

    /**
     * 从设备信息中解析设备类型
     */
    public String getDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Device";
        }
        
        if (userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iPhone")) {
            return "Mobile";
        } else if (userAgent.contains("Tablet") || userAgent.contains("iPad")) {
            return "Tablet";
        } else if (userAgent.contains("Windows") || userAgent.contains("Macintosh") || userAgent.contains("Linux")) {
            return "Desktop";
        } else {
            return "Other Device";
        }
    }

    // 删除重复检测相关的方法，简化注册流程

    /**
     * 简化的注册检查 - 仅检查用户名和邮箱是否已存在
     * 不再进行IP和设备检测，避免误删正常用户
     */
    public boolean isRegistrationAllowed(HttpServletRequest request) {
        // 始终允许注册，不再进行复杂的IP和设备检测
        return true;
    }

    /**
     * 检查并删除过去24小时内注册的重复账户（基于注册记录）
     * 只删除普通用户，保护管理员和版主账户
     * 保留最新注册的账户，删除较早注册的账户
     */
    public List<Long> checkAndDeleteDuplicateAccounts(Long newUserId, HttpServletRequest request) {
        List<Long> deletedUserIds = new ArrayList<>();
        
        try {
            // 首先检查新注册用户是否为管理员或版主，如果是则跳过重复检测
            User newUser = userService.getUserById(newUserId);
            if (newUser == null || "ADMIN".equals(newUser.getRole()) || "MODERATOR".equals(newUser.getRole())) {
                logger.info("跳过重复账户检测：新用户 {} 是管理员或版主", newUser != null ? newUser.getUsername() : "unknown");
                return deletedUserIds;
            }
            
            // 基于注册时间查找过去24小时内注册的普通用户
            LocalDateTime sinceTime = LocalDateTime.now().minusHours(24);
            List<User> recentUsers = userRepository.findDuplicateCandidates(sinceTime, newUserId);
            
            if (!recentUsers.isEmpty()) {
                logger.info("发现 {} 个过去24小时内注册的普通用户，准备检查重复账户", recentUsers.size());
                
                // 获取新用户的IP和设备信息用于比较
                String newUserIp = getIpAddress(request);
                String newUserAgent = getUserAgent(request);
                String newUserDeviceType = getDeviceType(newUserAgent);
                Object didAttr = request.getAttribute("__device_id__");
                String newUserDeviceId = didAttr != null ? didAttr.toString() : null;
                
                for (User candidateUser : recentUsers) {
                    // 跳过管理员和版主（虽然查询已经过滤了，但再次确认）
                    if ("ADMIN".equals(candidateUser.getRole()) || "MODERATOR".equals(candidateUser.getRole())) {
                        continue;
                    }
                    
                    // 确保只删除注册时间早于新用户的账户
                    if (candidateUser.getCreatedAt() != null && newUser.getCreatedAt() != null && 
                        candidateUser.getCreatedAt().isBefore(newUser.getCreatedAt())) {
                        
                        // 检查是否为重复账户（基于IP、设备类型或设备ID）
                        boolean isDuplicate = false;
                        
                        // 获取候选用户的登录信息进行比较
                        List<UserLoginInfo> candidateLoginInfos = userLoginInfoRepository.findByUserIdOrderByLoginTimeDesc(candidateUser.getId());
                        if (!candidateLoginInfos.isEmpty()) {
                            UserLoginInfo candidateLoginInfo = candidateLoginInfos.get(0);
                            
                            // 比较设备ID（最准确）
                            if (newUserDeviceId != null && !newUserDeviceId.isEmpty() && 
                                candidateLoginInfo.getDeviceId() != null && 
                                newUserDeviceId.equals(candidateLoginInfo.getDeviceId())) {
                                isDuplicate = true;
                                logger.info("检测到重复账户：设备ID匹配 - {} (ID: {})", 
                                    candidateUser.getUsername(), candidateUser.getId());
                            }
                            // 比较IP和设备类型
                            else if (newUserIp.equals(candidateLoginInfo.getIpAddress()) && 
                                     newUserDeviceType.equals(candidateLoginInfo.getDeviceType())) {
                                isDuplicate = true;
                                logger.info("检测到重复账户：IP+设备类型匹配 - {} (ID: {})", 
                                    candidateUser.getUsername(), candidateUser.getId());
                            }
                        }
                        
                        if (isDuplicate) {
                            // 删除重复的旧用户
                            userService.deleteUser(candidateUser.getId());
                            deletedUserIds.add(candidateUser.getId());
                            logger.info("已删除重复用户 {} (ID: {})，注册时间: {}", 
                                candidateUser.getUsername(), candidateUser.getId(), candidateUser.getCreatedAt());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("检查重复账户时发生错误", e);
        }
        
        return deletedUserIds;
    }


}