package com.philosophy.service;

import com.philosophy.model.ModeratorBlock;
import com.philosophy.model.User;
import com.philosophy.model.School;
import com.philosophy.repository.ModeratorBlockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 版主屏蔽用户服务类
 * 处理版主屏蔽用户相关的业务逻辑
 * 
 * @author James Gosling (Java之父风格)
 * @since 1.0
 */
@Service
@Transactional
public class ModeratorBlockService {

    private final ModeratorBlockRepository moderatorBlockRepository;
    private final UserService userService;
    private final SchoolService schoolService;

    @Autowired
    public ModeratorBlockService(ModeratorBlockRepository moderatorBlockRepository, 
                                UserService userService, 
                                SchoolService schoolService) {
        this.moderatorBlockRepository = moderatorBlockRepository;
        this.userService = userService;
        this.schoolService = schoolService;
    }

    /**
     * 版主屏蔽用户在特定流派中
     * @param moderatorId 版主用户ID
     * @param blockedUserId 被屏蔽用户ID
     * @param schoolId 流派ID
     * @param reason 屏蔽原因
     * @return 是否成功屏蔽
     */
    public boolean blockUserInSchool(Long moderatorId, Long blockedUserId, Long schoolId, String reason) {
        // 验证版主权限
        if (!isModerator(moderatorId)) {
            throw new SecurityException("Only moderators can block users in schools");
        }

        // 不能屏蔽自己
        if (moderatorId.equals(blockedUserId)) {
            return false;
        }

        // 检查用户和流派是否存在
        User moderator = userService.getUserById(moderatorId);
        User blockedUser = userService.getUserById(blockedUserId);
        School school = schoolService.getSchoolById(schoolId);
        
        if (moderator == null || blockedUser == null || school == null) {
            return false;
        }

        // 检查版主是否管理该流派
        if (!isModeratorOfSchool(moderatorId, schoolId)) {
            throw new SecurityException("Moderator does not have permission to block users in this school");
        }

        // 检查是否已经屏蔽
        if (moderatorBlockRepository.existsByModeratorIdAndBlockedUserIdAndSchoolId(moderatorId, blockedUserId, schoolId)) {
            return false; // 已经屏蔽过了
        }

        // 创建屏蔽关系
        ModeratorBlock moderatorBlock = new ModeratorBlock(moderator, blockedUser, school, reason);
        moderatorBlockRepository.save(moderatorBlock);
        return true;
    }

    /**
     * 取消版主屏蔽用户在特定流派中
     * @param moderatorId 版主用户ID
     * @param blockedUserId 被屏蔽用户ID
     * @param schoolId 流派ID
     * @return 是否成功取消屏蔽
     */
    public boolean unblockUserInSchool(Long moderatorId, Long blockedUserId, Long schoolId) {
        // 验证版主权限
        if (!isModerator(moderatorId)) {
            throw new SecurityException("Only moderators can unblock users in schools");
        }

        // 检查版主是否管理该流派
        if (!isModeratorOfSchool(moderatorId, schoolId)) {
            throw new SecurityException("Moderator does not have permission to unblock users in this school");
        }

        // 删除屏蔽关系
        moderatorBlockRepository.deleteByModeratorIdAndBlockedUserIdAndSchoolId(moderatorId, blockedUserId, schoolId);
        return true;
    }

    /**
     * 检查用户是否在特定流派中被版主屏蔽
     * @param blockedUserId 被屏蔽用户ID
     * @param schoolId 流派ID
     * @return 是否被屏蔽
     */
    @Transactional(readOnly = true)
    public boolean isUserBlockedInSchool(Long blockedUserId, Long schoolId) {
        return moderatorBlockRepository.existsByBlockedUserIdAndSchoolId(blockedUserId, schoolId);
    }

    /**
     * 检查用户是否在特定流派及其子流派中被版主屏蔽
     * @param blockedUserId 被屏蔽用户ID
     * @param schoolId 流派ID
     * @return 是否被屏蔽
     */
    @Transactional(readOnly = true)
    public boolean isUserBlockedInSchoolAndSubSchools(Long blockedUserId, Long schoolId) {
        List<Long> blockedUserIds = moderatorBlockRepository.findBlockedUserIdsBySchoolIdAndSubSchools(schoolId);
        return blockedUserIds.contains(blockedUserId);
    }

