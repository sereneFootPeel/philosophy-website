package com.philosophy.repository;

import com.philosophy.model.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestResultRepository extends JpaRepository<TestResult, Long> {

    List<TestResult> findByUserIdOrderByCreatedAtDesc(Long userId);

    /** 查询某用户可见的测试记录：本人看全部，他人只看 isPublic=true */
    List<TestResult> findByUserIdAndIsPublicTrueOrderByCreatedAtDesc(Long userId);

    void deleteByIdAndUserId(Long id, Long userId);
}
