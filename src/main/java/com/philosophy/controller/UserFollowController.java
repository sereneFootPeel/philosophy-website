package com.philosophy.controller;

import com.philosophy.model.User;
import com.philosophy.service.UserFollowService;
import com.philosophy.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/api/follow")
public class UserFollowController {

    private final UserFollowService userFollowService;
    private final UserService userService;

    public UserFollowController(UserFollowService userFollowService, UserService userService) {
        this.userFollowService = userFollowService;
        this.userService = userService;
    }

    /**
     * 关注用户
     */
    @PostMapping("/{userId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> followUser(@PathVariable Long userId, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.badRequest().body(response);
        }

        User currentUser = (User) authentication.getPrincipal();
        if (currentUser.getId().equals(userId)) {
            response.put("success", false);
            response.put("message", "不能关注自己");
            return ResponseEntity.badRequest().body(response);
        }

        // 检查要关注的用户是否存在
        User targetUser = userService.getUserById(userId);
        if (targetUser == null) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.badRequest().body(response);
        }

        boolean success = userFollowService.followUser(currentUser.getId(), userId);
        if (success) {
            response.put("success", true);
            response.put("message", "关注成功");
            response.put("followingCount", userFollowService.getFollowingCount(currentUser.getId()));
            response.put("followerCount", userFollowService.getFollowerCount(userId));
        } else {
            response.put("success", false);
            response.put("message", "已经关注过该用户");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 取消关注用户
     */
    @DeleteMapping("/{userId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unfollowUser(@PathVariable Long userId, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            response.put("success", false);
            response.put("message", "请先登录");
            return ResponseEntity.badRequest().body(response);
        }

        User currentUser = (User) authentication.getPrincipal();
        boolean success = userFollowService.unfollowUser(currentUser.getId(), userId);
        
        if (success) {
            response.put("success", true);
            response.put("message", "取消关注成功");
            response.put("followingCount", userFollowService.getFollowingCount(currentUser.getId()));
            response.put("followerCount", userFollowService.getFollowerCount(userId));
        } else {
            response.put("success", false);
            response.put("message", "未关注该用户");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * 检查是否关注了某个用户
     */
    @GetMapping("/{userId}/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFollowStatus(@PathVariable Long userId, Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            response.put("isFollowing", false);
            return ResponseEntity.ok(response);
        }

        User currentUser = (User) authentication.getPrincipal();
        boolean isFollowing = userFollowService.isFollowing(currentUser.getId(), userId);
        
        response.put("isFollowing", isFollowing);
        return ResponseEntity.ok(response);
    }

    /**
     * 获取用户的关注和粉丝数量
     */
    @GetMapping("/{userId}/counts")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getUserCounts(@PathVariable Long userId) {
        Map<String, Object> response = new HashMap<>();
        
        long followingCount = userFollowService.getFollowingCount(userId);
        long followerCount = userFollowService.getFollowerCount(userId);
        
        response.put("followingCount", followingCount);
        response.put("followerCount", followerCount);
        
        return ResponseEntity.ok(response);
    }
}
