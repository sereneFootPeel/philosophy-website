package com.philosophy.controller;

import com.philosophy.model.User;
import com.philosophy.model.Comment;
import com.philosophy.model.UserLoginInfo;
import com.philosophy.model.Content;
import com.philosophy.model.Like;
import com.philosophy.model.UserContentEdit;
import com.philosophy.service.UserService;
import com.philosophy.service.CommentService;
import com.philosophy.service.TranslationService;
import com.philosophy.service.LikeService;
import com.philosophy.service.ContentService;
import com.philosophy.service.UserContentEditService;
import com.philosophy.service.SchoolService;
import com.philosophy.service.UserBlockService;
import com.philosophy.model.School;
import com.philosophy.model.Philosopher;
import com.philosophy.util.UserInfoCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;

@Controller
public class UserProfileController {

    private final UserService userService;
    private final CommentService commentService;
    private final UserInfoCollector userInfoCollector;
    private final TranslationService translationService;
    private final LikeService likeService;
    private final ContentService contentService;
    private final UserContentEditService userContentEditService;
    private final SchoolService schoolService;
    private final UserBlockService userBlockService;

    private static final Logger logger = LoggerFactory.getLogger(UserProfileController.class);

    public UserProfileController(UserService userService, CommentService commentService,
                                UserInfoCollector userInfoCollector, TranslationService translationService,
                                LikeService likeService, ContentService contentService,
                                UserContentEditService userContentEditService, SchoolService schoolService,
                                UserBlockService userBlockService) {
        this.userService = userService;
        this.commentService = commentService;
        this.userInfoCollector = userInfoCollector;
        this.translationService = translationService;
        this.likeService = likeService;
        this.contentService = contentService;
        this.userContentEditService = userContentEditService;
        this.schoolService = schoolService;
        this.userBlockService = userBlockService;
    }

    // 管理员界面查看用户详情
    @GetMapping("/admin/users/view/{id}")
    public String viewUserDetails(@PathVariable Long id, Model model, Authentication authentication, HttpServletRequest request) {
        User user = userService.getUserById(id);
        if (user == null) {
            model.addAttribute("errorMessage", "用户不存在");
            return "error";
        }

        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 获取当前用户信息用于隐私过滤
        User currentUser = null;
        if (authentication != null && authentication.isAuthenticated()) {
            currentUser = userService.findByUsername(authentication.getName());
        }
        
        // 检查当前登录用户是否是管理员
        boolean isAdmin = authentication != null && authentication.isAuthenticated() &&
                authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);

        // 获取用户的所有评论，支持隐私过滤
        List<Comment> comments = commentService.findByUserIdWithPrivacyFilter(id, currentUser);
        long commentCount = commentService.countByUserId(id);
        
        // 如果是管理员，获取所有评论（包括软删除的）
        List<Comment> allComments = null;
        if (isAdmin) {
            allComments = commentService.findByUserIdIncludingDeleted(id);
        }

        // 获取用户的IP地址和设备信息
        String userIpAddress = userInfoCollector.getUserIpAddress(id);
        String userDevice = userInfoCollector.getUserDevice(id);
        
        // 获取用户的所有历史登录信息
        Set<String> allUserIpAddresses = userInfoCollector.getAllUserIpAddresses(id);
        Set<String> allUserDevices = userInfoCollector.getAllUserDevices(id);
        Set<String> allUserBrowsers = userInfoCollector.getAllUserBrowsers(id);
        Set<String> allUserOperatingSystems = userInfoCollector.getAllUserOperatingSystems(id);
        Set<String> allUserDeviceIds = userInfoCollector.getAllUserDeviceIds(id);
        List<UserLoginInfo> userLoginRecords = userInfoCollector.getUserLoginRecords(id);
        long loginCount = userInfoCollector.getUserLoginCount(id);

        // 获取用户的编辑内容
        List<UserContentEdit> userEdits = userContentEditService.getUserEdits(id, org.springframework.data.domain.PageRequest.of(0, 20)).getContent();
        long userEditCount = userContentEditService.getUserEditCount(id);

        // 获取版主创建的内容（如果是版主用户）
        List<Content> moderatorContents = null;
        long moderatorContentCount = 0;
        if (user.getRole() != null && user.getRole().equals("MODERATOR")) {
            moderatorContents = contentService.getContentsByUserId(id);
            moderatorContentCount = contentService.countContentsByUserId(id);
        }

        // 获取所有流派用于版主分配
        List<School> allSchools = schoolService.getAllSchools();

