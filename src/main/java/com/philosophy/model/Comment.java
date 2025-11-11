package com.philosophy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.ArrayList;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private Content content;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;
    
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> replies = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank(message = "评论内容不能为空")
    @Size(min = 1, max = 5000, message = "评论内容长度必须在1到5000个字符之间")
    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "privacy_set_by")
    private User privacySetBy;

    @Column(name = "privacy_set_at")
    private LocalDateTime privacySetAt;

    @Column(name = "like_count")
    private Integer likeCount = 0;

    @Column(name = "status", nullable = false)
    private int status = 0;

    @Column(name = "is_blocked", nullable = false)
    private boolean isBlocked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by")
    private User blockedBy;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    // 构造函数
    public Comment() {
    }

    public Comment(Content content, User user, String body) {
        this.content = content;
        this.user = user;
        this.body = body;
    }

    // Getter和Setter方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
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

    public Comment getParent() {
        return parent;
    }

    public void setParent(Comment parent) {
        this.parent = parent;
    }

    public List<Comment> getReplies() {
        return replies;
    }

    public void setReplies(List<Comment> replies) {
        this.replies = replies;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }

    public User getPrivacySetBy() {
        return privacySetBy;
    }

    public void setPrivacySetBy(User privacySetBy) {
        this.privacySetBy = privacySetBy;
    }

    public LocalDateTime getPrivacySetAt() {
        return privacySetAt;
    }

    public void setPrivacySetAt(LocalDateTime privacySetAt) {
        this.privacySetAt = privacySetAt;
    }

    public Integer getLikeCount() {
        return (likeCount == null) ? 0 : likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean isBlocked) {
        this.isBlocked = isBlocked;
    }

    public User getBlockedBy() {
        return blockedBy;
    }

    public void setBlockedBy(User blockedBy) {
        this.blockedBy = blockedBy;
    }

    public LocalDateTime getBlockedAt() {
        return blockedAt;
    }

    public void setBlockedAt(LocalDateTime blockedAt) {
        this.blockedAt = blockedAt;
    }

    @Override
    public String toString() {
        return "Comment{" +
                "id=" + id +
                ", content_id=" + (content != null ? content.getId() : null) +
                ", user_id=" + (user != null ? user.getId() : null) +
                ", parent_id=" + (parent != null ? parent.getId() : null) +
                '}';
    }
}