    /**
     * 获取版主在特定流派中屏蔽的用户列表
     * @param moderatorId 版主用户ID
     * @param schoolId 流派ID
     * @return 屏蔽的用户列表
     */
    @Transactional(readOnly = true)
    public List<ModeratorBlock> getBlockedUsersInSchool(Long moderatorId, Long schoolId) {
        return moderatorBlockRepository.findByModeratorIdAndSchoolIdOrderByCreatedAtDesc(moderatorId, schoolId);
    }

    /**
     * 获取被屏蔽用户的所有屏蔽关系
     * @param blockedUserId 被屏蔽用户ID
     * @return 屏蔽关系列表
     */
    @Transactional(readOnly = true)
    public List<ModeratorBlock> getBlockRelationshipsForUser(Long blockedUserId) {
        return moderatorBlockRepository.findByBlockedUserIdOrderByCreatedAtDesc(blockedUserId);
    }

    /**
     * 获取在特定流派中被屏蔽的用户ID列表
     * @param schoolId 流派ID
     * @return 被屏蔽的用户ID列表
     */
    @Transactional(readOnly = true)
    public List<Long> getBlockedUserIdsInSchool(Long schoolId) {
        return moderatorBlockRepository.findBlockedUserIdsBySchoolId(schoolId);
    }

    /**
     * 获取在特定流派及其子流派中被屏蔽的用户ID列表
     * @param schoolId 流派ID
     * @return 被屏蔽的用户ID列表
     */
    @Transactional(readOnly = true)
    public List<Long> getBlockedUserIdsInSchoolAndSubSchools(Long schoolId) {
        return moderatorBlockRepository.findBlockedUserIdsBySchoolIdAndSubSchools(schoolId);
    }

    /**
     * 统计版主在特定流派中屏蔽的用户数量
     * @param moderatorId 版主用户ID
     * @param schoolId 流派ID
     * @return 屏蔽的用户数量
     */
    @Transactional(readOnly = true)
    public long getBlockedUserCountInSchool(Long moderatorId, Long schoolId) {
        return moderatorBlockRepository.countByModeratorIdAndSchoolId(moderatorId, schoolId);
    }

    /**
     * 统计用户被版主屏蔽的次数
     * @param blockedUserId 被屏蔽用户ID
     * @return 被屏蔽的次数
     */
    @Transactional(readOnly = true)
    public long getBlockCountForUser(Long blockedUserId) {
        return moderatorBlockRepository.countByBlockedUserId(blockedUserId);
    }

    /**
     * 获取特定的屏蔽关系
     * @param moderatorId 版主用户ID
     * @param blockedUserId 被屏蔽用户ID
     * @param schoolId 流派ID
     * @return 屏蔽关系
     */
    @Transactional(readOnly = true)
    public Optional<ModeratorBlock> getBlockRelationship(Long moderatorId, Long blockedUserId, Long schoolId) {
        return moderatorBlockRepository.findByModeratorIdAndBlockedUserIdAndSchoolId(moderatorId, blockedUserId, schoolId);
    }

    /**
     * 检查用户是否是版主
     * @param userId 用户ID
     * @return 是否是版主
     */
    @Transactional(readOnly = true)
    private boolean isModerator(Long userId) {
        User user = userService.getUserById(userId);
        return user != null && "MODERATOR".equals(user.getRole());
    }

    /**
     * 检查版主是否管理特定流派
     * @param moderatorId 版主用户ID
     * @param schoolId 流派ID
     * @return 是否管理该流派
     */
    @Transactional(readOnly = true)
    private boolean isModeratorOfSchool(Long moderatorId, Long schoolId) {
        User moderator = userService.getUserById(moderatorId);
        if (moderator == null) {
            return false;
        }
        
        // 检查版主是否被分配管理该流派
        return schoolId.equals(moderator.getAssignedSchoolId());
    }
}
