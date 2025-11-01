package com.philosophy.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "email")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 1, max = 50, message = "用户名长度必须在1到50个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$", message = "用户名只能包含字母、数字、下划线和中文")
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6到100个字符之间")
    @Column(nullable = false, length = 255)
    private String password;

    @Size(max = 50, message = "名字长度不能超过50个字符")
    @Column(name = "first_name", length = 50)
    private String firstName;

    @Size(max = 50, message = "姓氏长度不能超过50个字符")
    @Column(name = "last_name", length = 50)
    private String lastName;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "role", nullable = false)
    private String role = "USER";

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "account_locked", nullable = false)
    private boolean accountLocked = false;

    @Column(name = "lock_time")
    private LocalDateTime lockTime;
    
    @Column(name = "lock_expire_time")
    private LocalDateTime lockExpireTime;

    /**
     * 主页隐私设置 - 控制整个用户主页的可见性
     * true: 所有内容和评论仅自己可见
     * false: 公开可见
     * 
     * 注意：当前实现使用此字段统一控制所有隐私设置
     * commentsPrivate和contentsPrivate字段预留用于未来细粒度控制
     */
    @Column(name = "profile_private", nullable = false)
    private boolean profilePrivate = false;

    /**
     * 评论隐私设置 - 预留字段，用于未来独立控制评论可见性
     * TODO: 当前未使用，由profilePrivate统一控制
     * 未来可实现：用户可以选择公开主页但隐藏评论
     */
    @Column(name = "comments_private", nullable = false)
    private boolean commentsPrivate = false;

    /**
     * 内容隐私设置 - 预留字段，用于未来独立控制内容可见性
     * TODO: 当前未使用，由profilePrivate统一控制
     * 未来可实现：用户可以选择公开主页但隐藏创建的内容
     */
    @Column(name = "contents_private", nullable = false)
    private boolean contentsPrivate = false;

    @Column(name = "admin_login_attempts", nullable = false)
    private int adminLoginAttempts = 0;

    @Column(name = "like_count")
    private Integer likeCount = 0;

    @Column(name = "assigned_school_id")
    private Long assignedSchoolId;

    @Size(max = 45, message = "IP地址长度不能超过45个字符")
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Size(max = 50, message = "设备类型长度不能超过50个字符")
    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Size(max = 500, message = "用户代理长度不能超过500个字符")
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Size(max = 500, message = "头像URL长度不能超过500个字符")
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Size(max = 10, message = "语言设置长度不能超过10个字符")
    @Column(name = "language", length = 10)
    private String language = "zh";

    @Size(max = 10, message = "主题设置长度不能超过10个字符")
    @Column(name = "theme", length = 10)
    private String theme = "midnight";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 构造函数
    public User() {
    }

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
    }

    // Getter和Setter方法
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public boolean isAccountLocked() {
        return accountLocked;
    }

    public void setAccountLocked(boolean accountLocked) {
        this.accountLocked = accountLocked;
    }

    public LocalDateTime getLockTime() {
        return lockTime;
    }

    public void setLockTime(LocalDateTime lockTime) {
        this.lockTime = lockTime;
    }

    public LocalDateTime getLockExpireTime() {
        return lockExpireTime;
    }

    public void setLockExpireTime(LocalDateTime lockExpireTime) {
        this.lockExpireTime = lockExpireTime;
    }

    public boolean isProfilePrivate() {
        return profilePrivate;
    }

    public void setProfilePrivate(boolean profilePrivate) {
        this.profilePrivate = profilePrivate;
    }

    public boolean isCommentsPrivate() {
        return commentsPrivate;
    }

    public void setCommentsPrivate(boolean commentsPrivate) {
        this.commentsPrivate = commentsPrivate;
    }

    public boolean isContentsPrivate() {
        return contentsPrivate;
    }

    public void setContentsPrivate(boolean contentsPrivate) {
        this.contentsPrivate = contentsPrivate;
    }

    public int getAdminLoginAttempts() {
        return adminLoginAttempts;
    }

    public void setAdminLoginAttempts(int adminLoginAttempts) {
        this.adminLoginAttempts = adminLoginAttempts;
    }

    public Integer getLikeCount() {
        return (likeCount == null) ? 0 : likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public Long getAssignedSchoolId() {
        return assignedSchoolId;
    }

    public void setAssignedSchoolId(Long assignedSchoolId) {
        this.assignedSchoolId = assignedSchoolId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    // UserDetails接口方法实现
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        // 如果账户被锁定，检查是否已过期
        if (accountLocked && lockExpireTime != null) {
            return LocalDateTime.now().isAfter(lockExpireTime);
        }
        return !accountLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }



    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                '}';
    }
}
    