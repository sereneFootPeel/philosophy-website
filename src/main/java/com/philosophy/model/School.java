package com.philosophy.model;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "schools")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class School {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "学派名称不能为空")
    @Size(max = 100, message = "学派名称长度不能超过100个字符")
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    // 基本验证方法
    public boolean isValidName() {
        return name != null && !name.trim().isEmpty();
    }

    @Size(max = 10000, message = "描述长度不能超过10000个字符")
    @Column(columnDefinition = "TEXT")
    private String description;

    @Size(max = 10000, message = "英文描述长度不能超过10000个字符")
    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Size(max = 100, message = "英文名称长度不能超过100个字符")
    @Column(name = "name_en", length = 100)
    private String nameEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonBackReference
    private School parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("name ASC")
    @JsonManagedReference
    private List<School> children = new ArrayList<>();

    @ManyToMany(mappedBy = "schools", fetch = FetchType.LAZY)
    @JsonBackReference("school-philosophers")
    private List<Philosopher> philosophers = new ArrayList<>();

    @OneToMany(mappedBy = "school", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("content-school")
    private List<Content> contents = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "like_count")
    private Integer likeCount = 0;

    
    // 构造函数
    public School() {}
    
    public School(String name, String description, School parent) {
        this.name = name;
        this.description = description;
        this.parent = parent;
        this.children = new ArrayList<>();
        this.philosophers = new ArrayList<>();
    }
    
    // Getter和Setter方法
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public School getParent() {
        return parent;
    }
    
    public void setParent(School parent) {
        this.parent = parent;
    }
    
    public List<School> getChildren() {
        return children;
    }
    
    public void setChildren(List<School> children) {
        this.children = children;
    }
    
    public List<Philosopher> getPhilosophers() {
        return philosophers;
    }

    public void setPhilosophers(List<Philosopher> philosophers) {
        this.philosophers = philosophers;
    }

    public List<Content> getContents() {
        return contents;
    }

    public void setContents(List<Content> contents) {
        this.contents = contents;
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

    public void addContent(Content content) {
        if (!this.contents.contains(content)) {
            this.contents.add(content);
            content.setSchool(this);
        }
    }

    public void removeContent(Content content) {
        if (this.contents.contains(content)) {
            this.contents.remove(content);
            content.setSchool(null);
        }
    }
    

    
    // 辅助方法
    public void addChild(School child) {
        children.add(child);
        child.setParent(this);
    }

    public void removeChild(School child) {
        children.remove(child);
        child.setParent(null);
    }
    
    // 添加和移除哲学家的方法
    public void addPhilosopher(Philosopher philosopher) {
        if (!this.philosophers.contains(philosopher)) {
            this.philosophers.add(philosopher);
            philosopher.getSchools().add(this);
        }
    }
    
    public void removePhilosopher(Philosopher philosopher) {
        if (this.philosophers.contains(philosopher)) {
            this.philosophers.remove(philosopher);
            philosopher.getSchools().remove(this);
        }
    }
    
    // toString方法
    @Override
    public String toString() {
        return "School{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", children.size()=" + children.size() +
                ", philosophers.size()=" + philosophers.size() +
                ", contents.size()=" + contents.size() +
                '}';
    }
}
    