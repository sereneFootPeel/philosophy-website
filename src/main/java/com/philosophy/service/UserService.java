package com.philosophy.service;

import com.philosophy.model.User;
import com.philosophy.model.UserLoginInfo;
import com.philosophy.model.Comment;
import com.philosophy.model.Content;
import com.philosophy.model.UserFollow;
import com.philosophy.model.Like;
import com.philosophy.model.UserContentEdit;
import com.philosophy.model.UserBlock;
import com.philosophy.model.ModeratorBlock;
import com.philosophy.model.Philosopher;
import com.philosophy.repository.UserRepository;
import com.philosophy.repository.UserLoginInfoRepository;
import com.philosophy.repository.CommentRepository;
import com.philosophy.repository.ContentRepository;
import com.philosophy.repository.UserFollowRepository;
import com.philosophy.repository.LikeRepository;
import com.philosophy.repository.UserContentEditRepository;
import com.philosophy.repository.UserBlockRepository;
import com.philosophy.repository.ModeratorBlockRepository;
import com.philosophy.repository.PhilosopherRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.ArrayList;

@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserLoginInfoRepository userLoginInfoRepository;
    private final CommentRepository commentRepository;
    private final ContentRepository contentRepository;
    private final UserFollowRepository userFollowRepository;
    private final LikeRepository likeRepository;
    private final UserContentEditRepository userContentEditRepository;
    private final UserBlockRepository userBlockRepository;
    private final ModeratorBlockRepository moderatorBlockRepository;
    private final PhilosopherRepository philosopherRepository;
    
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, 
                      UserLoginInfoRepository userLoginInfoRepository, CommentRepository commentRepository,
                      ContentRepository contentRepository, UserFollowRepository userFollowRepository,
                      LikeRepository likeRepository, UserContentEditRepository userContentEditRepository,
                      UserBlockRepository userBlockRepository, ModeratorBlockRepository moderatorBlockRepository,
                      PhilosopherRepository philosopherRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userLoginInfoRepository = userLoginInfoRepository;
        this.commentRepository = commentRepository;
        this.contentRepository = contentRepository;
        this.userFollowRepository = userFollowRepository;
        this.likeRepository = likeRepository;
        this.userContentEditRepository = userContentEditRepository;
        this.userBlockRepository = userBlockRepository;
        this.moderatorBlockRepository = moderatorBlockRepository;
        this.philosopherRepository = philosopherRepository;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }

    @Transactional
    public User registerNewUser(User user) {
        // 加密密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    @Transactional
    public void updatePassword(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + userId));
        user.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    @Transactional
    public void createAdminAccount() {
        // 检查是否已有管理员账户
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@example.com");
            admin.setPassword(passwordEncoder.encode("000000000000000000000000"));
            admin.setRole("ADMIN"); // 改为String类型
            admin.setEnabled(true); // 添加enabled字段
            userRepository.save(admin);
        }
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }
    
    @Transactional(readOnly = true)
    public Long countUsers() {
        return userRepository.count();
    }
    
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    @Transactional
    public User saveUser(User user) {
        return userRepository.save(user);
    }
    
    @Transactional
    public User updateUser(User user) {
        return userRepository.save(user);
    }
    
    @Transactional
    public void resetFailedAttempts(String username) {
        // 使用直接更新，避免触发实体验证
        userRepository.resetFailedAttemptsByUsername(username);
    }
    
    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
        
        // 1. 删除用户的登录记录（外键约束）
        List<UserLoginInfo> loginRecords = userLoginInfoRepository.findByUserIdOrderByLoginTimeDesc(id);
        if (!loginRecords.isEmpty()) {
            userLoginInfoRepository.deleteAll(loginRecords);
        }
        
        // 2. 处理Comment表中的外键约束
        // 需要处理以下外键字段：user_id, privacy_set_by, blocked_by
        
        // 2.1 删除用户创建的所有评论
        List<Comment> userComments = commentRepository.findByUserIdWithContent(id);
        if (!userComments.isEmpty()) {
            commentRepository.deleteAll(userComments);
        }
        
        // 2.2 处理用户设置隐私的评论 - 重置隐私设置者
        List<Comment> privacySetComments = commentRepository.findByPrivacySetByUserId(id);
        for (Comment comment : privacySetComments) {
            comment.setPrivacySetBy(null);
            comment.setPrivacySetAt(null);
            commentRepository.save(comment);
        }
        
        // 2.3 处理用户封禁的评论 - 解除封禁
        List<Comment> blockedComments = commentRepository.findByBlockedByUserId(id);
        for (Comment comment : blockedComments) {
            comment.setBlockedBy(null);
            comment.setBlockedAt(null);
            comment.setBlocked(false);
            commentRepository.save(comment);
        }
        
        // 3. 处理Content表中的外键约束
        // 需要处理以下外键字段：user_id, locked_by_user_id, privacy_set_by, blocked_by
        List<Content> userContents = contentRepository.findByUserId(id);
        if (!userContents.isEmpty()) {
            // 删除用户创建的所有内容
            contentRepository.deleteAll(userContents);
        }
        
        // 4. 处理用户锁定的内容 - 解除锁定
        List<Content> lockedContents = contentRepository.findByLockedByUserId(id);
        for (Content content : lockedContents) {
            content.setLockedByUser(null);
            content.setLockedAt(null);
            content.setLockedUntil(null);
            contentRepository.save(content);
        }
        
        // 5. 处理用户设置隐私的内容 - 重置为默认用户或系统
        List<Content> privacySetContents = contentRepository.findByPrivacySetByUserId(id);
        for (Content content : privacySetContents) {
            content.setPrivacySetBy(null);
            content.setPrivacySetAt(null);
            contentRepository.save(content);
        }
        
        // 6. 处理用户封禁的内容 - 解除封禁
        List<Content> blockedContents = contentRepository.findByBlockedByUserId(id);
        for (Content content : blockedContents) {
            content.setBlockedBy(null);
            content.setBlockedAt(null);
            content.setBlocked(false);
            contentRepository.save(content);
        }
        
        // 7. 删除用户关注关系
        List<UserFollow> userFollows = userFollowRepository.findByFollowerIdOrFollowingId(id, id);
        if (!userFollows.isEmpty()) {
            userFollowRepository.deleteAll(userFollows);
        }
        
        // 8. 删除用户的点赞记录
        List<Like> userLikes = likeRepository.findByUserId(id);
        if (!userLikes.isEmpty()) {
            likeRepository.deleteAll(userLikes);
        }
        
        // 9. 删除用户的内容编辑记录
        List<UserContentEdit> userContentEdits = userContentEditRepository.findByUserId(id);
        if (!userContentEdits.isEmpty()) {
            userContentEditRepository.deleteAll(userContentEdits);
        }
        
        // 10. 删除用户的封禁记录
        List<UserBlock> userBlocks = userBlockRepository.findByUserIdOrBlockedUserId(id, id);
        if (!userBlocks.isEmpty()) {
            userBlockRepository.deleteAll(userBlocks);
        }
        
        // 11. 删除用户的ModeratorBlock记录
        List<ModeratorBlock> moderatorBlocks = moderatorBlockRepository.findByUserIdOrModeratorId(id, id);
        if (!moderatorBlocks.isEmpty()) {
            moderatorBlockRepository.deleteAll(moderatorBlocks);
        }
        
        // 12. 处理Philosopher表中的外键约束 - 重置user_id为null
        List<Philosopher> userPhilosophers = philosopherRepository.findByUserId(id);
        for (Philosopher philosopher : userPhilosophers) {
            philosopher.setUser(null);
            philosopherRepository.save(philosopher);
        }
        
        // 13. 最后删除用户记录
        userRepository.delete(user);
    }
    
    @Transactional(readOnly = true)
    public boolean isAdmin(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        return user != null && "ADMIN".equals(user.getRole());
    }
    
    @Transactional(readOnly = true)
    public User getCurrentUser(org.springframework.security.core.Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        
        String username = authentication.getName();
        try {
            return findByUsername(username);
        } catch (UsernameNotFoundException e) {
            return null;
        }
    }
    
    @Transactional(readOnly = true)
    public boolean isModerator(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        return user != null && "MODERATOR".equals(user.getRole());
    }
    
    /**
     * 搜索用户（支持关键词）
     */
    @Transactional(readOnly = true)
    public List<User> searchUsers(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return userRepository.searchByUsernameOrName(query.trim());
    }
    
    @Transactional(readOnly = true)
    public List<User> getModerators() {
        return userRepository.findByRole("MODERATOR");
    }
    
    @Transactional
    public void assignSchoolToModerator(Long userId, Long schoolId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && "MODERATOR".equals(user.getRole())) {
            user.setAssignedSchoolId(schoolId);
            userRepository.save(user);
        }
    }
    
    @Transactional
    public void removeSchoolFromModerator(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && "MODERATOR".equals(user.getRole())) {
            user.setAssignedSchoolId(null);
            userRepository.save(user);
        }
    }
}
    