package com.philosophy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 版主屏蔽用户实体类
 * 记录版主在特定流派中屏蔽用户的关系
 * 
 * @author James Gosling (Java之父风格)
 * @since 1.0
 */
@Entity
@Table(name = "moderator_blocks", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"moderator_id", "blocked_user_id", "school_id"}))
public class ModeratorBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "moderator_id", nullable = false)
    private User moderator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private User blockedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Size(max = 2000, message = "屏蔽原因长度不能超过2000个字符")
    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 构造函数
    public ModeratorBlock() {
        // 默认构造函数，符合Java Bean规范
    }

    public ModeratorBlock(User moderator, User blockedUser, School school) {
        this.moderator = moderator;
        this.blockedUser = blockedUser;
        this.school = school;
    }

    public ModeratorBlock(User moderator, User blockedUser, School school, String reason) {
        this.moderator = moderator;
        this.blockedUser = blockedUser;
        this.school = school;
        this.reason = reason;
    }

    // Getter和Setter方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getModerator() {
        return moderator;
    }

    public void setModerator(User moderator) {
        this.moderator = moderator;
    }

    public User getBlockedUser() {
        return blockedUser;
    }

    public void setBlockedUser(User blockedUser) {
        this.blockedUser = blockedUser;
    }

    public School getSchool() {
        return school;
    }

    public void setSchool(School school) {
        this.school = school;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "ModeratorBlock{" +
                "id=" + id +
                ", moderator=" + (moderator != null ? moderator.getUsername() : "null") +
                ", blockedUser=" + (blockedUser != null ? blockedUser.getUsername() : "null") +
                ", school=" + (school != null ? school.getName() : "null") +
                ", reason='" + reason + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModeratorBlock that = (ModeratorBlock) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
