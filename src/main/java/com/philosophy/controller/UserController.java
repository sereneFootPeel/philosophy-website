package com.philosophy.controller;

import com.philosophy.model.Content;
import com.philosophy.model.Philosopher;
import com.philosophy.model.School;
import com.philosophy.model.User;
import com.philosophy.service.ContentService;
import com.philosophy.service.PhilosopherService;
import com.philosophy.service.SchoolService;
import com.philosophy.service.TranslationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/user")
public class UserController {

    private final ContentService contentService;
    private final PhilosopherService philosopherService;
    private final SchoolService schoolService;
    private final TranslationService translationService;

    public UserController(ContentService contentService, PhilosopherService philosopherService,
                         SchoolService schoolService, TranslationService translationService) {
        this.contentService = contentService;
        this.philosopherService = philosopherService;
        this.schoolService = schoolService;
        this.translationService = translationService;
    }

    /**
     * 用户设置页面
     */
    @GetMapping("/settings")
    public String userSettings(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = (User) authentication.getPrincipal();
        model.addAttribute("user", user);
        
        return "user/settings";
    }

    /**
     * 用户最近浏览页面
     */
    @GetMapping("/recent")
    public String userRecent(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = (User) authentication.getPrincipal();
        model.addAttribute("user", user);
        
        return "user/recent";
    }

    /**
     * 用户收藏夹页面
     */
    @GetMapping("/favorites")
    public String userFavorites(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = (User) authentication.getPrincipal();
        model.addAttribute("user", user);
        
        return "user/favorites";
    }


}