package com.philosophy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "philosophers")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Philosopher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "哲学家姓名不能为空")
    @Size(max = 100, message = "姓名长度不能超过100个字符")
    @Column(nullable = false, length = 100)
    private String name;

    // 基本验证方法
    public boolean isValidName() {
        return name != null && !name.trim().isEmpty();
    }

    @Size(max = 50, message = "时代长度不能超过50个字符")
    @Column(name = "era", length = 50)
    private String era;

    @Column(name = "birth_year")
    private Integer birthYear;

    @Size(max = 500, message = "图片URL长度不能超过500个字符")
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Size(max = 10000, message = "传记长度不能超过10000个字符")
    @Column(name = "biography", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "death_year")
    private Integer deathYear;

    @Size(max = 100, message = "国籍长度不能超过100个字符")
    @Column(name = "nationality", length = 100)
    private String nationality;

    @Size(max = 10000, message = "英文传记长度不能超过10000个字符")
    @Column(name = "bio_en", columnDefinition = "TEXT")
    private String bioEn;

    @Size(max = 100, message = "英文姓名长度不能超过100个字符")
    @Column(name = "name_en", length = 100)
    private String nameEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "philosopher_school",
            joinColumns = @JoinColumn(name = "philosopher_id"),
            inverseJoinColumns = @JoinColumn(name = "school_id")
    )
    @JsonManagedReference("school-philosophers")
    private List<School> schools = new ArrayList<>();

    @OneToMany(mappedBy = "philosopher", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH, CascadeType.DETACH})
    @JsonManagedReference("philosopher-content")
    private List<Content> contents = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "like_count")
    private Integer likeCount = 0;

    // 构造函数、getter和setter
    public Philosopher() {
    }

    public Philosopher(String name) {
        this.name = name;
    }

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

    public String getEra() {
        return era;
    }

    public void setEra(String era) {
        this.era = era;
    }

    public Integer getBirthYear() {
        return birthYear;
    }

    public void setBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Integer getDeathYear() {
        return deathYear;
    }

    public void setDeathYear(Integer deathYear) {
        this.deathYear = deathYear;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getBioEn() {
        return bioEn;
    }

    public void setBioEn(String bioEn) {
        this.bioEn = bioEn;
    }

    public String getNameEn() {
        return nameEn;
    }

    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<School> getSchools() {
        return schools;
    }

    public void setSchools(List<School> schools) {
        this.schools = schools;
    }

    public void addSchool(School school) {
        this.schools.add(school);
        school.getPhilosophers().add(this);
    }

    public void removeSchool(School school) {
        this.schools.remove(school);
        school.getPhilosophers().remove(this);
    }

    public List<Content> getContents() {
        return contents;
    }

    public void setContents(List<Content> contents) {
        this.contents = contents;
    }

    public void addContent(Content content) {
        this.contents.add(content);
        content.setPhilosopher(this);
    }

    public void removeContent(Content content) {
        this.contents.remove(content);
        content.setPhilosopher(null);
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

    @Override
    public String toString() {
        return "Philosopher{" +
                "id=" + id +
                ", name='" + name + "'" +
                ", era='" + era + "'" +
                ", birthYear=" + birthYear +
                "}";
    }
}
