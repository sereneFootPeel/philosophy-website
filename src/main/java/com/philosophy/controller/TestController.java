package com.philosophy.controller;

import com.philosophy.model.Comment;
import com.philosophy.model.Content;
import com.philosophy.model.User;
import com.philosophy.service.CommentService;
import com.philosophy.service.ContentService;
import com.philosophy.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class TestController {

    private final CommentService commentService;
    private final ContentService contentService;
    private final UserService userService;

    public TestController(CommentService commentService, ContentService contentService, UserService userService) {
        this.commentService = commentService;
        this.contentService = contentService;
        this.userService = userService;
    }

    @GetMapping("/test/comments/{contentId}")
    public String testComments(@PathVariable Long contentId, Model model) {
        // 获取内容
        Content content = contentService.getContentById(contentId);
        if (content == null) {
            model.addAttribute("error", "Content not found");
            return "error";
        }

        // 获取所有评论（不过滤）
        List<Comment> allComments = commentService.findByContentId(contentId);
        
        // 获取过滤后的评论
        List<Comment> filteredComments = commentService.findByContentIdWithPrivacyFilter(contentId, null);

        model.addAttribute("content", content);
        model.addAttribute("allComments", allComments);
        model.addAttribute("filteredComments", filteredComments);
        model.addAttribute("allCommentsCount", allComments.size());
        model.addAttribute("filteredCommentsCount", filteredComments.size());

        return "test-comments";
    }

    @PostMapping("/test/comments/{contentId}/add")
    public String addTestComment(@PathVariable Long contentId, 
                                @RequestParam String body,
                                @RequestParam String username) {
        // 获取或创建测试用户
        User user = userService.findByUsername(username);
        if (user == null) {
            // 创建测试用户
            user = new User();
            user.setUsername(username);
            user.setEmail(username + "@test.com");
            user.setPassword("password");
            user.setRole("USER");
            user = userService.saveUser(user);
        }

        // 添加评论
        commentService.saveComment(contentId, user, body);

        return "redirect:/test/comments/" + contentId;
    }
}