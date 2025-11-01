package com.philosophy.repository;

import com.philosophy.model.UserBlock;
import com.philosophy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户屏蔽关系数据访问层
 */
@Repository
public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    /**
     * 检查用户是否屏蔽了另一个用户
     */
    boolean existsByBlockerAndBlocked(User blocker, User blocked);

    /**
     * 检查用户是否屏蔽了另一个用户（通过ID）
     */
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /**
     * 查找屏蔽关系
     */
    Optional<UserBlock> findByBlockerAndBlocked(User blocker, User blocked);

    /**
     * 查找屏蔽关系（通过ID）
     */
    Optional<UserBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /**
     * 获取用户屏蔽的所有用户列表
     */
    List<UserBlock> findByBlockerOrderByCreatedAtDesc(User blocker);

    /**
     * 获取用户屏蔽的所有用户ID列表
     */
    @Query("SELECT ub.blocked.id FROM UserBlock ub WHERE ub.blocker.id = :blockerId")
    List<Long> findBlockedUserIdsByBlockerId(@Param("blockerId") Long blockerId);

    /**
     * 获取被用户屏蔽的所有用户ID列表
     */
    @Query("SELECT ub.blocker.id FROM UserBlock ub WHERE ub.blocked.id = :blockedId")
    List<Long> findBlockerUserIdsByBlockedId(@Param("blockedId") Long blockedId);

    /**
     * 删除屏蔽关系
     */
    void deleteByBlockerAndBlocked(User blocker, User blocked);

    /**
     * 删除屏蔽关系（通过ID）
     */
    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /**
     * 统计用户屏蔽的用户数量
     */
    long countByBlocker(User blocker);

    /**
     * 统计被用户屏蔽的数量
     */
    long countByBlocked(User blocked);
    
    /**
     * 根据用户ID查找屏蔽关系（作为屏蔽者或被屏蔽者）
     */
    @Query("SELECT ub FROM UserBlock ub WHERE ub.blocker.id = :userId OR ub.blocked.id = :blockedUserId")
    List<UserBlock> findByUserIdOrBlockedUserId(@Param("userId") Long userId, @Param("blockedUserId") Long blockedUserId);
}
