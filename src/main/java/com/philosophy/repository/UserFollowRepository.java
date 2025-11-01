package com.philosophy.repository;

import com.philosophy.model.UserFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    /**
     * 检查用户是否关注了另一个用户
     */
    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    /**
     * 获取用户关注的所有用户ID列表
     */
    @Query("SELECT uf.following.id FROM UserFollow uf WHERE uf.follower.id = :followerId")
    List<Long> findFollowingIdsByFollowerId(@Param("followerId") Long followerId);

    /**
     * 获取关注某个用户的所有用户ID列表
     */
    @Query("SELECT uf.follower.id FROM UserFollow uf WHERE uf.following.id = :followingId")
    List<Long> findFollowerIdsByFollowingId(@Param("followingId") Long followingId);

    /**
     * 删除关注关系
     */
    void deleteByFollowerIdAndFollowingId(Long followerId, Long followingId);

    /**
     * 获取用户的关注数量
     */
    long countByFollowerId(Long followerId);

    /**
     * 获取用户的粉丝数量
     */
    long countByFollowingId(Long followingId);
    
    /**
     * 根据关注者ID或被关注者ID查找关注关系
     */
    List<UserFollow> findByFollowerIdOrFollowingId(Long followerId, Long followingId);
}












