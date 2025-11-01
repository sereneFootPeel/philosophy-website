package com.philosophy.service;

import com.philosophy.model.*;
import com.philosophy.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class LikeService {

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ContentRepository contentRepository;

    @Autowired
    private PhilosopherRepository philosopherRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    /**
     * 点赞或取消点赞
     * @param userId 用户ID
     * @param entityType 实体类型
     * @param entityId 实体ID
     * @return 是否点赞成功（true表示点赞，false表示取消点赞）
     */
    public boolean toggleLike(Long userId, Like.EntityType entityType, Long entityId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        // 检查实体是否存在
        if (!entityExists(entityType, entityId)) {
            throw new RuntimeException("实体不存在");
        }

        try {
            // 检查是否已经点赞
            Optional<Like> existingLike = likeRepository.findByUserAndEntityTypeAndEntityId(user, entityType, entityId);

            if (existingLike.isPresent()) {
                // 取消点赞
                likeRepository.delete(existingLike.get());
                updateEntityLikeCount(entityType, entityId, -1);
                return false;
            } else {
                // 添加点赞
                Like like = new Like(user, entityType, entityId);
                likeRepository.save(like);
                updateEntityLikeCount(entityType, entityId, 1);
                return true;
            }
        } catch (Exception e) {
            // 处理数据库约束冲突或其他异常
            if (e.getMessage() != null && e.getMessage().contains("Duplicate entry")) {
                // 如果是重复键错误，说明已经存在点赞记录，返回true（表示点赞状态）
                return true;
            }
            // 对于其他异常，重新抛出
            throw new RuntimeException("点赞操作失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查用户是否已经点赞了某个实体
     */
    public boolean isLikedByUser(Long userId, Like.EntityType entityType, Long entityId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return likeRepository.existsByUserAndEntityTypeAndEntityId(user, entityType, entityId);
    }

    /**
     * 获取实体的点赞数量
     */
    public long getLikeCount(Like.EntityType entityType, Long entityId) {
        return likeRepository.countByEntityTypeAndEntityId(entityType, entityId);
    }

    /**
     * 获取用户的所有点赞记录
     */
    public List<Like> getUserLikes(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return likeRepository.findByUserOrderByCreatedAtDesc(user);
    }

    /**
     * 获取用户对特定类型实体的点赞记录
     */
    public List<Like> getUserLikesByType(Long userId, Like.EntityType entityType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return likeRepository.findByUserAndEntityTypeOrderByCreatedAtDesc(user, entityType);
    }

    /**
     * 获取用户点赞过的内容
     */
    public List<Content> getUserLikedContents(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        List<Long> contentIds = likeRepository.findEntityIdsByUserAndEntityType(user, Like.EntityType.CONTENT);
        return contentRepository.findAllById(contentIds);
    }

    /**
     * 获取用户点赞过的哲学家
     */
    public List<Philosopher> getUserLikedPhilosophers(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        List<Long> philosopherIds = likeRepository.findEntityIdsByUserAndEntityType(user, Like.EntityType.PHILOSOPHER);
        return philosopherRepository.findAllById(philosopherIds);
    }

    /**
     * 获取用户点赞过的流派
     */
    public List<School> getUserLikedSchools(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        List<Long> schoolIds = likeRepository.findEntityIdsByUserAndEntityType(user, Like.EntityType.SCHOOL);
        return schoolRepository.findAllById(schoolIds);
    }

    /**
     * 获取用户点赞过的评论
     */
    public List<Comment> getUserLikedComments(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        List<Long> commentIds = likeRepository.findEntityIdsByUserAndEntityType(user, Like.EntityType.COMMENT);
        return commentRepository.findAllById(commentIds);
    }

    /**
     * 获取用户点赞过的用户
     */
    public List<User> getUserLikedUsers(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        List<Long> userIds = likeRepository.findEntityIdsByUserAndEntityType(user, Like.EntityType.USER);
        return userRepository.findAllById(userIds);
    }

    /**
     * 获取最受欢迎的内容
     */
    public List<Content> getMostLikedContents(int limit) {
        List<Long> contentIds = likeRepository.findMostLikedEntitiesByType(Like.EntityType.CONTENT.name(), limit)
                .stream()
                .map(result -> (Long) result[0])
                .toList();
        return contentRepository.findAllById(contentIds);
    }

    /**
     * 获取最受欢迎的哲学家
     */
    public List<Philosopher> getMostLikedPhilosophers(int limit) {
        List<Long> philosopherIds = likeRepository.findMostLikedEntitiesByType(Like.EntityType.PHILOSOPHER.name(), limit)
                .stream()
                .map(result -> (Long) result[0])
                .toList();
        return philosopherRepository.findAllById(philosopherIds);
    }

    /**
     * 获取最受欢迎的流派
     */
    public List<School> getMostLikedSchools(int limit) {
        List<Long> schoolIds = likeRepository.findMostLikedEntitiesByType(Like.EntityType.SCHOOL.name(), limit)
                .stream()
                .map(result -> (Long) result[0])
                .toList();
        return schoolRepository.findAllById(schoolIds);
    }

    /**
     * 检查实体是否存在
     */
    private boolean entityExists(Like.EntityType entityType, Long entityId) {
        return switch (entityType) {
            case COMMENT -> commentRepository.existsById(entityId);
            case CONTENT -> contentRepository.existsById(entityId);
            case PHILOSOPHER -> philosopherRepository.existsById(entityId);
            case SCHOOL -> schoolRepository.existsById(entityId);
            case USER -> userRepository.existsById(entityId);
        };
    }

    /**
     * 更新实体的点赞计数
     */
    private void updateEntityLikeCount(Like.EntityType entityType, Long entityId, int delta) {
        switch (entityType) {
            case COMMENT -> commentRepository.updateLikeCount(entityId, delta);
            case CONTENT -> contentRepository.updateLikeCount(entityId, delta);
            case PHILOSOPHER -> philosopherRepository.updateLikeCount(entityId, delta);
            case SCHOOL -> schoolRepository.updateLikeCount(entityId, delta);
            case USER -> userRepository.updateLikeCount(entityId, delta);
        }
    }
}
