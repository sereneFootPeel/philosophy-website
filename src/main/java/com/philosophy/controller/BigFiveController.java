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
 * 大五人格（Big Five）测试页面控制器。
 */
@Controller
public class BigFiveController {

    private final LanguageUtil languageUtil;

    public BigFiveController(LanguageUtil languageUtil) {
        this.languageUtil = languageUtil;
    }

    @GetMapping({ "/bigfive", "/big-five", "/BigFive", "/Bigfive" })
    public String bigfivePage(HttpServletRequest request, Model model) {
        String language = languageUtil.getLanguage(request);
        model.addAttribute("language", language);
        model.addAttribute("pageTitle", "大五人格测验");
        model.addAttribute("activePage", "bigfive");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "bigfive";
    }
}
