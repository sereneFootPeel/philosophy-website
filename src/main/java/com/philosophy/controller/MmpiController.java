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
 * MMPI（明尼苏达多相人格测验）测试页面控制器。
 */
@Controller
public class MmpiController {

    private final LanguageUtil languageUtil;

    public MmpiController(LanguageUtil languageUtil) {
        this.languageUtil = languageUtil;
    }

    @GetMapping({ "/mmpi", "/MMPI", "/Mmpi" })
    public String mmpiPage(HttpServletRequest request, Model model) {
        String language = languageUtil.getLanguage(request);
        model.addAttribute("language", language);
        model.addAttribute("pageTitle", "MMPI 人格测验");
        model.addAttribute("activePage", "mmpi");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("isAuthenticated", isAuthenticated);

        return "mmpi";
    }
}
