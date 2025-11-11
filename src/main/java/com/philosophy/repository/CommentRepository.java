package com.philosophy.repository;

import com.philosophy.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    
    // 根据内容ID查找评论，按照创建时间降序排序
    List<Comment> findByContentIdOrderByCreatedAtDesc(Long contentId);
    
    // 获取指定内容的评论数量
    long countByContentId(Long contentId);
    
    // 根据内容ID查找评论并预加载关联的用户对象，避免LazyInitializationException
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.content.id = :contentId AND c.parent IS NULL ORDER BY c.createdAt DESC")
    List<Comment> findByContentIdWithUser(Long contentId);
    
    // 根据用户ID查找评论并预加载关联的内容对象
    @Query("SELECT c FROM Comment c JOIN FETCH c.content WHERE c.user.id = :userId ORDER BY c.createdAt DESC")
    List<Comment> findByUserIdWithContent(Long userId);
    
    // 查找指定评论的所有回复
    @Query("SELECT c FROM Comment c JOIN FETCH c.user WHERE c.parent.id = :parentId ORDER BY c.createdAt ASC")
    List<Comment> findRepliesByParentId(Long parentId);
    
    // 获取内容的顶级评论（无父评论的评论）
    List<Comment> findByContentIdAndParentIsNullOrderByCreatedAtDesc(Long contentId);
    
    // 获取指定用户的评论数量
    long countByUserId(Long userId);
    
    // 统计在指定时间之后创建的评论数量
    long countByCreatedAtAfter(LocalDateTime dateTime);
    
    // 查找最新的评论
    List<Comment> findTop10ByOrderByCreatedAtDesc();
    
    // 根据流派ID列表查找评论，按照创建时间降序排序
    @Query("SELECT c FROM Comment c JOIN FETCH c.user JOIN FETCH c.content WHERE c.content.school.id IN :schoolIds ORDER BY c.createdAt DESC")
    List<Comment> findBySchoolIdsOrderByCreatedAtDesc(List<Long> schoolIds);
    
    @Modifying
    @Transactional
    @Query("UPDATE Comment c SET c.likeCount = c.likeCount + :delta WHERE c.id = :id")
    void updateLikeCount(Long id, int delta);
    
    // 根据隐私设置者用户ID查找评论
    @Query("SELECT c FROM Comment c WHERE c.privacySetBy.id = :userId")
    List<Comment> findByPrivacySetByUserId(@Param("userId") Long userId);
    
    // 根据封禁者用户ID查找评论
    @Query("SELECT c FROM Comment c WHERE c.blockedBy.id = :userId")
    List<Comment> findByBlockedByUserId(@Param("userId") Long userId);
}