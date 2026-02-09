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
 * MBTI（迈尔斯-布里格斯性格分类法）测试页面控制器。
 */
@Controller
public class MbtiController {

    private final LanguageUtil languageUtil;

    public MbtiController(LanguageUtil languageUtil) {
        this.languageUtil = languageUtil;
    }

    @GetMapping({ "/mbti", "/MBTI", "/Mbti" })
    public String mbtiPage(HttpServletRequest request, Model model) {
        String language = languageUtil.getLanguage(request);
        model.addAttribute("language", language);
        model.addAttribute("pageTitle", "MBTI 性格测试");
        model.addAttribute("activePage", "mbti");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "mbti";
    }
}
