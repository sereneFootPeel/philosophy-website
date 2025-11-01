package com.philosophy.repository;

import com.philosophy.model.Like;
import com.philosophy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LikeRepository extends JpaRepository<Like, Long> {

    /**
     * 检查用户是否已经点赞了某个实体
     */
    boolean existsByUserAndEntityTypeAndEntityId(User user, Like.EntityType entityType, Long entityId);

    /**
     * 根据用户、实体类型和实体ID查找点赞记录
     */
    Optional<Like> findByUserAndEntityTypeAndEntityId(User user, Like.EntityType entityType, Long entityId);

    /**
     * 删除用户的点赞记录
     */
    void deleteByUserAndEntityTypeAndEntityId(User user, Like.EntityType entityType, Long entityId);

    /**
     * 统计某个实体的点赞数量
     */
    long countByEntityTypeAndEntityId(Like.EntityType entityType, Long entityId);

    /**
     * 获取用户的所有点赞记录
     */
    List<Like> findByUserOrderByCreatedAtDesc(User user);

    /**
     * 获取用户对特定类型实体的点赞记录
     */
    List<Like> findByUserAndEntityTypeOrderByCreatedAtDesc(User user, Like.EntityType entityType);

    /**
     * 获取用户点赞过的内容ID列表
     */
    @Query("SELECT l.entityId FROM Like l WHERE l.user = :user AND l.entityType = :entityType ORDER BY l.createdAt DESC")
    List<Long> findEntityIdsByUserAndEntityType(@Param("user") User user, @Param("entityType") Like.EntityType entityType);

    /**
     * 获取某个实体的点赞用户列表
     */
    @Query("SELECT l.user FROM Like l WHERE l.entityType = :entityType AND l.entityId = :entityId ORDER BY l.createdAt DESC")
    List<User> findUsersByEntityTypeAndEntityId(@Param("entityType") Like.EntityType entityType, @Param("entityId") Long entityId);

    /**
     * 统计用户的总点赞数
     */
    long countByUser(User user);

    /**
     * 获取最受欢迎的实体（按点赞数排序）
     */
    @Query(value = "SELECT l.entity_type, l.entity_id, COUNT(*) as likeCount FROM likes l GROUP BY l.entity_type, l.entity_id ORDER BY likeCount DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> findMostLikedEntities(@Param("limit") int limit);

    /**
     * 获取特定类型的最受欢迎实体
     */
    @Query(value = "SELECT l.entity_id, COUNT(*) as likeCount FROM likes l WHERE l.entity_type = :entityType GROUP BY l.entity_id ORDER BY likeCount DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> findMostLikedEntitiesByType(@Param("entityType") String entityType, @Param("limit") int limit);
    
    /**
     * 根据用户ID查找所有点赞记录
     */
    @Query("SELECT l FROM Like l WHERE l.user.id = :userId")
    List<Like> findByUserId(@Param("userId") Long userId);
}
