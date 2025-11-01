package com.philosophy.repository;

import com.philosophy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    
    // 统计在指定时间之后创建的用户数量
    long countByCreatedAtAfter(LocalDateTime dateTime);
    
    // 查找最新的用户
    List<User> findTop10ByOrderByCreatedAtDesc();
    
    // 按角色查找用户
    List<User> findByRole(String role);
    
    // 查找指定流派的版主
    List<User> findByRoleAndAssignedSchoolId(String role, Long schoolId);
    
    /**
     * 查找过去24小时内注册的普通用户（基于注册时间）
     * 用于重复账户检测，只返回普通用户
     */
    @Query("SELECT u FROM User u WHERE u.createdAt >= :sinceTime AND u.role = 'USER' AND u.id != :excludeUserId ORDER BY u.createdAt ASC")
    List<User> findRecentNormalUsers(@Param("sinceTime") LocalDateTime sinceTime, @Param("excludeUserId") Long excludeUserId);
    
    /**
     * 查找过去24小时内注册的普通用户，按注册时间升序排列
     * 用于重复账户检测，保留最新的用户
     */
    @Query("SELECT u FROM User u WHERE u.createdAt >= :sinceTime AND u.role = 'USER' AND u.id != :excludeUserId ORDER BY u.createdAt ASC")
    List<User> findDuplicateCandidates(@Param("sinceTime") LocalDateTime sinceTime, @Param("excludeUserId") Long excludeUserId);
    
    /**
     * 根据用户名搜索用户（忽略大小写）
     */
    List<User> findByUsernameContainingIgnoreCase(String username);

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.likeCount = u.likeCount + :delta WHERE u.id = :id")
    void updateLikeCount(Long id, int delta);
    
    /**
     * 直接更新登录失败次数，避免触发实体验证
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.failedLoginAttempts = :attempts WHERE u.id = :id")
    void updateFailedLoginAttempts(@Param("id") Long id, @Param("attempts") int attempts);
    
    /**
     * 直接更新账户锁定状态，避免触发实体验证
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.failedLoginAttempts = :attempts, u.accountLocked = :locked, u.lockTime = :lockTime, u.lockExpireTime = :lockExpireTime WHERE u.id = :id")
    void updateAccountLockStatus(@Param("id") Long id, @Param("attempts") int attempts, @Param("locked") boolean locked, @Param("lockTime") LocalDateTime lockTime, @Param("lockExpireTime") LocalDateTime lockExpireTime);
    
    /**
     * 重置登录失败次数和账户锁定状态，避免触发实体验证
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.accountLocked = false, u.lockTime = null, u.lockExpireTime = null WHERE u.username = :username")
    void resetFailedAttemptsByUsername(@Param("username") String username);
}