package com.philosophy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import jakarta.servlet.Filter;
import com.philosophy.security.DeviceIdFilter;
import com.philosophy.security.CustomAuthenticationFailureHandler;
import org.springframework.http.HttpMethod;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.philosophy.model.User;
import com.philosophy.util.UserInfoCollector;
import com.philosophy.service.IpLocationService;
import com.philosophy.util.LanguageUtil;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    
    private final UserInfoCollector userInfoCollector;
    private final com.philosophy.service.UserService userService;
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
    private final IpLocationService ipLocationService;
    private final LanguageUtil languageUtil;
    
    public SecurityConfig(UserInfoCollector userInfoCollector,
                          com.philosophy.service.UserService userService,
                          CustomAuthenticationFailureHandler customAuthenticationFailureHandler,
                          IpLocationService ipLocationService,
                          LanguageUtil languageUtil) {
        this.userInfoCollector = userInfoCollector;
        this.userService = userService;
        this.customAuthenticationFailureHandler = customAuthenticationFailureHandler;
        this.ipLocationService = ipLocationService;
        this.languageUtil = languageUtil;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        logger.info("Configuring security filters");
        
        http
            // 禁用CSRF保护以便于测试
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/register/send-code", "/likes/toggle", "/admin/data-import/upload", "/user/profile/*/theme")
            )
            // 添加请求日志记录过滤器
            .addFilterBefore((request, response, chain) -> {
                HttpServletRequest httpRequest = (HttpServletRequest) request;
                String uri = httpRequest.getRequestURI();
                String method = httpRequest.getMethod();
                logger.info("Incoming request: {} {}", method, uri);
                
                // 专门为评论路由添加详细日志
                if (uri.startsWith("/comments/content/")) {
                    logger.info("Processing comment route: {}, Method: {}", uri, method);
                }
                
                chain.doFilter(request, response);
            }, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(authorize -> authorize
                // 评论路由GET请求允许匿名访问
                .requestMatchers(HttpMethod.GET, "/comments/**").permitAll()
                // 公开的随机名句API
                .requestMatchers(HttpMethod.GET, "/api/quotes/random").permitAll()
                // 允许所有用户访问的页面
                .requestMatchers("/", "/home", "/philosophers", "/schools", "/schools/filter/**", "/api/schools/children", "/api/schools/detail", "/api/philosophers/**", "/partials/schools/contents", "/search/**", "/api/search/**", "/register", "/css/**", "/js/**", "/images/**", "/uploads/**", "/test/**", "/quotes", "/error", "/language/**", "/user/profile/**", "/contents").permitAll()
                // 允许发送注册验证码
                .requestMatchers(HttpMethod.POST, "/register/send-code").permitAll()
                // 允许访问Vite相关资源
                .requestMatchers("/@vite/**", "/node_modules/**").permitAll()
                // 评论提交需要认证
                .requestMatchers(HttpMethod.POST, "/comments/content/*").authenticated()
                // 评论删除需要认证
                .requestMatchers("/comments/delete/**").authenticated()
                // 评论隐私设置需要认证
                .requestMatchers(HttpMethod.POST, "/comments/privacy/**").authenticated()
                // 评论状态设置需要认证
                .requestMatchers(HttpMethod.POST, "/comments/status/**").authenticated()
                // 点赞相关端点需要认证
                .requestMatchers("/likes/**").authenticated()
                // 管理员页面需要ADMIN角色
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 版主页面需要MODERATOR角色
                .requestMatchers("/moderator/**").hasRole("MODERATOR")
                // 登录页面允许所有人访问
                .requestMatchers("/login").permitAll()
                // 其他所有请求都需要认证
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(customAuthenticationSuccessHandler())
                .failureHandler(customAuthenticationFailureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/")
                .permitAll()
            );
        
        return http.build();
    }

    @Bean
    public FilterRegistrationBean<Filter> deviceIdFilterRegistration(DeviceIdFilter deviceIdFilter) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(deviceIdFilter);
        registration.addUrlPatterns("/*");
        registration.setName("deviceIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    // Remove the deprecated authenticationProvider() method as it's no longer needed
    // Spring Security will automatically configure DaoAuthenticationProvider
    // when UserDetailsService and PasswordEncoder beans are available

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public AuthenticationSuccessHandler customAuthenticationSuccessHandler() {
        return (request, response, authentication) -> {
            // 记录用户登录信息并重置失败次数
            try {
                User user = (User) authentication.getPrincipal();
                
                // 检查账户锁定是否已过期，如果过期则自动解锁
                if (user.isAccountLocked() && user.getLockExpireTime() != null && 
                    java.time.LocalDateTime.now().isAfter(user.getLockExpireTime())) {
                    user.setAccountLocked(false);
                    user.setLockTime(null);
                    user.setLockExpireTime(null);
                    user.setFailedLoginAttempts(0);
                    userService.saveUser(user);
                    logger.info("账户 {} 的锁定已自动过期解锁", user.getUsername());
                }
                
                // 重置登录失败次数
                userService.resetFailedAttempts(user.getUsername());
                
                // 记录登录信息
                userInfoCollector.recordLoginInfo(user, request);
                logger.info("User {} logged in from IP: {}", user.getUsername(), userInfoCollector.getUserIpAddress(user.getId()));
                
                // 从数据库读取用户的语言偏好并设置到Session和Cookie
                try {
                    String userLanguage = user.getLanguage();
                    
                    // 如果用户没有设置语言偏好，根据IP地址判断默认语言
                    if (userLanguage == null || userLanguage.trim().isEmpty()) {
                        boolean isForeign = ipLocationService.isForeignIp(request);
                        userLanguage = isForeign ? "en" : "zh";
                        
                        // 保存到数据库
                        user.setLanguage(userLanguage);
                        userService.updateUser(user);
                        
                        logger.info("User {} 没有语言偏好，根据IP地址设置默认语言: {} (IP是否国外: {})", 
                                    user.getUsername(), userLanguage, isForeign);
                    }
                    
                    // 设置到Session
                    request.getSession().setAttribute("language", userLanguage);
                    
                    // 设置到Cookie
                    jakarta.servlet.http.Cookie languageCookie = new jakarta.servlet.http.Cookie("philosophy_language", userLanguage);
                    languageCookie.setPath("/");
                    languageCookie.setMaxAge(30 * 24 * 60 * 60); // 30天
                    response.addCookie(languageCookie);
                    
                    logger.info("User {} language preference loaded: {}", user.getUsername(), userLanguage);
                } catch (Exception e) {
                    logger.error("Failed to load user language preference", e);
                    // 如果出错，使用工具类获取默认语言
                    try {
                        String defaultLanguage = languageUtil.getLanguage(request);
                        request.getSession().setAttribute("language", defaultLanguage);
                    } catch (Exception ex) {
                        logger.error("Failed to set default language", ex);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to record user login info", e);
            }
            
            // 检查是否有重定向参数
            String redirectUrl = request.getParameter("redirect");
            if (redirectUrl != null && !redirectUrl.isEmpty()) {
                response.sendRedirect(redirectUrl);
                return;
            }

            // 无论角色，默认重定向到首页，保持与普通用户一致
            response.sendRedirect("/");
        };
    }
}