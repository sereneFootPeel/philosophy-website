package com.philosophy.controller;

import com.philosophy.model.Comment;
import com.philosophy.model.Content;
import com.philosophy.model.User;
import com.philosophy.service.CommentService;
import com.philosophy.service.ContentService;
import com.philosophy.service.UserService;
import com.philosophy.service.SchoolService;
import com.philosophy.service.TranslationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import com.philosophy.util.LanguageUtil;
import java.util.List;
import java.util.Collections;

@Controller
@RequestMapping("/comments")
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);
    
    private final CommentService commentService;
    private final UserService userService;
    private final ContentService contentService;
    private final TranslationService translationService;
    private final SchoolService schoolService;
    private final LanguageUtil languageUtil;

    public CommentController(CommentService commentService, UserService userService, ContentService contentService, TranslationService translationService, SchoolService schoolService, LanguageUtil languageUtil) {
        this.commentService = commentService;
        this.userService = userService;
        this.contentService = contentService;
        this.translationService = translationService;
        this.schoolService = schoolService;
        this.languageUtil = languageUtil;
    }

    // 查看指定内容的评论
    @GetMapping("/content/{contentId}")
    public String viewComments(@PathVariable Long contentId, Model model, Authentication authentication, HttpServletRequest request) {
        return viewCommentsByPath(contentId, model, authentication, request);
    }
    
    // 兼容旧路径的查看评论
    @GetMapping("/view/{contentId}")
    public String viewCommentsByPath(@PathVariable Long contentId, Model model, Authentication authentication, HttpServletRequest request) {
        try {
            // 获取当前语言设置（根据IP自动判断默认语言）
            String language = languageUtil.getLanguage(request);
            
            model.addAttribute("contentId", contentId);
            model.addAttribute("language", language);
            model.addAttribute("translationService", translationService);
            
            // 获取当前用户信息用于隐私过滤
            User currentUser = null;
            if (authentication != null && authentication.isAuthenticated() && 
                !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                currentUser = userService.findByUsername(authentication.getName());
            }
            
            // 获取顶级评论（无父评论的评论），支持隐私过滤
            logger.debug("Getting comments for contentId: {}, currentUser: {}", contentId, 
                (currentUser != null ? currentUser.getUsername() : "anonymous"));
            List<Comment> topLevelComments = commentService.findByContentIdWithPrivacyFilter(contentId, currentUser);
            logger.debug("Found {} top-level comments", topLevelComments.size());
            
            // 为每个顶级评论加载回复，支持隐私过滤
            for (Comment comment : topLevelComments) {
                List<Comment> replies = commentService.findRepliesByParentIdWithPrivacyFilter(comment.getId(), currentUser);
                comment.setReplies(replies);
            }
            
            model.addAttribute("comments", topLevelComments);
            
            // 获取内容对象并添加到模型中
            Content content = contentService.getContentById(contentId);
            if (content == null) {
                // 内容不存在时，返回404错误页面
                model.addAttribute("statusCode", 404);
                model.addAttribute("errorMessage", "您访问的内容不存在或已被删除");
                return "error";
            }
            model.addAttribute("content", content);
            
            // 检查用户是否已登录
            if (authentication != null && authentication.isAuthenticated() && 
                !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                model.addAttribute("isAuthenticated", true);
                model.addAttribute("currentUsername", authentication.getName());
                // 检查是否是管理员
                boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                model.addAttribute("isAdmin", isAdmin);
                boolean isModerator = currentUser != null && "MODERATOR".equals(currentUser.getRole());
                model.addAttribute("isModerator", isModerator);
                Long assignedSchoolId = currentUser != null ? currentUser.getAssignedSchoolId() : null;
                if (isModerator && assignedSchoolId != null) {
                    model.addAttribute("moderatorSchoolIds", schoolService.getModeratorManageableSchoolIds(assignedSchoolId));
                } else {
                    model.addAttribute("moderatorSchoolIds", Collections.emptyList());
                }
            } else {
                model.addAttribute("isAuthenticated", false);
                model.addAttribute("isAdmin", false);
                model.addAttribute("isModerator", false);
                model.addAttribute("moderatorSchoolIds", Collections.emptyList());
            }
        } catch (Exception e) {
            // 记录错误日志
            logger.error("Error loading comments for content {}: {}", contentId, e.getMessage(), e);
            
            // 返回500错误页面
            model.addAttribute("statusCode", 500);
            model.addAttribute("errorMessage", "加载评论时发生错误，请稍后再试");
            return "error";
        }
        
        return "comments/view";
    }

    // 提交评论
    @PostMapping("/content/{contentId}")
    public String submitComment(@PathVariable Long contentId, 
                               @RequestParam String body, 
                               @RequestParam(required = false) Long parentId,
                               Authentication authentication,
                               Model model) {
        if (authentication == null || !(authentication.isAuthenticated()) || 
            authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return "redirect:/login?redirect=/comments/content/" + contentId;
        }
        
        // 输入验证
        if (body == null || body.trim().isEmpty()) {
            model.addAttribute("error", "评论内容不能为空");
            return "redirect:/comments/content/" + contentId + "?error=empty";
        }
        
        if (body.length() > 5000) {
            model.addAttribute("error", "评论内容不能超过5000个字符");
            return "redirect:/comments/content/" + contentId + "?error=toolong";
        }
        
        User currentUser = userService.findByUsername(authentication.getName());
        
        if (parentId != null) {
            // 回复评论
            commentService.saveReply(parentId, currentUser, body);
        } else {
            // 顶级评论
            commentService.saveComment(contentId, currentUser, body);
        }
        
        return "redirect:/comments/content/" + contentId;
    }

    // 删除评论
    @PostMapping("/delete/{commentId}")
    public String deleteComment(@PathVariable Long commentId, Authentication authentication) {
        if (authentication == null || !(authentication.isAuthenticated()) || 
            authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return "redirect:/login";
        }
        
        Comment comment = commentService.getCommentById(commentId);
        Long redirectContentId = null;
        if (comment != null) {
            redirectContentId = comment.getContent() != null ? comment.getContent().getId() : null;
            User currentUser = userService.findByUsername(authentication.getName());
            
            // 检查用户是否有权限删除评论（评论所有者或管理员）
            boolean isOwner = comment.getUser().getId().equals(currentUser.getId());
            boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isModeratorWithAccess = false;
            if (!isOwner && !isAdmin && currentUser != null && "MODERATOR".equals(currentUser.getRole())) {
                Long assignedSchoolId = currentUser.getAssignedSchoolId();
                Long commentSchoolId = comment.getContent() != null && comment.getContent().getSchool() != null
                        ? comment.getContent().getSchool().getId()
                        : null;
                if (assignedSchoolId != null && commentSchoolId != null) {
                    isModeratorWithAccess = schoolService.canModeratorManageSchool(assignedSchoolId, commentSchoolId);
                }
            }
            if (isOwner || isAdmin || isModeratorWithAccess) {
                commentService.deleteComment(commentId);
            }
        }
        
        return redirectContentId != null
            ? "redirect:/comments/content/" + redirectContentId
            : "redirect:/";
    }

    // 屏蔽评论
    @PostMapping("/block/{commentId}")
    public String blockComment(@PathVariable Long commentId, Authentication authentication) {
        if (authentication == null || !(authentication.isAuthenticated()) || 
            authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return "redirect:/login";
        }
        
        Comment comment = commentService.getCommentById(commentId);
        if (comment != null) {
            User currentUser = userService.findByUsername(authentication.getName());
            
            // 检查用户是否有权限屏蔽评论（版主或管理员）
            boolean isModerator = currentUser.getRole() != null && 
                (currentUser.getRole().equals("MODERATOR") || currentUser.getRole().equals("ADMIN"));
            boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            
            if (isModerator || isAdmin) {
                commentService.blockComment(commentId, currentUser);
            }
        }
        
        return "redirect:/moderator/comments";
    }

    // 取消屏蔽评论
    @PostMapping("/unblock/{commentId}")
    public String unblockComment(@PathVariable Long commentId, Authentication authentication) {
        if (authentication == null || !(authentication.isAuthenticated()) || 
            authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return "redirect:/login";
        }
        
        Comment comment = commentService.getCommentById(commentId);
        if (comment != null) {
            User currentUser = userService.findByUsername(authentication.getName());
            
            // 检查用户是否有权限取消屏蔽评论（版主或管理员）
            boolean isModerator = currentUser.getRole() != null && 
                (currentUser.getRole().equals("MODERATOR") || currentUser.getRole().equals("ADMIN"));
            boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            
            if (isModerator || isAdmin) {
                commentService.unblockComment(commentId);
            }
        }
        
        return "redirect:/moderator/comments";
    }

    // 设置评论隐私状态
    @PostMapping("/privacy/{commentId}")
    @ResponseBody
    public String setCommentPrivacy(@PathVariable Long commentId, 
                                   @RequestParam boolean isPrivate,
                                   Authentication authentication) {
        if (authentication == null || !(authentication.isAuthenticated()) || 
            authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return "{\"success\": false, \"message\": \"请先登录\"}";
        }
        
        try {
            User currentUser = userService.findByUsername(authentication.getName());
            commentService.setCommentPrivacy(commentId, isPrivate, currentUser);
            
            String message = isPrivate ? "评论已设置为仅自己和管理员可见" : "评论已设置为公开";
            return "{\"success\": true, \"message\": \"" + message + "\"}";
        } catch (SecurityException e) {
            return "{\"success\": false, \"message\": \"" + e.getMessage() + "\"}";
        } catch (IllegalArgumentException e) {
            return "{\"success\": false, \"message\": \"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            logger.error("Error setting comment privacy for comment {}: {}", commentId, e.getMessage(), e);
            return "{\"success\": false, \"message\": \"设置失败，请稍后再试\"}";
        }
    }

    // 批量设置用户所有评论的隐私状态
    @PostMapping("/privacy/batch/{userId}")
    @ResponseBody
    public String setAllCommentsPrivacy(@PathVariable Long userId, 
                                       @RequestParam boolean isPrivate,
                                       Authentication authentication) {
        if (authentication == null || !(authentication.isAuthenticated()) || 
            authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return "{\"success\": false, \"message\": \"请先登录\"}";
        }
        
        try {
            User currentUser = userService.findByUsername(authentication.getName());
            int updatedCount = commentService.setAllCommentsPrivacyForUser(userId, isPrivate, currentUser);
            
            String message = isPrivate ? 
                "已将所有评论设置为仅自己和管理员可见（共更新 " + updatedCount + " 条评论）" : 
                "已将所有评论设置为公开（共更新 " + updatedCount + " 条评论）";
            return "{\"success\": true, \"message\": \"" + message + "\", \"updatedCount\": " + updatedCount + "}";
        } catch (SecurityException e) {
            return "{\"success\": false, \"message\": \"" + e.getMessage() + "\"}";
        } catch (IllegalArgumentException e) {
            return "{\"success\": false, \"message\": \"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            logger.error("Error setting all comments privacy for user {}: {}", userId, e.getMessage(), e);
            return "{\"success\": false, \"message\": \"设置失败，请稍后再试\"}";
        }
    }

    // 设置评论状态（管理员功能）
    @PostMapping("/status/{commentId}")
    @ResponseBody
    public String setCommentStatus(@PathVariable Long commentId, 
                                  @RequestParam int status,
                                  Authentication authentication) {
        if (authentication == null || !(authentication.isAuthenticated()) || 
            authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return "{\"success\": false, \"message\": \"请先登录\"}";
        }
        
        try {
            User currentUser = userService.findByUsername(authentication.getName());
            commentService.setCommentStatus(commentId, status, currentUser);
            
            String message = status == 1 ? "评论已设置为仅管理员和作者可见" : "评论已设置为公开";
            return "{\"success\": true, \"message\": \"" + message + "\"}";
        } catch (SecurityException e) {
            return "{\"success\": false, \"message\": \"" + e.getMessage() + "\"}";
        } catch (IllegalArgumentException e) {
            return "{\"success\": false, \"message\": \"" + e.getMessage() + "\"}";
        } catch (Exception e) {
            logger.error("Error setting comment status for comment {}: {}", commentId, e.getMessage(), e);
            return "{\"success\": false, \"message\": \"设置失败，请稍后再试\"}";
        }
    }
}