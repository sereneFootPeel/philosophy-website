package com.philosophy.repository;

import com.philosophy.model.Philosopher;
import com.philosophy.model.School;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PhilosopherRepository extends JpaRepository<Philosopher, Long> {
    List<Philosopher> findBySchoolsContaining(School school);
    
    List<Philosopher> findByNameContainingIgnoreCase(String name);

    Optional<Philosopher> findByName(String name);
    
    @Query("SELECT p FROM Philosopher p JOIN p.schools s WHERE s.id = :schoolId")
    List<Philosopher> findBySchoolId(@Param("schoolId") Long schoolId);
    
    @Query("SELECT p FROM Philosopher p JOIN p.schools s WHERE s.id IN :schoolIds")
    List<Philosopher> findBySchoolIds(@Param("schoolIds") List<Long> schoolIds);
    
    @Query("SELECT DISTINCT p FROM Philosopher p JOIN p.schools s WHERE s.id IN :schoolIds")
    List<Philosopher> findBySchoolsIdIn(@Param("schoolIds") List<Long> schoolIds);
    
    // 统计在指定时间之后创建的哲学家数量
    long countByCreatedAtAfter(LocalDateTime dateTime);
    
    // 查找最新的哲学家
    List<Philosopher> findTop10ByOrderByCreatedAtDesc();

    @Query("SELECT COUNT(DISTINCT p) FROM Philosopher p JOIN p.schools s WHERE s.id IN :schoolIds")
    long countBySchoolIds(@Param("schoolIds") List<Long> schoolIds);

    @Modifying
    @Transactional
    @Query("UPDATE Philosopher p SET p.likeCount = p.likeCount + :delta WHERE p.id = :id")
    void updateLikeCount(Long id, int delta);
    
    @Query("SELECT p FROM Philosopher p WHERE p.user.id = :userId")
    List<Philosopher> findByUserId(@Param("userId") Long userId);
}