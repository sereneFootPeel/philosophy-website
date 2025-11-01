package com.philosophy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_content_edits")
public class UserContentEdit {

    public enum EditStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_content_id", nullable = true)
    private Content originalContent;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "philosopher_id", nullable = false)
    private Philosopher philosopher;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;
    
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200个字符")
    @Column(nullable = false, length = 200)
    private String title;
    
    @NotBlank(message = "内容不能为空")
    @Size(max = 50000, message = "内容长度不能超过50000个字符")
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Size(max = 50000, message = "英文内容长度不能超过50000个字符")
    @Column(columnDefinition = "TEXT")
    private String contentEn;

    @Size(max = 2000, message = "管理员备注长度不能超过2000个字符")
    @Column(columnDefinition = "TEXT")
    private String adminNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EditStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    
    // 构造函数
    public UserContentEdit() {}
    
    public UserContentEdit(User user, Content originalContent, Philosopher philosopher, School school, String title, String content) {
        this.user = user;
        this.originalContent = originalContent;
        this.philosopher = philosopher;
        this.school = school;
        this.title = title;
        this.content = content;
        this.status = EditStatus.PENDING; // Default status
    }

    public UserContentEdit(User user, Content originalContent, Philosopher philosopher, School school, String title, String content, String contentEn) {
        this.user = user;
        this.originalContent = originalContent;
        this.philosopher = philosopher;
        this.school = school;
        this.title = title;
        this.content = content;
        this.contentEn = contentEn;
        this.status = EditStatus.PENDING; // Default status
    }
    
    // Getters and Setters
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
    
    public Content getOriginalContent() {
        return originalContent;
    }
    
    public void setOriginalContent(Content originalContent) {
        this.originalContent = originalContent;
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
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
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

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }
    
    
    
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public EditStatus getStatus() {
        return status;
    }

    public void setStatus(EditStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
