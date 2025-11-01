package com.philosophy.service;

import com.philosophy.model.User;
import com.philosophy.model.UserFollow;
import com.philosophy.repository.UserFollowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserFollowService {

    private final UserFollowRepository userFollowRepository;

    public UserFollowService(UserFollowRepository userFollowRepository) {
        this.userFollowRepository = userFollowRepository;
    }

    /**
     * 关注用户
     */
    @Transactional
    public boolean followUser(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            return false; // 不能关注自己
        }
        
        if (userFollowRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            return false; // 已经关注了
        }
        
        UserFollow userFollow = new UserFollow();
        User follower = new User();
        follower.setId(followerId);
        User following = new User();
        following.setId(followingId);
        
        userFollow.setFollower(follower);
        userFollow.setFollowing(following);
        
        userFollowRepository.save(userFollow);
        return true;
    }

    /**
     * 取消关注用户
     */
    @Transactional
    public boolean unfollowUser(Long followerId, Long followingId) {
        if (userFollowRepository.existsByFollowerIdAndFollowingId(followerId, followingId)) {
            userFollowRepository.deleteByFollowerIdAndFollowingId(followerId, followingId);
            return true;
        }
        return false;
    }

    /**
     * 检查是否关注了某个用户
     */
    @Transactional(readOnly = true)
    public boolean isFollowing(Long followerId, Long followingId) {
        return userFollowRepository.existsByFollowerIdAndFollowingId(followerId, followingId);
    }

    /**
     * 获取用户关注的所有用户ID列表
     */
    @Transactional(readOnly = true)
    public List<Long> getFollowingIds(Long followerId) {
        return userFollowRepository.findFollowingIdsByFollowerId(followerId);
    }

    /**
     * 获取关注某个用户的所有用户ID列表
     */
    @Transactional(readOnly = true)
    public List<Long> getFollowerIds(Long followingId) {
        return userFollowRepository.findFollowerIdsByFollowingId(followingId);
    }

    /**
     * 获取用户的关注数量
     */
    @Transactional(readOnly = true)
    public long getFollowingCount(Long userId) {
        return userFollowRepository.countByFollowerId(userId);
    }

    /**
     * 获取用户的粉丝数量
     */
    @Transactional(readOnly = true)
    public long getFollowerCount(Long userId) {
        return userFollowRepository.countByFollowingId(userId);
    }
}












