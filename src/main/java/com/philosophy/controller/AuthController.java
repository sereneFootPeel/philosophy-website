package com.philosophy.controller;

import com.philosophy.model.User;
import com.philosophy.service.UserService;
import com.philosophy.service.TranslationService;
import com.philosophy.service.EmailService;
import com.philosophy.service.VerificationCodeService;
import com.philosophy.service.RateLimitingService;
import com.philosophy.util.UserInfoCollector;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;
import java.util.Map;


@Controller
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    private final UserService userService;
    private final UserInfoCollector userInfoCollector;
    private final TranslationService translationService;
    private final EmailService emailService;
    private final VerificationCodeService verificationCodeService;
    private final RateLimitingService rateLimitingService;
    
    public AuthController(UserService userService, UserInfoCollector userInfoCollector, TranslationService translationService, EmailService emailService, VerificationCodeService verificationCodeService, RateLimitingService rateLimitingService) {
        this.userService = userService;
        this.userInfoCollector = userInfoCollector;
        this.translationService = translationService;
        this.emailService = emailService;
        this.verificationCodeService = verificationCodeService;
        this.rateLimitingService = rateLimitingService;
    }

    @GetMapping("/login")
    public String login(Model model, HttpServletRequest request) {
        // 获取当前语言设置
        String language = (String) request.getSession().getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }
        
        // 检查是否有登录错误信息
        String loginError = (String) request.getSession().getAttribute("loginError");
        if (loginError != null) {
            model.addAttribute("loginError", loginError);
            // 清除 session 中的错误信息，避免重复显示
            request.getSession().removeAttribute("loginError");
        }
        
        model.addAttribute("activePage", "login");
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        return "login";
    }

    @GetMapping("/register")
    public String register(Model model, HttpServletRequest request) {
        // 获取当前语言设置
        String language = (String) request.getSession().getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }
        
        model.addAttribute("user", new User());
        model.addAttribute("activePage", "register");
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        return "register";
    }

    @PostMapping("/register/send-code")
    public ResponseEntity<Map<String, Object>> sendRegistrationCode(@RequestParam String email, HttpServletRequest request) {
        // 获取客户端IP地址
        String clientIp = userInfoCollector.getIpAddress(request);
        
        // 检查速率限制（防止DDoS攻击）
        RateLimitingService.RateLimitResult rateLimitResult = rateLimitingService.checkRateLimit(clientIp);
        if (!rateLimitResult.isAllowed()) {
            logger.warn("IP {} 触发速率限制，等待时间: {} 秒", clientIp, rateLimitResult.getWaitSeconds());
            return ResponseEntity.status(429).body(Collections.singletonMap("error", 
                rateLimitResult.getMessage() + "（剩余等待时间: " + rateLimitResult.getWaitSeconds() + "秒）"));
        }
        
        if (userService.existsByEmail(email)) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "该邮箱已被注册"));
        }
        try {
            String code = verificationCodeService.generateAndStoreCode(email);
            emailService.sendVerificationCode(email, code);
            long cooldown = verificationCodeService.getSecondsUntilResendAllowed(email);
            logger.info("成功发送验证码到邮箱: {}, IP: {}", email, clientIp);
            return ResponseEntity.ok(Collections.singletonMap("cooldown", cooldown));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(429).body(Collections.singletonMap("error", "请稍后再试"));
        } catch (Exception e) {
            logger.error("发送验证码失败，邮箱: {}, IP: {}", email, clientIp, e);
            return ResponseEntity.internalServerError().body(Collections.singletonMap("error", "发送失败，请稍后再试"));
        }
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute("user") User user, 
                             @RequestParam("verificationCode") String verificationCode,
                             BindingResult bindingResult, 
                             Model model,
                             HttpServletRequest request) {
        
        if (!verificationCodeService.verifyCode(user.getEmail(), verificationCode)) {
            bindingResult.rejectValue("email", "error.user", "验证码错误或已失效");
        }

        // 手动验证
        if (user.getUsername() == null || user.getUsername().trim().isEmpty() || user.getUsername().length() > 50) {
            bindingResult.rejectValue("username", "error.user", 
                    "用户名不能为空且不能超过50个字符");
        }
        
        if (user.getEmail() == null || user.getEmail().trim().isEmpty() || user.getEmail().length() > 100) {
            bindingResult.rejectValue("email", "error.user", 
                    "请输入有效的邮箱地址");
        } else {
            // 简单的邮箱格式验证
            if (!user.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                bindingResult.rejectValue("email", "error.user", 
                        "请输入有效的邮箱地址");
            }
        }
        
        if (user.getPassword() == null || user.getPassword().trim().isEmpty()) {
            bindingResult.rejectValue("password", "error.user", 
                    "密码不能为空");
        } else if (user.getPassword().trim().length() < 6) {
            bindingResult.rejectValue("password", "error.user", 
                    "密码长度至少为6位");
        }
        
        // 检查用户名是否已存在
        if (userService.existsByUsername(user.getUsername())) {
            bindingResult.rejectValue("username", "error.user", 
                    "该用户名已被注册");
        }
        
        // 检查邮箱是否已存在
        if (userService.existsByEmail(user.getEmail())) {
            bindingResult.rejectValue("email", "error.user", 
                    "该邮箱已被注册");
        }
        
        // 如果有验证错误，返回注册页面
        if (bindingResult.hasErrors()) {
            // 获取当前语言设置
            String language = (String) request.getSession().getAttribute("language");
            if (language == null) {
                language = "zh"; // 默认中文
            }
            model.addAttribute("language", language);
            model.addAttribute("translationService", translationService);
            return "register";
        }
        
        // 注册新用户
        User registeredUser = userService.registerNewUser(user);
        
        // 记录注册信息（首次登录记录）
        userInfoCollector.recordLoginInfo(registeredUser, request);
        
        // 检查并删除同一天内相同IP和设备的旧账户
        List<Long> deletedUserIds = userInfoCollector.checkAndDeleteDuplicateAccounts(registeredUser.getId(), request);
        if (!deletedUserIds.isEmpty()) {
            logger.info("注册完成后删除了 {} 个重复用户账户", deletedUserIds.size());
        }
        
        // 注册成功，重定向到登录页面
        String language = (String) request.getSession().getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        model.addAttribute("successMessage", "注册成功，请登录");
        return "login";
    }
}
    