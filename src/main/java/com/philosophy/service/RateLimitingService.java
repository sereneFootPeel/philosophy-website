package com.philosophy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 速率限制服务，用于防止DDoS攻击
 * 提供IP级别和全局级别的限流功能
 */
@Service
public class RateLimitingService {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingService.class);

    // IP级别的限流配置
    private static final int IP_MAX_REQUESTS_PER_HOUR = 10; // 每个IP每小时最多10次
    private static final int IP_MAX_REQUESTS_PER_MINUTE = 3; // 每个IP每分钟最多3次
    
    // 全局限流配置
    private static final int GLOBAL_MAX_REQUESTS_PER_MINUTE = 100; // 全局每分钟最多100次
    private static final int GLOBAL_MAX_REQUESTS_PER_HOUR = 1000; // 全局每小时最多1000次

    // IP级别的请求记录
    private static class IpRequestRecord {
        private final ConcurrentHashMap<String, LocalDateTime> requests = new ConcurrentHashMap<>();
        private final AtomicInteger requestCount = new AtomicInteger(0);
    }

    // 全局请求记录
    private static class GlobalRequestRecord {
        private LocalDateTime minuteWindowStart;
        private LocalDateTime hourWindowStart;
        private final AtomicInteger minuteCount = new AtomicInteger(0);
        private final AtomicInteger hourCount = new AtomicInteger(0);
        
        public GlobalRequestRecord() {
            this.minuteWindowStart = LocalDateTime.now();
            this.hourWindowStart = LocalDateTime.now();
        }
    }

    private final ConcurrentHashMap<String, IpRequestRecord> ipRecords = new ConcurrentHashMap<>();
    private final GlobalRequestRecord globalRecord = new GlobalRequestRecord();

    /**
     * 检查IP是否允许发送验证码
     * @param ipAddress IP地址
     * @return RateLimitResult 包含是否允许和剩余等待时间
     */
    public synchronized RateLimitResult checkIpRateLimit(String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        IpRequestRecord record = ipRecords.computeIfAbsent(ipAddress, k -> new IpRequestRecord());

        // 清理过期的请求记录（超过1小时）
        record.requests.entrySet().removeIf(entry -> 
            Duration.between(entry.getValue(), now).getSeconds() > 3600
        );

        // 检查每分钟限制
        long minuteRequests = record.requests.values().stream()
            .filter(time -> Duration.between(time, now).getSeconds() <= 60)
            .count();
        
        if (minuteRequests >= IP_MAX_REQUESTS_PER_MINUTE) {
            long oldestRequestTime = record.requests.values().stream()
                .filter(time -> Duration.between(time, now).getSeconds() <= 60)
                .mapToLong(time -> Duration.between(time, now).getSeconds())
                .min()
                .orElse(60);
            long waitSeconds = 60 - oldestRequestTime + 1;
            logger.warn("IP {} 触发每分钟限流: {} 次/分钟，需要等待 {} 秒", 
                ipAddress, minuteRequests, waitSeconds);
            return new RateLimitResult(false, waitSeconds, "请求过于频繁，请稍后再试");
        }

        // 检查每小时限制
        long hourRequests = record.requests.values().stream()
            .filter(time -> Duration.between(time, now).getSeconds() <= 3600)
            .count();
        
        if (hourRequests >= IP_MAX_REQUESTS_PER_HOUR) {
            long oldestRequestTime = record.requests.values().stream()
                .filter(time -> Duration.between(time, now).getSeconds() <= 3600)
                .mapToLong(time -> Duration.between(time, now).getSeconds())
                .min()
                .orElse(3600);
            long waitSeconds = 3600 - oldestRequestTime + 1;
            logger.warn("IP {} 触发每小时限流: {} 次/小时，需要等待 {} 秒", 
                ipAddress, hourRequests, waitSeconds);
            return new RateLimitResult(false, waitSeconds, "今日请求次数已达上限，请稍后再试");
        }

        // 记录此次请求
        String requestId = String.valueOf(System.currentTimeMillis()) + "-" + record.requestCount.incrementAndGet();
        record.requests.put(requestId, now);

        // 定期清理旧的记录（每100次请求清理一次）
        if (record.requestCount.get() % 100 == 0) {
            record.requests.entrySet().removeIf(entry -> 
                Duration.between(entry.getValue(), now).getSeconds() > 3600
            );
        }

        return new RateLimitResult(true, 0, null);
    }

    /**
     * 检查全局是否允许发送验证码
     * @return RateLimitResult 包含是否允许和剩余等待时间
     */
    public synchronized RateLimitResult checkGlobalRateLimit() {
        LocalDateTime now = LocalDateTime.now();
        
        // 检查每分钟限制
        long minuteElapsed = Duration.between(globalRecord.minuteWindowStart, now).getSeconds();
        if (minuteElapsed >= 60) {
            // 重置分钟窗口
            globalRecord.minuteWindowStart = now;
            globalRecord.minuteCount.set(0);
        }
        
        if (globalRecord.minuteCount.get() >= GLOBAL_MAX_REQUESTS_PER_MINUTE) {
            long waitSeconds = 60 - minuteElapsed + 1;
            logger.warn("触发全局每分钟限流: {} 次/分钟，需要等待 {} 秒", 
                globalRecord.minuteCount.get(), waitSeconds);
            return new RateLimitResult(false, waitSeconds, "系统繁忙，请稍后再试");
        }

        // 检查每小时限制
        long hourElapsed = Duration.between(globalRecord.hourWindowStart, now).getSeconds();
        if (hourElapsed >= 3600) {
            // 重置小时窗口
            globalRecord.hourWindowStart = now;
            globalRecord.hourCount.set(0);
        }
        
        if (globalRecord.hourCount.get() >= GLOBAL_MAX_REQUESTS_PER_HOUR) {
            long waitSeconds = 3600 - hourElapsed + 1;
            logger.warn("触发全局每小时限流: {} 次/小时，需要等待 {} 秒", 
                globalRecord.hourCount.get(), waitSeconds);
            return new RateLimitResult(false, waitSeconds, "系统繁忙，请稍后再试");
        }

        // 记录此次请求
        globalRecord.minuteCount.incrementAndGet();
        globalRecord.hourCount.incrementAndGet();

        return new RateLimitResult(true, 0, null);
    }

    /**
     * 检查IP和全局是否都允许发送验证码
     * @param ipAddress IP地址
     * @return RateLimitResult 包含是否允许和剩余等待时间
     */
    public RateLimitResult checkRateLimit(String ipAddress) {
        // 先检查全局限制
        RateLimitResult globalResult = checkGlobalRateLimit();
        if (!globalResult.isAllowed()) {
            return globalResult;
        }

        // 再检查IP限制
        RateLimitResult ipResult = checkIpRateLimit(ipAddress);
        if (!ipResult.isAllowed()) {
            return ipResult;
        }

        return new RateLimitResult(true, 0, null);
    }

    /**
     * 速率限制结果
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final long waitSeconds;
        private final String message;

        public RateLimitResult(boolean allowed, long waitSeconds, String message) {
            this.allowed = allowed;
            this.waitSeconds = waitSeconds;
            this.message = message;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public long getWaitSeconds() {
            return waitSeconds;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 清理过期的IP记录（用于内存管理）
     */
    public void cleanupExpiredRecords() {
        LocalDateTime now = LocalDateTime.now();
        ipRecords.entrySet().removeIf(entry -> {
            IpRequestRecord record = entry.getValue();
            // 如果最后一条记录超过2小时，则删除该IP的记录
            return record.requests.values().stream()
                .allMatch(time -> Duration.between(time, now).getSeconds() > 7200);
        });
    }
}

