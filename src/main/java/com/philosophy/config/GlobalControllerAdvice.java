package com.philosophy.config;

import com.philosophy.model.User;
import com.philosophy.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 全局Controller通知
 * 为所有页面提供通用的模型属性
 * 
 * @author James Gosling
 * @date 2025-10-17
 */
@ControllerAdvice
public class GlobalControllerAdvice {

    private final UserService userService;

    public GlobalControllerAdvice(UserService userService) {
        this.userService = userService;
    }

    /**
     * 为所有页面添加用户主题设置
     * 确保header.html中的主题加载脚本能正常工作
     */
    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            // 检查用户是否已登录
            if (authentication != null && authentication.isAuthenticated() 
                && !"anonymousUser".equals(authentication.getPrincipal())) {
                
                String username = authentication.getName();
                User currentUser = userService.findByUsername(username);
                
                if (currentUser != null && currentUser.getTheme() != null) {
                    // 添加用户保存的主题设置
                    model.addAttribute("savedTheme", currentUser.getTheme());
                } else {
                    // 新用户使用极简纯白主题作为默认主题
                    model.addAttribute("savedTheme", "midnight");
                }
            } else {
                // 游客用户使用极简纯白主题作为默认主题
                model.addAttribute("savedTheme", "midnight");
            }
        } catch (Exception e) {
            // 出错时使用极简纯白主题作为默认主题
            model.addAttribute("savedTheme", "midnight");
        }
    }
}
