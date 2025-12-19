package com.philosophy.controller;

import com.philosophy.model.User;
import com.philosophy.service.TranslationService;
import com.philosophy.service.UserService;
import com.philosophy.util.LanguageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Controller
public class LanguageController {

    private static final Logger logger = LoggerFactory.getLogger(LanguageController.class);
    
    private final TranslationService translationService;
    private final UserService userService;
    private final LanguageUtil languageUtil;

    public LanguageController(TranslationService translationService, UserService userService, LanguageUtil languageUtil) {
        this.translationService = translationService;
        this.userService = userService;
        this.languageUtil = languageUtil;
    }

    /**
     * 切换语言
     */
    @GetMapping("/language/switch")
    public String switchLanguage(@RequestParam String lang, 
                                HttpServletRequest request, 
                                HttpServletResponse response,
                                Authentication authentication) {
        setLanguageInternal(lang, request, response, authentication);
        
        // 获取来源页面，如果没有则返回首页
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isEmpty()) {
            return "redirect:" + referer;
        }
        
        return "redirect:/";
    }

    /**
     * 获取当前语言设置
     */
    @GetMapping("/language/current")
    @ResponseBody
    public Map<String, String> getCurrentLanguage(HttpServletRequest request) {
        String language = languageUtil.getLanguage(request);
        
        Map<String, String> result = new HashMap<>();
        result.put("language", language);
        return result;
    }

    /**
     * 设置语言（AJAX方式）
     */
    @GetMapping("/language/set")
    @ResponseBody
    public Map<String, Object> setLanguage(@RequestParam String lang, 
                                         HttpServletRequest request,
                                         HttpServletResponse response,
                                         Authentication authentication) {
        Map<String, Object> result = new HashMap<>();
        
        // 验证语言代码
        if (!isValidLanguageCode(lang)) {
            result.put("success", false);
            result.put("message", "不支持的语言代码");
            return result;
        }
        
        setLanguageInternal(lang, request, response, authentication);
        
        result.put("success", true);
        result.put("message", "语言设置成功");
        result.put("language", lang);
        
        return result;
    }
    
    /**
     * 内部方法：设置语言的通用逻辑
     */
    private void setLanguageInternal(String lang, 
                                     HttpServletRequest request,
                                     HttpServletResponse response,
                                     Authentication authentication) {
        // 验证语言代码
        if (!isValidLanguageCode(lang)) {
            lang = "zh"; // 默认中文
        }
        
        // 将语言设置保存到Session
        HttpSession session = request.getSession();
        session.setAttribute("language", lang);
        
        // 设置Cookie以便客户端JavaScript可以读取
        jakarta.servlet.http.Cookie languageCookie = new jakarta.servlet.http.Cookie("philosophy_language", lang);
        languageCookie.setPath("/");
        languageCookie.setMaxAge(30 * 24 * 60 * 60); // 30天
        response.addCookie(languageCookie);
        
        // 如果用户已登录，保存到数据库
        if (authentication != null && authentication.isAuthenticated() 
            && !"anonymousUser".equals(authentication.getName())) {
            try {
                User user = userService.findByUsername(authentication.getName());
                if (user != null) {
                    user.setLanguage(lang);
                    userService.updateUser(user);
                    logger.info("User {} updated language preference to: {}", user.getUsername(), lang);
                }
            } catch (Exception e) {
                logger.error("Failed to save language preference to database for user: " + authentication.getName(), e);
            }
        }
    }

    /**
     * 验证语言代码是否有效
     */
    private boolean isValidLanguageCode(String lang) {
        return "zh".equals(lang) || "en".equals(lang);
    }
}
