package com.philosophy.controller;

import com.philosophy.model.Content;
import com.philosophy.model.School;
import com.philosophy.model.User;
import com.philosophy.service.ContentService;
import com.philosophy.service.SchoolService;
import com.philosophy.service.TranslationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class SchoolPartialController {

    private final SchoolService schoolService;
    private final TranslationService translationService;
    private final ContentService contentService;

    public SchoolPartialController(SchoolService schoolService, TranslationService translationService, ContentService contentService) {
        this.schoolService = schoolService;
        this.translationService = translationService;
        this.contentService = contentService;
    }

    // 返回内容列表的局部HTML，用于AJAX更新右侧面板（首次加载）
    @GetMapping("/partials/schools/contents")
    public String getSchoolContentsPartial(@RequestParam("id") Long schoolId, Model model, HttpServletRequest request, Authentication authentication) {
        School school = schoolService.getSchoolById(schoolId);
        
        // 首次加载只获取第一页数据（10条）
        Map<String, Object> result = school != null ? 
            schoolService.getContentsBySchoolIdAdminModeratorOnlyPaged(schoolId, 0, 10) : 
            Map.of("contents", List.of(), "hasMore", false);
        
        @SuppressWarnings("unchecked")
        List<Content> contents = (List<Content>) result.get("contents");

        // 获取当前用户用于隐私过滤
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        User currentUser = null;
        if (isAuthenticated) {
            currentUser = (User) authentication.getPrincipal();
        }

        // 应用隐私和屏蔽过滤
        contents = contentService.filterContentsByPrivacy(contents, currentUser);

        // 语言和鉴权变量供片段使用
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) language = "zh";

        model.addAttribute("contents", contents);
        model.addAttribute("translationService", translationService);
        model.addAttribute("language", language);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("hasMore", result.get("hasMore"));
        model.addAttribute("schoolId", schoolId);
        return "fragments/content-list :: content-list";
    }

    // 返回更多内容（用于无限滚动）
    @GetMapping("/api/schools/contents/more")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMoreSchoolContents(
            @RequestParam("id") Long schoolId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            School school = schoolService.getSchoolById(schoolId);
            if (school == null) {
                response.put("success", false);
                response.put("message", "School not found");
                return ResponseEntity.notFound().build();
            }

            // 获取分页数据
            Map<String, Object> result = schoolService.getContentsBySchoolIdAdminModeratorOnlyPaged(schoolId, page, size);
            
            @SuppressWarnings("unchecked")
            List<Content> contents = (List<Content>) result.get("contents");

            // 获取当前用户用于隐私过滤
            boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
            User currentUser = null;
            if (isAuthenticated) {
                currentUser = (User) authentication.getPrincipal();
            }

            // 应用隐私和屏蔽过滤
            contents = contentService.filterContentsByPrivacy(contents, currentUser);

            response.put("success", true);
            response.put("contents", contents);
            response.put("hasMore", result.get("hasMore"));
            response.put("totalElements", result.get("totalElements"));
            response.put("currentPage", page);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error loading more contents: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}


