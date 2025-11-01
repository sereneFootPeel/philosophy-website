package com.philosophy.model;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;
import java.time.LocalDateTime;

@Entity
@Table(name = "contents")
@DynamicInsert
@DynamicUpdate
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Content {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "philosopher_id", nullable = true)
    private Philosopher philosopher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    @JsonBackReference("content-school")
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locked_by_user_id")
    private User lockedByUser;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "history_pinned", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean historyPinned = false;

    @Size(max = 50000, message = "内容长度不能超过50000个字符")
    @Column(columnDefinition = "MEDIUMTEXT", name = "content")
    private String content;

    @Size(max = 50000, message = "英文内容长度不能超过50000个字符")
    @Column(name = "content_en", columnDefinition = "MEDIUMTEXT")
    private String contentEn;

    @Size(max = 200, message = "标题长度不能超过200个字符")
    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "order_index")
    private Integer orderIndex;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "like_count")
    private Integer likeCount = 0;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "privacy_set_by")
    private User privacySetBy;

    @Column(name = "privacy_set_at")
    private LocalDateTime privacySetAt;

    @Column(name = "status", nullable = false)
    private int status = 0;

    @Column(name = "is_blocked", nullable = false)
    private boolean isBlocked = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by")
    private User blockedBy;

    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;

    @Version
    private Long version;

    // 构造函数
    public Content() {
    }

    public Content(String content) {
        this.content = content;
    }

    // Getter和Setter方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Philosopher getPhilosopher() {
        return philosopher;
    }

    public void setPhilosopher(Philosopher philosopher) {
        this.philosopher = philosopher;
    }

    public School getSchool() {
        return school;
    }

    public void setSchool(School school) {
        this.school = school;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentEn() {
        return contentEn;
    }

    public void setContentEn(String contentEn) {
        this.contentEn = contentEn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
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

    public Integer getLikeCount() {
        return (likeCount == null) ? 0 : likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getLockedByUser() {
        return lockedByUser;
    }

    public void setLockedByUser(User lockedByUser) {
        this.lockedByUser = lockedByUser;
    }

    public LocalDateTime getLockedAt() {
        return lockedAt;
    }

    public void setLockedAt(LocalDateTime lockedAt) {
        this.lockedAt = lockedAt;
    }

    public boolean isLocked() {
        return lockedByUser != null && lockedAt != null;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(LocalDateTime lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public boolean isHistoryPinned() {
        return historyPinned;
    }

    public void setHistoryPinned(boolean historyPinned) {
        this.historyPinned = historyPinned;
    }

    @Override
    public String toString() {
        return "Content{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", philosopher_id=" + (philosopher != null ? philosopher.getId() : null) +
                ", school_id=" + (school != null ? school.getId() : null) +
                ", school_name=" + (school != null ? school.getName() : null) +
                ", locked=" + isLocked() +
                ", locked_by=" + (lockedByUser != null ? lockedByUser.getUsername() : null) +
                ", is_private=" + isPrivate +
                ", status=" + status +
                ", is_blocked=" + isBlocked +
                '}';
    }
}
    