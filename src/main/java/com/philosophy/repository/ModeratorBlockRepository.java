package com.philosophy.repository;

import com.philosophy.model.ModeratorBlock;
import com.philosophy.model.User;
import com.philosophy.model.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 版主屏蔽用户数据访问层
 * 提供版主屏蔽用户相关的数据库操作
 * 
 * @author James Gosling (Java之父风格)
 * @since 1.0
 */
@Repository
public interface ModeratorBlockRepository extends JpaRepository<ModeratorBlock, Long> {

    /**
     * 检查版主是否屏蔽了指定用户在特定流派中
     * @param moderator 版主用户
     * @param blockedUser 被屏蔽用户
     * @param school 流派
     * @return 是否存在屏蔽关系
     */
    boolean existsByModeratorAndBlockedUserAndSchool(User moderator, User blockedUser, School school);

    /**
     * 检查版主是否屏蔽了指定用户在特定流派中
     * @param moderatorId 版主用户ID
     * @param blockedUserId 被屏蔽用户ID
     * @param schoolId 流派ID
     * @return 是否存在屏蔽关系
     */
    boolean existsByModeratorIdAndBlockedUserIdAndSchoolId(Long moderatorId, Long blockedUserId, Long schoolId);

    /**
     * 检查用户是否在特定流派中被屏蔽
     * @param blockedUserId 被屏蔽用户ID
     * @param schoolId 流派ID
     * @return 是否存在屏蔽关系
     */
    boolean existsByBlockedUserIdAndSchoolId(Long blockedUserId, Long schoolId);

    /**
     * 根据版主和流派查找屏蔽的用户列表
     * @param moderator 版主用户
     * @param school 流派
     * @return 屏蔽的用户列表
     */
    List<ModeratorBlock> findByModeratorAndSchoolOrderByCreatedAtDesc(User moderator, School school);

    /**
     * 根据版主和流派查找屏蔽的用户列表
     * @param moderatorId 版主用户ID
     * @param schoolId 流派ID
     * @return 屏蔽的用户列表
     */
    List<ModeratorBlock> findByModeratorIdAndSchoolIdOrderByCreatedAtDesc(Long moderatorId, Long schoolId);

    /**
     * 根据被屏蔽用户查找屏蔽关系
     * @param blockedUser 被屏蔽用户
     * @return 屏蔽关系列表
     */
    List<ModeratorBlock> findByBlockedUserOrderByCreatedAtDesc(User blockedUser);

    /**
     * 根据被屏蔽用户查找屏蔽关系
     * @param blockedUserId 被屏蔽用户ID
     * @return 屏蔽关系列表
     */
    List<ModeratorBlock> findByBlockedUserIdOrderByCreatedAtDesc(Long blockedUserId);

    /**
     * 查找特定的屏蔽关系
     * @param moderatorId 版主用户ID
     * @param blockedUserId 被屏蔽用户ID
     * @param schoolId 流派ID
     * @return 屏蔽关系
     */
    Optional<ModeratorBlock> findByModeratorIdAndBlockedUserIdAndSchoolId(Long moderatorId, Long blockedUserId, Long schoolId);

    /**
     * 删除特定的屏蔽关系
     * @param moderatorId 版主用户ID
     * @param blockedUserId 被屏蔽用户ID
     * @param schoolId 流派ID
     */
    void deleteByModeratorIdAndBlockedUserIdAndSchoolId(Long moderatorId, Long blockedUserId, Long schoolId);

    /**
     * 统计版主在特定流派中屏蔽的用户数量
     * @param moderatorId 版主用户ID
     * @param schoolId 流派ID
     * @return 屏蔽的用户数量
     */
    long countByModeratorIdAndSchoolId(Long moderatorId, Long schoolId);

    /**
     * 统计被屏蔽用户被屏蔽的次数
     * @param blockedUserId 被屏蔽用户ID
     * @return 被屏蔽的次数
     */
    long countByBlockedUserId(Long blockedUserId);

    /**
     * 查找在特定流派中被屏蔽的用户ID列表
     * @param schoolId 流派ID
     * @return 被屏蔽的用户ID列表
     */
    @Query("SELECT mb.blockedUser.id FROM ModeratorBlock mb WHERE mb.school.id = :schoolId")
    List<Long> findBlockedUserIdsBySchoolId(@Param("schoolId") Long schoolId);

    /**
     * 查找在特定流派及其子流派中被屏蔽的用户ID列表
     * @param schoolId 流派ID
     * @return 被屏蔽的用户ID列表
     */
    @Query("SELECT mb.blockedUser.id FROM ModeratorBlock mb WHERE mb.school.id = :schoolId OR mb.school.parent.id = :schoolId")
    List<Long> findBlockedUserIdsBySchoolIdAndSubSchools(@Param("schoolId") Long schoolId);

    /**
     * 查找版主屏蔽的所有用户ID列表
     * @param moderatorId 版主用户ID
     * @return 被屏蔽的用户ID列表
     */
    @Query("SELECT mb.blockedUser.id FROM ModeratorBlock mb WHERE mb.moderator.id = :moderatorId")
    List<Long> findBlockedUserIdsByModeratorId(@Param("moderatorId") Long moderatorId);
    
    /**
     * 根据用户ID或版主ID查找屏蔽关系
     * @param userId 用户ID
     * @param moderatorId 版主ID
     * @return 屏蔽关系列表
     */
    @Query("SELECT mb FROM ModeratorBlock mb WHERE mb.blockedUser.id = :userId OR mb.moderator.id = :moderatorId")
    List<ModeratorBlock> findByUserIdOrModeratorId(@Param("userId") Long userId, @Param("moderatorId") Long moderatorId);
}
