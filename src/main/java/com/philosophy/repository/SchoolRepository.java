package com.philosophy.repository;

import com.philosophy.model.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;


import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SchoolRepository extends JpaRepository<School, Long> {
    List<School> findByParentIsNullOrderByName();
    List<School> findByParentId(Long parentId);
    boolean existsByName(String name);

    Optional<School> findByName(String name);

    List<School> findByParentIsNull();
    
    // 搜索学派名称（忽略大小写）
    List<School> findByNameContainingIgnoreCase(String name);
    
    // 统计在指定时间之后创建的学派数量
    long countByCreatedAtAfter(LocalDateTime dateTime);
    
    // 查找最新的学派
    List<School> findTop10ByOrderByCreatedAtDesc();

    @Modifying
    @Transactional
    @Query("UPDATE School s SET s.likeCount = s.likeCount + :delta WHERE s.id = :id")
    void updateLikeCount(Long id, int delta);
}