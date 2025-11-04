package com.philosophy.repository;

import com.philosophy.model.UserContentEdit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface UserContentEditRepository extends JpaRepository<UserContentEdit, Long> {
    
    // 根据用户ID查找所有编辑
    Page<UserContentEdit> findByUserIdOrderByUpdatedAtDesc(Long userId, Pageable pageable);

    // 根据原始内容ID查找所有编辑
    List<UserContentEdit> findByOriginalContentIdOrderByUpdatedAtDesc(Long originalContentId);
    
    // 统计用户编辑数量
    long countByUserId(Long userId);
    
    // 查找用户对特定内容的编辑
    @Query("SELECT uce FROM UserContentEdit uce WHERE uce.user.id = :userId AND uce.originalContent.id = :contentId ORDER BY uce.updatedAt DESC")
    List<UserContentEdit> findByUserIdAndOriginalContentId(@Param("userId") Long userId, @Param("contentId") Long contentId);

    // 查找最新的用户编辑（用于显示在内容卡片上）
    @Query("SELECT uce FROM UserContentEdit uce WHERE uce.originalContent.id = :contentId ORDER BY uce.updatedAt DESC")
    List<UserContentEdit> findLatestByOriginalContentId(@Param("contentId") Long contentId, Pageable pageable);

    // 删除指定原始内容ID的所有编辑记录
    @Modifying
    @Query("DELETE FROM UserContentEdit uce WHERE uce.originalContent.id = :originalContentId")
    void deleteByOriginalContentId(@Param("originalContentId") Long originalContentId);

    // 统计指定状态的编辑数量
    long countByStatus(UserContentEdit.EditStatus status);

    // 根据哲学家ID和流派ID查找用户编辑内容（包括待审核、已通过的内容）
    @Query("SELECT uce FROM UserContentEdit uce LEFT JOIN FETCH uce.user u LEFT JOIN FETCH uce.philosopher p WHERE uce.philosopher.id = :philosopherId AND uce.school.id = :schoolId AND uce.status IN ('PENDING', 'APPROVED') ORDER BY uce.updatedAt DESC")
    List<UserContentEdit> findByPhilosopherIdAndSchoolId(@Param("philosopherId") Long philosopherId, @Param("schoolId") Long schoolId);
    
    // 根据用户ID查找所有编辑记录
    @Query("SELECT uce FROM UserContentEdit uce WHERE uce.user.id = :userId")
    List<UserContentEdit> findByUserId(@Param("userId") Long userId);
    
    // 根据哲学家ID查找所有编辑记录
    @Query("SELECT uce FROM UserContentEdit uce WHERE uce.philosopher.id = :philosopherId")
    List<UserContentEdit> findByPhilosopherId(@Param("philosopherId") Long philosopherId);
    
    // 删除指定哲学家ID的所有编辑记录
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("DELETE FROM UserContentEdit uce WHERE uce.philosopher.id = :philosopherId")
    void deleteByPhilosopherId(@Param("philosopherId") Long philosopherId);
}
