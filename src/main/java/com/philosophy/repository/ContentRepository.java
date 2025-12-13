package com.philosophy.repository;

import java.util.Optional;

import com.philosophy.model.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContentRepository extends JpaRepository<Content, Long> {

    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.philosopher WHERE c.id = :id")
    Optional<Content> findByIdWithPhilosopher(Long id);
    List<Content> findByPhilosopherId(Long philosopherId);
    
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.philosopher LEFT JOIN FETCH c.school s LEFT JOIN FETCH s.parent WHERE c.philosopher.id = :philosopherId")
    List<Content> findByPhilosopherIdWithSchool(Long philosopherId);
    
    List<Content> findByPhilosopherSchoolsId(Long schoolId);

    
    // 自定义查询方法，使用JOIN FETCH预先加载关联的philosopher对象
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.philosopher")
    List<Content> findAllWithPhilosopher();
    
    // 获取指定流派的内容，只显示该流派下哲学家的content
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.philosopher p WHERE c.school.id = :schoolId AND p.id IN (SELECT ph.id FROM Philosopher ph JOIN ph.schools s WHERE s.id = :schoolId)")
    List<Content> findAllBySchoolIdWithPhilosopher(Long schoolId);
    
    // 根据多个流派ID查找内容，只显示这些流派下哲学家的content
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.philosopher p WHERE c.school.id IN :schoolIds AND p.id IN (SELECT ph.id FROM Philosopher ph JOIN ph.schools s WHERE s.id IN :schoolIds)")
    List<Content> findContentsBySchoolIds(List<Long> schoolIds);
    
    // 直接根据流派ID查找内容（只根据content的school_id筛选）
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.philosopher WHERE c.school.id = :schoolId")
    List<Content> findBySchoolIdDirect(Long schoolId);
    
    // 直接根据多个流派ID查找内容（只根据content的school_id筛选）
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.philosopher WHERE c.school.id IN :schoolIds")
    List<Content> findBySchoolIdsDirect(List<Long> schoolIds);
    
    // 统计在指定时间之后创建的内容数量
    long countByCreatedAtAfter(LocalDateTime dateTime);
    
    // 查找最新的内容
    List<Content> findTop10ByOrderByCreatedAtDesc();
    
    // 搜索内容（根据内容文本搜索，忽略大小写）
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.philosopher p LEFT JOIN FETCH c.school s LEFT JOIN FETCH s.parent WHERE LOWER(c.content) LIKE LOWER(CONCAT('%', :content, '%'))")
    List<Content> findByContentContainingIgnoreCase(@Param("content") String content);

    @Query("SELECT COUNT(DISTINCT c) FROM Content c WHERE c.school.id IN :schoolIds")
    long countBySchoolIds(List<Long> schoolIds);

    // 获取指定哲学家和流派的内容，按用户角色优先级排序
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.user u WHERE c.philosopher.id = :philosopherId AND c.school.id = :schoolId ORDER BY " +
           "CASE WHEN u.role = 'ADMIN' THEN 1 " +
           "WHEN u.role = 'MODERATOR' THEN 2 " +
           "ELSE 3 END, " +
           "c.likeCount DESC")
    List<Content> findByPhilosopherAndSchoolWithPriority(Long philosopherId, Long schoolId);

    // 获取指定哲学家和流派的内容，按用户角色优先级排序（包含用户编辑的内容）
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.user u LEFT JOIN FETCH c.philosopher p WHERE c.philosopher.id = :philosopherId AND c.school.id = :schoolId ORDER BY " +
           "CASE WHEN u.role = 'ADMIN' THEN 1 " +
           "WHEN u.role = 'MODERATOR' THEN 2 " +
           "ELSE 3 END, " +
           "c.likeCount DESC")
    List<Content> findByPhilosopherAndSchoolWithUserPriority(Long philosopherId, Long schoolId);

    // 根据用户ID查找内容
    List<Content> findByUserId(Long userId);

    // 根据用户ID查找内容（分页）
    org.springframework.data.domain.Page<Content> findByUserId(Long userId, org.springframework.data.domain.Pageable pageable);
    
    // 根据用户ID统计内容数量
    long countByUserId(Long userId);
    
    // 获取指定哲学家和流派的所有内容（包括用户编辑的内容）
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.user u LEFT JOIN FETCH c.philosopher p WHERE c.philosopher.id = :philosopherId AND c.school.id = :schoolId ORDER BY c.createdAt DESC")
    List<Content> findByPhilosopherAndSchoolAll(Long philosopherId, Long schoolId);
    
    // 获取指定流派的内容，只包含管理员和版主编辑的内容
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.user u LEFT JOIN FETCH c.philosopher p WHERE c.school.id IN :schoolIds AND (u.role = 'ADMIN' OR u.role = 'MODERATOR') ORDER BY " +
           "CASE WHEN u.role = 'ADMIN' THEN 1 " +
           "WHEN u.role = 'MODERATOR' THEN 2 " +
           "ELSE 3 END, " +
           "c.likeCount DESC")
    List<Content> findBySchoolIdsAdminModeratorOnly(List<Long> schoolIds);
    
    // 获取指定流派的内容（分页），只包含管理员和版主编辑的内容
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.user u LEFT JOIN FETCH c.philosopher p WHERE c.school.id IN :schoolIds AND (u.role = 'ADMIN' OR u.role = 'MODERATOR') ORDER BY " +
           "CASE WHEN u.role = 'ADMIN' THEN 1 " +
           "WHEN u.role = 'MODERATOR' THEN 2 " +
           "ELSE 3 END, " +
           "c.likeCount DESC")
    org.springframework.data.domain.Page<Content> findBySchoolIdsAdminModeratorOnlyPaged(List<Long> schoolIds, org.springframework.data.domain.Pageable pageable);
    
    // 获取指定流派的内容，包含管理员、版主编辑的内容以及用户点赞的作者的内容
    @Query("SELECT DISTINCT c FROM Content c LEFT JOIN FETCH c.user u LEFT JOIN FETCH c.philosopher p " +
           "WHERE c.school.id IN :schoolIds AND " +
           "(u.role = 'ADMIN' OR u.role = 'MODERATOR' OR " +
           "u.id IN (SELECT DISTINCT l.entityId FROM Like l WHERE l.entityType = 'USER' AND l.user.id = :currentUserId)) " +
           "ORDER BY " +
           "CASE WHEN u.role = 'ADMIN' THEN 1 " +
           "WHEN u.role = 'MODERATOR' THEN 2 " +
           "ELSE 3 END, " +
           "c.likeCount DESC")
    List<Content> findBySchoolIdsWithLikedAuthors(List<Long> schoolIds, Long currentUserId);

    // 获取指定流派的内容，包含管理员、版主编辑的内容以及用户关注的作者的内容（用于 /schools/filter/{id} 端点）
    @Query("SELECT DISTINCT c FROM Content c LEFT JOIN FETCH c.user u LEFT JOIN FETCH c.philosopher p " +
           "WHERE c.school.id IN :schoolIds AND " +
           "(u.role = 'ADMIN' OR u.role = 'MODERATOR' OR " +
           "u.id IN (SELECT DISTINCT uf.following.id FROM UserFollow uf WHERE uf.follower.id = :currentUserId)) " +
           "ORDER BY " +
           "CASE WHEN u.role = 'ADMIN' THEN 1 " +
           "WHEN u.role = 'MODERATOR' THEN 2 " +
           "ELSE 3 END, " +
           "c.likeCount DESC")
    List<Content> findBySchoolIdsWithFollowedAuthors(@Param("schoolIds") List<Long> schoolIds, @Param("currentUserId") Long currentUserId);

    @Modifying
    @Transactional
    @Query("UPDATE Content c SET c.likeCount = c.likeCount + :delta WHERE c.id = :id")
    void updateLikeCount(Long id, int delta);

    @Modifying
    @Transactional
    @Query("DELETE FROM Content c WHERE c.id = :id")
    void deleteByIdWithoutVersion(Long id);

    @Modifying
    @Transactional
    @Query("DELETE FROM Content")
    void deleteAllContents();
    
    // 根据锁定者用户ID查找内容
    @Query("SELECT c FROM Content c WHERE c.lockedByUser.id = :userId")
    List<Content> findByLockedByUserId(@Param("userId") Long userId);
    
    // 根据隐私设置者用户ID查找内容
    @Query("SELECT c FROM Content c WHERE c.privacySetBy.id = :userId")
    List<Content> findByPrivacySetByUserId(@Param("userId") Long userId);
    
    // 根据封禁者用户ID查找内容
    @Query("SELECT c FROM Content c WHERE c.blockedBy.id = :userId")
    List<Content> findByBlockedByUserId(@Param("userId") Long userId);
    
    // 获取所有内容并按优先级排序（分页）- 用于内容总览页面
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.user u LEFT JOIN FETCH c.philosopher p LEFT JOIN FETCH c.school s ORDER BY " +
           "CASE WHEN u.role = 'ADMIN' THEN 1 " +
           "WHEN u.role = 'MODERATOR' THEN 2 " +
           "ELSE 3 END, " +
           "c.likeCount DESC")
    org.springframework.data.domain.Page<Content> findAllWithPriorityPaged(org.springframework.data.domain.Pageable pageable);
    
    // 获取指定哲学家的内容并按优先级排序（分页）- 用于哲学家页面
    @Query("SELECT DISTINCT c FROM Content c LEFT JOIN FETCH c.user u LEFT JOIN FETCH c.philosopher p LEFT JOIN FETCH c.school s LEFT JOIN FETCH s.parent WHERE c.philosopher.id = :philosopherId ORDER BY " +
           "CASE WHEN u.role = 'ADMIN' THEN 1 " +
           "WHEN u.role = 'MODERATOR' THEN 2 " +
           "ELSE 3 END, " +
           "c.likeCount DESC")
    org.springframework.data.domain.Page<Content> findByPhilosopherIdWithPriorityPaged(Long philosopherId, org.springframework.data.domain.Pageable pageable);
    
    // 获取所有内容（用于随机名句）
    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.philosopher p LEFT JOIN FETCH c.user u")
    List<Content> findAllContentsForQuotes();

    @Query("SELECT c FROM Content c LEFT JOIN FETCH c.philosopher p LEFT JOIN FETCH c.school s LEFT JOIN FETCH s.parent WHERE LOWER(c.content) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.contentEn) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(c.title) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Content> searchByContentOrContentEnOrTitle(@Param("query") String query);
}
