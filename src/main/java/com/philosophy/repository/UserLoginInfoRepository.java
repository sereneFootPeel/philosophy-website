package com.philosophy.repository;

import com.philosophy.model.User;
import com.philosophy.model.UserLoginInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface UserLoginInfoRepository extends JpaRepository<UserLoginInfo, Long> {

    /**
     * 根据用户ID查找所有登录记录
     */
    List<UserLoginInfo> findByUserOrderByLoginTimeDesc(User user);

    /**
     * 根据用户ID查找所有登录记录
     */
    List<UserLoginInfo> findByUserIdOrderByLoginTimeDesc(Long userId);

    /**
     * 获取用户的所有唯一IP地址
     */
    @Query("SELECT DISTINCT u.ipAddress FROM UserLoginInfo u WHERE u.user.id = :userId")
    Set<String> findDistinctIpAddressesByUserId(@Param("userId") Long userId);

    /**
     * 获取用户的所有唯一设备类型
     */
    @Query("SELECT DISTINCT u.deviceType FROM UserLoginInfo u WHERE u.user.id = :userId")
    Set<String> findDistinctDeviceTypesByUserId(@Param("userId") Long userId);

    /**
     * 获取用户的所有唯一浏览器
     */
    @Query("SELECT DISTINCT u.browser FROM UserLoginInfo u WHERE u.user.id = :userId")
    Set<String> findDistinctBrowsersByUserId(@Param("userId") Long userId);

    /**
     * 获取用户的所有唯一操作系统
     */
    @Query("SELECT DISTINCT u.operatingSystem FROM UserLoginInfo u WHERE u.user.id = :userId")
    Set<String> findDistinctOperatingSystemsByUserId(@Param("userId") Long userId);

    /**
     * 获取用户的所有唯一设备ID
     */
    @Query("SELECT DISTINCT u.deviceId FROM UserLoginInfo u WHERE u.user.id = :userId AND u.deviceId IS NOT NULL")
    Set<String> findDistinctDeviceIdsByUserId(@Param("userId") Long userId);

    /**
     * 统计用户的登录次数
     */
    long countByUserId(Long userId);

    /**
     * 获取用户最近的登录记录
     */
    UserLoginInfo findFirstByUserIdOrderByLoginTimeDesc(Long userId);
    
    /**
     * 获取用户最早的登录记录
     */
    UserLoginInfo findFirstByUserIdOrderByLoginTimeAsc(Long userId);

    /**
     * 获取用户最近的登录记录（分页）
     */
    @Query("SELECT u FROM UserLoginInfo u WHERE u.user.id = :userId ORDER BY u.loginTime DESC")
    List<UserLoginInfo> findRecentByUserId(@Param("userId") Long userId, org.springframework.data.domain.Pageable pageable);

    /**
     * 查找指定时间之后的登录记录
     */
    List<UserLoginInfo> findByLoginTimeAfter(java.time.LocalDateTime loginTime);

    /**
     * 统计指定时间之后的登录记录数量
     */
    long countByLoginTimeAfter(java.time.LocalDateTime loginTime);

    // 删除复杂的重复检测相关查询方法，简化注册流程
    
    /**
     * 查找同一天内使用相同IP和设备类型注册的用户（排除指定用户ID、管理员和版主）
     */
    @Query("SELECT u.user FROM UserLoginInfo u WHERE u.ipAddress = :ipAddress AND u.deviceType = :deviceType AND DATE(u.user.createdAt) = CURRENT_DATE AND u.user.id != :excludeUserId AND u.user.role = 'USER'")
    List<User> findDuplicateUsersByIpAndDeviceToday(@Param("ipAddress") String ipAddress, @Param("deviceType") String deviceType, @Param("excludeUserId") Long excludeUserId);

    /**
     * 查找过去24小时内使用相同IP和设备类型注册的用户（排除指定用户ID、管理员和版主）
     */
    @Query("SELECT u.user FROM UserLoginInfo u WHERE u.ipAddress = :ipAddress AND u.deviceType = :deviceType AND u.user.createdAt >= :sinceTime AND u.user.id != :excludeUserId AND u.user.role = 'USER'")
    List<User> findDuplicateUsersByIpAndDeviceSince(@Param("ipAddress") String ipAddress,
                                                    @Param("deviceType") String deviceType,
                                                    @Param("excludeUserId") Long excludeUserId,
                                                    @Param("sinceTime") java.time.LocalDateTime sinceTime);

    /**
     * 查找过去24小时内使用相同设备ID注册的用户（排除指定用户ID、管理员和版主）
     */
    @Query("SELECT u.user FROM UserLoginInfo u WHERE u.deviceId = :deviceId AND u.user.createdAt >= :sinceTime AND u.user.id != :excludeUserId AND u.user.role = 'USER'")
    List<User> findDuplicateUsersByDeviceIdSince(@Param("deviceId") String deviceId,
                                                 @Param("excludeUserId") Long excludeUserId,
                                                 @Param("sinceTime") java.time.LocalDateTime sinceTime);
}