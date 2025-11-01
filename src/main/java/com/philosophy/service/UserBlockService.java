package com.philosophy.service;

import com.philosophy.model.User;
import com.philosophy.model.UserBlock;
import com.philosophy.repository.UserBlockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 用户屏蔽服务类
 * 处理用户屏蔽相关的业务逻辑
 */
@Service
@Transactional
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserService userService;

    @Autowired
    public UserBlockService(UserBlockRepository userBlockRepository, UserService userService) {
        this.userBlockRepository = userBlockRepository;
        this.userService = userService;
    }

    /**
     * 屏蔽用户
     * @param blockerId 屏蔽者用户ID
     * @param blockedId 被屏蔽用户ID
     * @return 是否成功屏蔽
     */
    public boolean blockUser(Long blockerId, Long blockedId) {
        // 不能屏蔽自己
        if (blockerId.equals(blockedId)) {
            return false;
        }

        // 检查用户是否存在
        User blocker = userService.getUserById(blockerId);
        User blocked = userService.getUserById(blockedId);
        
        if (blocker == null || blocked == null) {
            return false;
        }

        // 检查是否已经屏蔽
        if (userBlockRepository.existsByBlockerAndBlocked(blocker, blocked)) {
            return false; // 已经屏蔽过了
        }

        // 创建屏蔽关系
        UserBlock userBlock = new UserBlock(blocker, blocked);
        userBlockRepository.save(userBlock);
        return true;
    }

    /**
     * 取消屏蔽用户
     * @param blockerId 屏蔽者用户ID
     * @param blockedId 被屏蔽用户ID
     * @return 是否成功取消屏蔽
     */
    public boolean unblockUser(Long blockerId, Long blockedId) {
        // 检查用户是否存在
        User blocker = userService.getUserById(blockerId);
        User blocked = userService.getUserById(blockedId);
        
        if (blocker == null || blocked == null) {
            return false;
        }

        // 删除屏蔽关系
        userBlockRepository.deleteByBlockerAndBlocked(blocker, blocked);
        return true;
    }

    /**
     * 检查用户是否屏蔽了另一个用户
     * @param blockerId 屏蔽者用户ID
     * @param blockedId 被屏蔽用户ID
     * @return 是否屏蔽
     */
    @Transactional(readOnly = true)
    public boolean isBlocked(Long blockerId, Long blockedId) {
        return userBlockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
    }

    /**
     * 检查用户是否屏蔽了另一个用户
     * @param blocker 屏蔽者用户
     * @param blocked 被屏蔽用户
     * @return 是否屏蔽
     */
    @Transactional(readOnly = true)
    public boolean isBlocked(User blocker, User blocked) {
        if (blocker == null || blocked == null) {
            return false;
        }
        return userBlockRepository.existsByBlockerAndBlocked(blocker, blocked);
    }

    /**
     * 获取用户屏蔽的所有用户列表
     * @param blockerId 屏蔽者用户ID
     * @return 屏蔽的用户列表
     */
    @Transactional(readOnly = true)
    public List<UserBlock> getBlockedUsers(Long blockerId) {
        User blocker = userService.getUserById(blockerId);
        if (blocker == null) {
            return List.of();
        }
        return userBlockRepository.findByBlockerOrderByCreatedAtDesc(blocker);
    }

    /**
     * 获取用户屏蔽的所有用户ID列表
     * @param blockerId 屏蔽者用户ID
     * @return 被屏蔽的用户ID列表
     */
    @Transactional(readOnly = true)
    public List<Long> getBlockedUserIds(Long blockerId) {
        return userBlockRepository.findBlockedUserIdsByBlockerId(blockerId);
    }

    /**
     * 获取被用户屏蔽的所有用户ID列表
     * @param blockedId 被屏蔽用户ID
     * @return 屏蔽该用户的用户ID列表
     */
    @Transactional(readOnly = true)
    public List<Long> getBlockerUserIds(Long blockedId) {
        return userBlockRepository.findBlockerUserIdsByBlockedId(blockedId);
    }

    /**
     * 统计用户屏蔽的用户数量
     * @param blockerId 屏蔽者用户ID
     * @return 屏蔽的用户数量
     */
    @Transactional(readOnly = true)
    public long getBlockedUserCount(Long blockerId) {
        User blocker = userService.getUserById(blockerId);
        if (blocker == null) {
            return 0;
        }
        return userBlockRepository.countByBlocker(blocker);
    }

    /**
     * 统计被用户屏蔽的数量
     * @param blockedId 被屏蔽用户ID
     * @return 被屏蔽的次数
     */
    @Transactional(readOnly = true)
    public long getBlockerCount(Long blockedId) {
        User blocked = userService.getUserById(blockedId);
        if (blocked == null) {
            return 0;
        }
        return userBlockRepository.countByBlocked(blocked);
    }

    /**
     * 获取屏蔽关系详情
     * @param blockerId 屏蔽者用户ID
     * @param blockedId 被屏蔽用户ID
     * @return 屏蔽关系详情
     */
    @Transactional(readOnly = true)
    public Optional<UserBlock> getBlockRelationship(Long blockerId, Long blockedId) {
        return userBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId);
    }
}
