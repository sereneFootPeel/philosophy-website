package com.philosophy.controller;

import com.philosophy.model.*;
import com.philosophy.service.LikeService;
import com.philosophy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/likes")
public class LikeController {

    private static final Logger logger = LoggerFactory.getLogger(LikeController.class);

    @Autowired
    private LikeService likeService;
    
    @Autowired
    private UserService userService;

    /**
     * 点赞或取消点赞
     */
    @PostMapping("/toggle")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleLike(
            @RequestParam String entityType,
            @RequestParam Long entityId,
            Authentication authentication) {
        
        logger.info("Received like toggle request for entityType: {}, entityId: {}", entityType, entityId);
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.debug("=== LikeController.toggleLike 开始处理 ===");
            logger.debug("entityType: {}, entityId: {}, authentication: {}", entityType, entityId, 
                (authentication != null ? authentication.getName() : "null"));

            if (authentication == null || !authentication.isAuthenticated()) {
                logger.warn("认证失败：用户未登录");
                response.put("success", false);
                response.put("message", "请先登录");
                return ResponseEntity.badRequest().body(response);
            }

            String username = authentication.getName();
            logger.info("Like toggle attempt by user: {}", username);
            User user = userService.findByUsername(username);
            if (user == null) {
                logger.error("用户查找失败：用户 '{}' 不存在", username);
                response.put("success", false);
                response.put("message", "用户不存在");
                return ResponseEntity.badRequest().body(response);
            }
            logger.debug("用户ID: {}", user.getId());

            Like.EntityType type;
            try {
                type = Like.EntityType.valueOf(entityType.toUpperCase());
                logger.debug("实体类型: {}", type);
            } catch (IllegalArgumentException e) {
                logger.error("Like toggle failed: Invalid entityType '{}'", entityType, e);
                response.put("success", false);
                response.put("message", "无效的实体类型");
                return ResponseEntity.badRequest().body(response);
            }

            boolean isLiked = likeService.toggleLike(user.getId(), type, entityId);
            long likeCount = likeService.getLikeCount(type, entityId);
            logger.info("Like status toggled. isLiked: {}, new likeCount: {}", isLiked, likeCount);

            response.put("success", true);
            response.put("isLiked", isLiked);
            response.put("likeCount", likeCount);
            response.put("message", isLiked ? "点赞成功" : "取消点赞成功");

            logger.debug("=== LikeController.toggleLike 处理完成 ===");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("=== LikeController.toggleLike 异常 ===");
            logger.error("Error occurred during like toggle for entityType {} and entityId {}", entityType, entityId, e);
            logger.error("异常类型: {}, 异常消息: {}", e.getClass().getSimpleName(), e.getMessage());

            response.put("success", false);
            response.put("message", "操作失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 检查用户是否已点赞
     */
    @GetMapping("/check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkLikeStatus(
            @RequestParam String entityType,
            @RequestParam Long entityId,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            logger.debug("=== LikeController.checkLikeStatus 开始处理 ===");
            logger.debug("entityType: {}, entityId: {}, authentication: {}", entityType, entityId, 
                (authentication != null ? authentication.getName() : "null"));

            if (authentication == null || !authentication.isAuthenticated()) {
                logger.debug("用户未登录，返回默认状态");
                long likeCount = likeService.getLikeCount(Like.EntityType.valueOf(entityType.toUpperCase()), entityId);
                response.put("isLiked", false);
                response.put("likeCount", likeCount);
                logger.debug("点赞数量: {}", likeCount);
                return ResponseEntity.ok(response);
            }

            String username = authentication.getName();
            logger.debug("用户名: {}", username);
            User user = userService.findByUsername(username);
            if (user == null) {
                logger.warn("用户查找失败：用户 '{}' 不存在", username);
                response.put("isLiked", false);
                response.put("likeCount", 0);
                response.put("error", "用户不存在");
                return ResponseEntity.ok(response);
            }
            logger.debug("用户ID: {}", user.getId());

            Like.EntityType type = Like.EntityType.valueOf(entityType.toUpperCase());
            logger.debug("实体类型: {}", type);

            boolean isLiked = likeService.isLikedByUser(user.getId(), type, entityId);
            long likeCount = likeService.getLikeCount(type, entityId);

            logger.debug("检查结果: isLiked={}, likeCount={}", isLiked, likeCount);

            response.put("isLiked", isLiked);
            response.put("likeCount", likeCount);

            logger.debug("=== LikeController.checkLikeStatus 处理完成 ===");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("=== LikeController.checkLikeStatus 异常 ===");
            logger.error("异常类型: {}, 异常消息: {}", e.getClass().getSimpleName(), e.getMessage(), e);

            response.put("isLiked", false);
            response.put("likeCount", 0);
            response.put("error", e.getMessage());
            return ResponseEntity.ok(response);
        }
    }


    /**
     * 获取最受欢迎的内容页面
     */
    @GetMapping("/popular")
    public String getPopularContent(Model model) {
        List<Content> popularContents = likeService.getMostLikedContents(10);
        List<Philosopher> popularPhilosophers = likeService.getMostLikedPhilosophers(10);
        List<School> popularSchools = likeService.getMostLikedSchools(10);
        
        model.addAttribute("popularContents", popularContents);
        model.addAttribute("popularPhilosophers", popularPhilosophers);
        model.addAttribute("popularSchools", popularSchools);
        
        return "popular";
    }

    /**
     * 获取实体的点赞数量
     */
    @GetMapping("/count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getLikeCount(
            @RequestParam String entityType,
            @RequestParam Long entityId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Like.EntityType type = Like.EntityType.valueOf(entityType.toUpperCase());
            long likeCount = likeService.getLikeCount(type, entityId);
            
            response.put("success", true);
            response.put("likeCount", likeCount);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "获取点赞数量失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
