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
 * 测试入口页面控制器，展示各类人格/心理测试的入口。
 */
@Controller
public class TestsController {

    private final LanguageUtil languageUtil;

    public TestsController(LanguageUtil languageUtil) {
        this.languageUtil = languageUtil;
    }

    @GetMapping({ "/tests", "/test" })
    public String testsPage(HttpServletRequest request, Model model) {
        String language = languageUtil.getLanguage(request);
        model.addAttribute("language", language);
        model.addAttribute("pageTitle", language != null && "en".equals(language) ? "Personality Tests" : "心理测试");
        model.addAttribute("activePage", "tests");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "tests";
    }
}
