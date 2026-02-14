package com.philosophy.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 用户心理/人格测试结果记录。
 * 支持仅本人可见或公开，用户可删除自己的记录。
 */
@Entity
@Table(name = "test_results", indexes = {
    @Index(name = "idx_test_results_user_id", columnList = "user_id"),
    @Index(name = "idx_test_results_user_created", columnList = "user_id, created_at")
})
public class TestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 测试类型：enneagram, mbti, bigfive, mmpi, values8 */
    @Column(name = "test_type", nullable = false, length = 50)
    private String testType;

    /** 简短结果摘要，用于卡片展示，如 "第3型 成就型"、"INTJ" */
    @Column(name = "result_summary", nullable = false, length = 200)
    private String resultSummary;

    /** 完整结果 JSON，用于详情页还原展示 */
    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    /** 是否公开：false=仅本人可见，true=公开 */
    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public TestResult() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTestType() {
        return testType;
    }

    public void setTestType(String testType) {
        this.testType = testType;
    }

    public String getResultSummary() {
        return resultSummary;
    }

    public void setResultSummary(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public boolean isPublic() {
        return isPublic;
    }

    /** Thymeleaf 友好：避免模板里使用保留字 public */
    public boolean isVisible() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