        model.addAttribute("user", user);
        model.addAttribute("comments", comments);
        model.addAttribute("commentCount", commentCount);
        model.addAttribute("allComments", allComments);
        model.addAttribute("userIpAddress", userIpAddress);
        model.addAttribute("userDevice", userDevice);
        model.addAttribute("allUserIpAddresses", allUserIpAddresses);
        model.addAttribute("allUserDevices", allUserDevices);
        model.addAttribute("allUserBrowsers", allUserBrowsers);
        model.addAttribute("allUserOperatingSystems", allUserOperatingSystems);
        model.addAttribute("allUserDeviceIds", allUserDeviceIds);
        model.addAttribute("userLoginRecords", userLoginRecords);
        model.addAttribute("loginCount", loginCount);
        model.addAttribute("userEdits", userEdits);
        model.addAttribute("userEditCount", userEditCount);
        model.addAttribute("moderatorContents", moderatorContents);
        model.addAttribute("moderatorContentCount", moderatorContentCount);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("allSchools", allSchools);
        model.addAttribute("userInfoCollector", userInfoCollector);
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("userService", userService);
        model.addAttribute("schoolService", schoolService);

        return "admin/users/view";
    }

    // 当前用户主页（无需ID参数）
    @GetMapping("/user/profile")
    public String viewCurrentUserProfile(Model model, Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User currentUser = userService.findByUsername(authentication.getName());
        if (currentUser == null) {
            model.addAttribute("errorMessage", "用户不存在");
            return "error";
        }

        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 检查当前登录用户是否是管理员
        boolean isAdmin = authentication != null && authentication.isAuthenticated() &&
                authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);

        // 获取用户的所有评论，支持隐私过滤
        List<Comment> comments = commentService.findByUserIdWithPrivacyFilter(currentUser.getId(), currentUser);
        long commentCount = commentService.countByUserId(currentUser.getId());
        
        // 如果是管理员，获取所有评论（包括软删除的）
        List<Comment> allComments = null;
        if (isAdmin) {
            allComments = commentService.findByUserIdIncludingDeleted(currentUser.getId());
        }

        // 获取用户的IP地址和设备信息
        String userIpAddress = userInfoCollector.getUserIpAddress(currentUser.getId());
        String userDevice = userInfoCollector.getUserDevice(currentUser.getId());
        
        // 获取用户的所有历史登录信息
        Set<String> allUserIpAddresses = userInfoCollector.getAllUserIpAddresses(currentUser.getId());
        Set<String> allUserDevices = userInfoCollector.getAllUserDevices(currentUser.getId());
        Set<String> allUserBrowsers = userInfoCollector.getAllUserBrowsers(currentUser.getId());
        Set<String> allUserOperatingSystems = userInfoCollector.getAllUserOperatingSystems(currentUser.getId());
        Set<String> allUserDeviceIds = userInfoCollector.getAllUserDeviceIds(currentUser.getId());
        List<UserLoginInfo> userLoginRecords = userInfoCollector.getUserLoginRecords(currentUser.getId());
        long loginCount = userInfoCollector.getUserLoginCount(currentUser.getId());

        // 获取用户编辑的content数据
        List<UserContentEdit> userEdits = userContentEditService.getUserEdits(currentUser.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        long userEditCount = userContentEditService.getUserEditCount(currentUser.getId());

        // 获取用户创建的内容（普通用户和版主都显示），并进行隐私过滤
        List<Content> userCreatedContents = contentService.getContentsByUserIdWithPrivacyFilter(currentUser.getId(), currentUser);
        long userContentCount = userCreatedContents.size();

        // 获取版主创建的内容（如果是版主用户）
        List<Content> moderatorContents = null;
        long moderatorContentCount = 0;
        if (currentUser.getRole() != null && currentUser.getRole().equals("MODERATOR")) {
            moderatorContents = userCreatedContents;
            moderatorContentCount = userContentCount;
        }


        model.addAttribute("user", currentUser);
        model.addAttribute("comments", comments);
        model.addAttribute("commentCount", commentCount);
        model.addAttribute("allComments", allComments);
        model.addAttribute("userIpAddress", userIpAddress);
        model.addAttribute("userDevice", userDevice);
        model.addAttribute("allUserIpAddresses", allUserIpAddresses);
        model.addAttribute("allUserDevices", allUserDevices);
        model.addAttribute("allUserBrowsers", allUserBrowsers);
        model.addAttribute("allUserOperatingSystems", allUserOperatingSystems);
        model.addAttribute("allUserDeviceIds", allUserDeviceIds);
        model.addAttribute("userLoginRecords", userLoginRecords);
        model.addAttribute("loginCount", loginCount);
        model.addAttribute("userEdits", userEdits);
        model.addAttribute("userEditCount", userEditCount);
        model.addAttribute("userCreatedContents", userCreatedContents);
        model.addAttribute("userContentCount", userContentCount);
        model.addAttribute("moderatorContents", moderatorContents);
        model.addAttribute("moderatorContentCount", moderatorContentCount);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isCurrentUser", true); // 标记为当前用户
        model.addAttribute("isBlocked", false); // 当前用户不能屏蔽自己
        model.addAttribute("userInfoCollector", userInfoCollector);
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        model.addAttribute("isAuthenticated", isAuthenticated);
        // 注意：savedTheme现在由GlobalControllerAdvice全局提供

        return "user/profile";
    }

    // 评论界面点击用户头像查看用户主页（公共视图）
    @GetMapping("/user/profile/{id}")
    public String viewUserProfile(@PathVariable Long id, Model model, Authentication authentication, HttpServletRequest request) {
        User user = userService.getUserById(id);
        if (user == null) {
            model.addAttribute("errorMessage", "用户不存在");
            return "error";
        }

        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 获取当前用户信息用于隐私过滤
        User currentUser = null;
        if (authentication != null && authentication.isAuthenticated()) {
            currentUser = userService.findByUsername(authentication.getName());
        }
        
        // 获取用户的所有评论，支持隐私过滤
        List<Comment> comments = commentService.findByUserIdWithPrivacyFilter(id, currentUser);
        long commentCount = commentService.countByUserId(id);

        // 获取用户编辑的content数据
        List<UserContentEdit> userEdits = userContentEditService.getUserEdits(id, org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        long userEditCount = userContentEditService.getUserEditCount(id);

        // 获取用户创建的内容（普通用户和版主都显示），并进行隐私过滤
        List<Content> userCreatedContents = contentService.getContentsByUserIdWithPrivacyFilter(id, currentUser);
        long userContentCount = userCreatedContents.size();

        // 检查当前登录用户是否是评论所有者或管理员
        boolean canDeleteComments = false;
        boolean isCurrentUser = false;
        boolean isAdmin = false;
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        boolean isBlocked = false;

        if (authentication != null && authentication.isAuthenticated()) {
            isCurrentUser = currentUser != null && currentUser.getId().equals(id);
            isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            canDeleteComments = isCurrentUser || isAdmin;
            
            // 检查当前用户是否屏蔽了该用户
            if (!isCurrentUser && currentUser != null) {
                isBlocked = userBlockService.isBlocked(currentUser.getId(), id);
            }
        }

        model.addAttribute("user", user);
        model.addAttribute("comments", comments);
        model.addAttribute("commentCount", commentCount);
        model.addAttribute("userEdits", userEdits);
        model.addAttribute("userEditCount", userEditCount);
        model.addAttribute("userCreatedContents", userCreatedContents);
        model.addAttribute("userContentCount", userContentCount);
        model.addAttribute("isCurrentUser", isCurrentUser);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("canDeleteComments", canDeleteComments);
        model.addAttribute("isBlocked", isBlocked);
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "user/profile";
    }

    // 删除评论功能
    @GetMapping("/user/comments/delete/{commentId}")
    public String deleteUserComment(@PathVariable Long commentId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        Comment comment = commentService.getCommentById(commentId);
        if (comment != null) {
            User currentUser = userService.findByUsername(authentication.getName());
            Long userId = comment.getUser().getId();

            // 检查用户是否有权限删除评论（评论所有者或管理员）
            if (comment.getUser().getId().equals(currentUser.getId()) ||
                    authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                // 使用软删除而不是硬删除
                commentService.softDeleteComment(commentId, currentUser);
            }

            return "redirect:/user/profile/" + userId;
        }

        return "redirect:/";
    }

    // 更新用户主页隐私设置
    @PostMapping("/user/profile/{id}/privacy")
    @ResponseBody
    public Map<String, Object> updateProfilePrivacy(@PathVariable Long id,
                                     @RequestParam(defaultValue = "false") boolean isPrivate,
                                     Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        if (authentication == null || !authentication.isAuthenticated()) {
            response.put("success", false);
            response.put("message", "请先登录");
            return response;
        }

        User currentUser = userService.findByUsername(authentication.getName());
        if (currentUser == null || currentUser.getId() == null) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return response;
        }
        
        if (id == null) {
            response.put("success", false);
            response.put("message", "无效的用户ID");
            return response;
        }

        // 检查权限：只能修改自己的设置或者是管理员
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!java.util.Objects.equals(currentUser.getId(), id) && !isAdmin) {
            response.put("success", false);
            response.put("message", "无权限修改此设置");
            return response;
        }

        try {
            User user = userService.getUserById(id);
            if (user == null) {
                response.put("success", false);
                response.put("message", "用户不存在");
                return response;
            }
            
            // 调用Service层的事务方法来确保原子性
            boolean success = updateUserPrivacyWithTransaction(user, isPrivate, currentUser);
            
            if (success) {
                // 记录审计日志
                logger.info("User privacy updated: userId={}, isPrivate={}, changedBy={}", 
                    id, isPrivate, currentUser.getUsername());
                
                response.put("success", true);
                response.put("message", "隐私设置已更新");
            } else {
                response.put("success", false);
                response.put("message", "更新失败");
            }
            return response;
            
        } catch (org.springframework.dao.DataAccessException e) {
            logger.error("Database error updating profile privacy for user " + id, e);
            response.put("success", false);
            response.put("message", "数据库错误，请稍后重试");
            return response;
        } catch (IllegalStateException e) {
            logger.warn("Invalid state when updating privacy for user " + id, e);
            response.put("success", false);
            response.put("message", "当前状态不允许修改");
            return response;
        } catch (Exception e) {
            logger.error("Unexpected error updating profile privacy for user " + id, e);
            response.put("success", false);
            response.put("message", "系统错误，请联系管理员");
            return response;
        }
    }

    /**
     * 带事务的隐私更新方法，确保原子性
     * @param user 用户对象
     * @param isPrivate 是否私密
     * @param currentUser 当前操作用户
     * @return 是否成功
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    protected boolean updateUserPrivacyWithTransaction(User user, boolean isPrivate, User currentUser) {
        try {
            // 1. 更新用户隐私设置
            user.setProfilePrivate(isPrivate);
            userService.updateUser(user);

            // 2. 批量设置所有内容的隐私状态
            contentService.setAllContentsPrivacyForUser(user.getId(), isPrivate, currentUser);
            
            // 3. 批量设置所有评论的隐私状态
            commentService.setAllCommentsPrivacyForUser(user.getId(), isPrivate, currentUser);
            
            return true;
        } catch (Exception e) {
            logger.error("Transaction failed while updating privacy for user " + user.getId(), e);
            throw e; // 重新抛出异常以触发事务回滚
        }
    }

    // 更新用户主题设置 - 添加by James Gosling的审查建议
    @PostMapping("/user/profile/{id}/theme")
    @ResponseBody
    public Map<String, Object> updateUserTheme(@PathVariable Long id, 
                                                @RequestParam String theme,
                                                Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            response.put("success", false);
            response.put("message", "请先登录");
            return response;
        }

        User currentUser = userService.findByUsername(authentication.getName());
        if (currentUser == null) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return response;
        }

        // 检查权限：只能修改自己的设置
        if (!currentUser.getId().equals(id)) {
            response.put("success", false);
            response.put("message", "无权限修改此设置");
            return response;
        }

        // 验证主题值
        String[] validThemes = {"light", "forest", "dark", "sunset", "ocean", "purple", "sakura", "nordic", "emerald", "midnight", "carbon", "obsidian"};
        boolean isValidTheme = false;
        for (String validTheme : validThemes) {
            if (validTheme.equals(theme)) {
                isValidTheme = true;
                break;
            }
        }
        
        if (!isValidTheme) {
            response.put("success", false);
            response.put("message", "无效的主题设置");
            return response;
        }

        try {
            User user = userService.getUserById(id);
            if (user != null) {
                user.setTheme(theme);
                userService.updateUser(user);

                logger.info("User {} updated theme to: {}", user.getUsername(), theme);
                
                response.put("success", true);
                response.put("message", "已切换主题");
                response.put("theme", theme);
                return response;
            } else {
                response.put("success", false);
                response.put("message", "用户不存在");
                return response;
            }
        } catch (Exception e) {
            logger.error("Error updating theme for user " + id, e);
            response.put("success", false);
            response.put("message", "更新失败，请稍后再试");
            return response;
        }
    }

    // 我的点赞页面
    @GetMapping("/likes/my-likes")
    public String myLikes(Model model, Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User currentUser = userService.findByUsername(authentication.getName());
        if (currentUser == null) {
            model.addAttribute("errorMessage", "用户不存在");
            return "error";
        }

        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 获取用户点赞的不同类型内容
        List<Content> likedContents = likeService.getUserLikedContents(currentUser.getId());
        List<User> likedUsers = likeService.getUserLikedUsers(currentUser.getId());
        
        // 检查所有点赞记录
        try {
            List<Like> allLikes = likeService.getUserLikes(currentUser.getId());
            if (allLikes != null && !allLikes.isEmpty()) {
                for (Like like : allLikes) {
                }
            }
        } catch (Exception e) {
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("likedContents", likedContents);
        model.addAttribute("likedUsers", likedUsers);
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        model.addAttribute("isAuthenticated", true);

        return "user/my-likes";
    }

    // 我的评论页面
    @GetMapping("/comments/my-comments")
    public String myComments(Model model, Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User currentUser = userService.findByUsername(authentication.getName());
        if (currentUser == null) {
            model.addAttribute("errorMessage", "用户不存在");
            return "error";
        }

        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 获取用户的所有评论（不包括已删除的）
        List<Comment> comments = commentService.findByUserIdWithPrivacyFilter(currentUser.getId(), currentUser);
        long commentCount = commentService.countByUserId(currentUser.getId());

        model.addAttribute("user", currentUser);
        model.addAttribute("comments", comments);
        model.addAttribute("commentCount", commentCount);
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        model.addAttribute("isAuthenticated", true);

        return "user/my-comments";
    }

    // 屏蔽用户
    @PostMapping("/user/block/{blockedUserId}")
    @ResponseBody
    public String blockUser(@PathVariable Long blockedUserId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "{\"success\": false, \"message\": \"请先登录\"}";
        }

        User currentUser = userService.findByUsername(authentication.getName());
        if (currentUser == null) {
            return "{\"success\": false, \"message\": \"用户不存在\"}";
        }

        // 不能屏蔽自己
        if (currentUser.getId().equals(blockedUserId)) {
            return "{\"success\": false, \"message\": \"不能屏蔽自己\"}";
        }

        try {
            boolean success = userBlockService.blockUser(currentUser.getId(), blockedUserId);
            if (success) {
                // 屏蔽用户时自动取消对该用户的点赞
                if (likeService.isLikedByUser(currentUser.getId(), Like.EntityType.USER, blockedUserId)) {
                    likeService.toggleLike(currentUser.getId(), Like.EntityType.USER, blockedUserId);
                }
                return "{\"success\": true, \"message\": \"已屏蔽该用户\"}";
            } else {
                return "{\"success\": false, \"message\": \"屏蔽失败，可能已经屏蔽过该用户\"}";
            }
        } catch (Exception e) {
            return "{\"success\": false, \"message\": \"屏蔽失败，请稍后再试\"}";
        }
    }

    // 取消屏蔽用户
    @PostMapping("/user/unblock/{blockedUserId}")
    @ResponseBody
    public String unblockUser(@PathVariable Long blockedUserId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "{\"success\": false, \"message\": \"请先登录\"}";
        }

        User currentUser = userService.findByUsername(authentication.getName());
        if (currentUser == null) {
            return "{\"success\": false, \"message\": \"用户不存在\"}";
        }

        try {
            boolean success = userBlockService.unblockUser(currentUser.getId(), blockedUserId);
            if (success) {
                return "{\"success\": true, \"message\": \"已取消屏蔽该用户\"}";
            } else {
                return "{\"success\": false, \"message\": \"取消屏蔽失败，可能没有屏蔽过该用户\"}";
            }
        } catch (Exception e) {
            return "{\"success\": false, \"message\": \"取消屏蔽失败，请稍后再试\"}";
        }
    }

    // 检查屏蔽状态
    @GetMapping("/user/block-status/{blockedUserId}")
    @ResponseBody
    public String getBlockStatus(@PathVariable Long blockedUserId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "{\"success\": false, \"message\": \"请先登录\"}";
        }

        User currentUser = userService.findByUsername(authentication.getName());
        if (currentUser == null) {
            return "{\"success\": false, \"message\": \"用户不存在\"}";
        }

        try {
            boolean isBlocked = userBlockService.isBlocked(currentUser.getId(), blockedUserId);
            return "{\"success\": true, \"isBlocked\": " + isBlocked + "}";
        } catch (Exception e) {
            return "{\"success\": false, \"message\": \"获取屏蔽状态失败\"}";
        }
    }
}