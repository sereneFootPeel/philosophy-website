package com.philosophy.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP地理位置服务
 * 用于判断IP地址是否在国外
 */
@Service
public class IpLocationService {

    private static final Logger logger = LoggerFactory.getLogger(IpLocationService.class);
    
    // 缓存IP地理位置信息，避免重复请求
    private final Map<String, Boolean> ipLocationCache = new ConcurrentHashMap<>();
    
    // 中国国家代码
    private static final String CHINA_COUNTRY_CODE = "CN";
    
    // 本地IP地址（用于开发环境）
    private static final String LOCALHOST_IP = "127.0.0.1";
    private static final String LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";
    
    private final RestTemplate restTemplate;
    
    public IpLocationService() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * 判断IP地址是否在国外
     * @param ipAddress IP地址
     * @return true表示在国外，false表示在中国或无法判断
     */
    public boolean isForeignIp(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return false;
        }
        
        // 清理IP地址（去除端口号等）
        ipAddress = ipAddress.trim();
        if (ipAddress.contains(",")) {
            // 处理多个IP的情况，取第一个
            ipAddress = ipAddress.split(",")[0].trim();
        }
        
        // 本地IP默认视为国内
        if (LOCALHOST_IP.equals(ipAddress) || LOCALHOST_IPV6.equals(ipAddress) || 
            ipAddress.startsWith("192.168.") || ipAddress.startsWith("10.") || 
            ipAddress.startsWith("172.16.") || ipAddress.startsWith("172.17.") ||
            ipAddress.startsWith("172.18.") || ipAddress.startsWith("172.19.") ||
            ipAddress.startsWith("172.20.") || ipAddress.startsWith("172.21.") ||
            ipAddress.startsWith("172.22.") || ipAddress.startsWith("172.23.") ||
            ipAddress.startsWith("172.24.") || ipAddress.startsWith("172.25.") ||
            ipAddress.startsWith("172.26.") || ipAddress.startsWith("172.27.") ||
            ipAddress.startsWith("172.28.") || ipAddress.startsWith("172.29.") ||
            ipAddress.startsWith("172.30.") || ipAddress.startsWith("172.31.")) {
            return false;
        }
        
        // 检查缓存
        Boolean cachedResult = ipLocationCache.get(ipAddress);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        try {
            // 使用ip-api.com免费API查询IP地理位置
            // 注意：免费版本有请求限制（每分钟45次）
            String url = "http://ip-api.com/json/" + ipAddress + "?fields=status,countryCode";
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && "success".equals(response.get("status"))) {
                String countryCode = (String) response.get("countryCode");
                boolean isForeign = !CHINA_COUNTRY_CODE.equals(countryCode);
                
                // 缓存结果（最多缓存1000个IP）
                if (ipLocationCache.size() < 1000) {
                    ipLocationCache.put(ipAddress, isForeign);
                }
                
                logger.debug("IP {} 地理位置: {}, 是否国外: {}", ipAddress, countryCode, isForeign);
                return isForeign;
            }
        } catch (RestClientException e) {
            logger.warn("查询IP地理位置失败: {}, 错误: {}", ipAddress, e.getMessage());
        } catch (Exception e) {
            logger.error("查询IP地理位置时发生异常: {}", ipAddress, e);
        }
        
        // 如果查询失败，默认视为国内（保守策略）
        return false;
    }
    
    /**
     * 从HttpServletRequest中获取IP并判断是否在国外
     * @param request HTTP请求
     * @return true表示在国外，false表示在中国或无法判断
     */
    public boolean isForeignIp(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        
        String ipAddress = getClientIpAddress(request);
        return isForeignIp(ipAddress);
    }
    
    /**
     * 从HttpServletRequest中获取客户端IP地址
     * @param request HTTP请求
     * @return IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ipAddress = request.getHeader("X-Forwarded-For");
        
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
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
     * 清除缓存（可选，用于测试或定期清理）
     */
    public void clearCache() {
        ipLocationCache.clear();
    }
}

