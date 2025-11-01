package com.philosophy.controller;

import com.philosophy.model.*;
import com.philosophy.service.UserContentEditService;
import com.philosophy.service.PhilosopherService;
import com.philosophy.service.SchoolService;
import com.philosophy.service.ContentService;
import com.philosophy.service.TranslationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Controller
@RequestMapping("/user/content-edits")
public class UserContentEditController {
    
    @Autowired
    private UserContentEditService userContentEditService;

    @Autowired
    private PhilosopherService philosopherService;

    @Autowired
    private SchoolService schoolService;

    @Autowired
    private ContentService contentService;

    @Autowired
    private TranslationService translationService;
    
    // 显示用户编辑列表
    @GetMapping
    public String listUserEdits(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "10") int size,
                               Authentication authentication,
                               HttpServletRequest request,
                               Model model) {
        User user = (User) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size);
        Page<UserContentEdit> edits = userContentEditService.getUserEdits(user.getId(), pageable);

        // 获取语言设置
        String language = (String) request.getSession().getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 判断用户是否已认证
        boolean isAuthenticated = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);

        model.addAttribute("edits", edits);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", edits.getTotalPages());
        model.addAttribute("totalElements", edits.getTotalElements());
        model.addAttribute("translationService", translationService);
        model.addAttribute("language", language);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "user/content-edits";
    }

    // 显示用户自己的内容列表
    @GetMapping("/my-contents")
    public String listUserContents(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size,
                                  Authentication authentication,
                                  HttpServletRequest request,
                                  Model model) {
        User user = (User) authentication.getPrincipal();
        Pageable pageable = PageRequest.of(page, size);
        Page<Content> contents = contentService.findUserOwnContents(user.getId(), pageable);

        // 获取语言设置
        String language = (String) request.getSession().getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 判断用户是否已认证
        boolean isAuthenticated = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);

        model.addAttribute("contents", contents);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", contents.getTotalPages());
        model.addAttribute("totalElements", contents.getTotalElements());
        model.addAttribute("translationService", translationService);
        model.addAttribute("language", language);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "user/my-contents";
    }
    
    // 显示创建新内容页面
    @GetMapping("/create")
    public String showCreateContentForm(HttpServletRequest request,
                                       Authentication authentication,
                                       Model model) {
        List<Philosopher> philosophers = philosopherService.getAllPhilosophers();
        List<School> schools = schoolService.getAllSchools();

        // 获取语言设置
        String language = (String) request.getSession().getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 判断用户是否已认证
        boolean isAuthenticated = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);

        model.addAttribute("philosophers", philosophers);
        model.addAttribute("schools", schools);
        model.addAttribute("edit", new UserContentEdit());
        model.addAttribute("translationService", translationService);
        model.addAttribute("language", language);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "user/content-create-form";
    }

    // 显示创建编辑页面（基于现有内容）
    @GetMapping("/create/{contentId}")
    public String showCreateEditForm(@PathVariable Long contentId,
                                   HttpServletRequest request,
                                   Authentication authentication,
                                   Model model) {
        Content content = contentService.getContentById(contentId);
        if (content == null) {
            throw new RuntimeException("内容不存在");
        }

        List<Philosopher> philosophers = philosopherService.getAllPhilosophers();
        List<School> schools = schoolService.getAllSchools();

        // 获取语言设置
        String language = (String) request.getSession().getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 判断用户是否已认证
        boolean isAuthenticated = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);

        model.addAttribute("content", content);
        model.addAttribute("philosophers", philosophers);
        model.addAttribute("schools", schools);
        model.addAttribute("edit", new UserContentEdit());
        model.addAttribute("translationService", translationService);
        model.addAttribute("language", language);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "user/content-edit-form";
    }
    
    // 处理创建编辑
    @PostMapping("/create")
    public String createEdit(@RequestParam(required = false) Long originalContentId,
                           @RequestParam Long philosopherId,
                           @RequestParam Long schoolId,
                           @RequestParam String title,
                           @RequestParam String content,
                           @RequestParam(required = false) String contentEn,
                           Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        try {
            // 输入验证
            if (title == null || title.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "标题不能为空");
                return "redirect:/user/content-edits";
            }
            if (title.length() > 200) {
                redirectAttributes.addFlashAttribute("errorMessage", "标题长度不能超过200个字符");
                return "redirect:/user/content-edits";
            }
            if (content == null || content.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "内容不能为空");
                return "redirect:/user/content-edits";
            }
            if (content.length() > 50000) {
                redirectAttributes.addFlashAttribute("errorMessage", "内容长度不能超过50000个字符");
                return "redirect:/user/content-edits";
            }
            if (contentEn != null && contentEn.length() > 50000) {
                redirectAttributes.addFlashAttribute("errorMessage", "英文内容长度不能超过50000个字符");
                return "redirect:/user/content-edits";
            }
            
            User user = (User) authentication.getPrincipal();

            // 如果没有指定originalContentId，则创建新内容并设置user
            if (originalContentId == null) {
                Content newContent = new Content();
                newContent.setContent(content);
                newContent.setPhilosopher(philosopherService.getPhilosopherById(philosopherId));
                newContent.setSchool(schoolService.getSchoolById(schoolId));

                // 保存内容并设置创建者
                Content savedContent = contentService.saveContentWithUser(newContent, user);

                // 创建用户编辑记录
                UserContentEdit userEdit = userContentEditService.createEdit(user, savedContent.getId(), philosopherId, schoolId, title, content, contentEn);

                // 添加成功消息
                redirectAttributes.addFlashAttribute("successMessage", "编辑已保存");

                // 重定向到编辑页面
                return "redirect:/user/content-edits/edit/" + userEdit.getId();
            } else {
                // 编辑现有内容
                UserContentEdit userEdit = userContentEditService.createEdit(user, originalContentId, philosopherId, schoolId, title, content, contentEn);

                // 添加成功消息
                redirectAttributes.addFlashAttribute("successMessage", "编辑已保存");

                // 重定向到编辑页面
                return "redirect:/user/content-edits/edit/" + userEdit.getId();
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "创建编辑失败: " + e.getMessage());
        }

        return "redirect:/user/content-edits";
    }
    
    // 显示编辑表单
    @GetMapping("/edit/{editId}")
    public String showEditForm(@PathVariable Long editId,
                             HttpServletRequest request,
                             Authentication authentication,
                             Model model) {
        UserContentEdit edit = userContentEditService.getEditById(editId)
            .orElseThrow(() -> new RuntimeException("编辑不存在"));

        List<Philosopher> philosophers = philosopherService.getAllPhilosophers();
        List<School> schools = schoolService.getAllSchools();

        // 获取语言设置
        String language = (String) request.getSession().getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 判断用户是否已认证
        boolean isAuthenticated = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);

        model.addAttribute("edit", edit);
        model.addAttribute("content", edit.getOriginalContent());
        model.addAttribute("philosophers", philosophers);
        model.addAttribute("schools", schools);
        model.addAttribute("translationService", translationService);
        model.addAttribute("language", language);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "user/content-edit-form";
    }
    
    // 处理更新编辑
    @PostMapping("/edit/{editId}")
    public String updateEdit(@PathVariable Long editId,
                           @RequestParam Long philosopherId,
                           @RequestParam Long schoolId,
                           @RequestParam String title,
                           @RequestParam String content,
                           @RequestParam(required = false) String contentEn,
                           RedirectAttributes redirectAttributes) {
        try {
            // 输入验证
            if (title == null || title.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "标题不能为空");
                return "redirect:/user/content-edits/edit/" + editId;
            }
            if (title.length() > 200) {
                redirectAttributes.addFlashAttribute("errorMessage", "标题长度不能超过200个字符");
                return "redirect:/user/content-edits/edit/" + editId;
            }
            if (content == null || content.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "内容不能为空");
                return "redirect:/user/content-edits/edit/" + editId;
            }
            if (content.length() > 50000) {
                redirectAttributes.addFlashAttribute("errorMessage", "内容长度不能超过50000个字符");
                return "redirect:/user/content-edits/edit/" + editId;
            }
            if (contentEn != null && contentEn.length() > 50000) {
                redirectAttributes.addFlashAttribute("errorMessage", "英文内容长度不能超过50000个字符");
                return "redirect:/user/content-edits/edit/" + editId;
            }
            
            userContentEditService.updateEdit(editId, philosopherId, schoolId, title, content, contentEn);
            redirectAttributes.addFlashAttribute("successMessage", "编辑已更新");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "更新编辑失败: " + e.getMessage());
        }
        
        return "redirect:/user/content-edits";
    }
    
    // 删除编辑
    @PostMapping("/delete/{editId}")
    public String deleteEdit(@PathVariable Long editId, RedirectAttributes redirectAttributes) {
        try {
            userContentEditService.deleteEdit(editId);
            redirectAttributes.addFlashAttribute("successMessage", "编辑已删除");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "删除编辑失败: " + e.getMessage());
        }
        
        return "redirect:/user/content-edits";
    }
    
    // 显示内容的所有用户编辑
    @GetMapping("/content/{contentId}")
    public String showContentEdits(@PathVariable Long contentId,
                                 HttpServletRequest request,
                                 Authentication authentication,
                                 Model model) {
        List<UserContentEdit> edits = userContentEditService.getContentEdits(contentId);
        Content content = contentService.getContentById(contentId);
        if (content == null) {
            throw new RuntimeException("内容不存在");
        }

        // 获取语言设置
        String language = (String) request.getSession().getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 判断用户是否已认证
        boolean isAuthenticated = authentication != null && !(authentication instanceof AnonymousAuthenticationToken);

        model.addAttribute("edits", edits);
        model.addAttribute("content", content);
        model.addAttribute("translationService", translationService);
        model.addAttribute("language", language);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "user/content-edits-list";
    }
}
