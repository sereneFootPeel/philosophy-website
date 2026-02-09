package com.philosophy.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import com.philosophy.util.LanguageUtil;

/**
 * 九型人格测试页面控制器。
 */
@Controller
public class EnneagramController {

    private final LanguageUtil languageUtil;

    public EnneagramController(LanguageUtil languageUtil) {
        this.languageUtil = languageUtil;
    }

    @GetMapping({ "/enneagram", "/Enneagram", "/enneagram.html" })
    public String enneagramPage(HttpServletRequest request, Model model) {
        String language = languageUtil.getLanguage(request);
        model.addAttribute("language", language);
        model.addAttribute("pageTitle", "九型人格测试");
        model.addAttribute("activePage", "enneagram");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "enneagram";
    }
}
