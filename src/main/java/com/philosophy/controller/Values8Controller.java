package com.philosophy.controller;

import com.philosophy.util.LanguageUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 8values 政治价值测试页面控制器。
 */
@Controller
public class Values8Controller {

    private final LanguageUtil languageUtil;

    public Values8Controller(LanguageUtil languageUtil) {
        this.languageUtil = languageUtil;
    }

    @GetMapping({ "/values8", "/values-8", "/8values", "/eightvalues" })
    public String values8Page(HttpServletRequest request, Model model) {
        String language = languageUtil.getLanguage(request);
        model.addAttribute("language", language);
        model.addAttribute("pageTitle", language != null && "en".equals(language)
                ? "8values Political Values Test"
                : "8values 政治价值测试");
        model.addAttribute("activePage", "values8");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "values8";
    }
}
