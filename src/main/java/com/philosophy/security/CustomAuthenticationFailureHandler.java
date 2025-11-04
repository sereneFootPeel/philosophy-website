package com.philosophy.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Random;

@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Random random = new Random();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                       HttpServletResponse response,
                                       AuthenticationException exception) throws IOException {
        
        // 随机选择错误类型：0 = 账号错误, 1 = 密码错误
        int errorType = random.nextInt(2);
        String errorMessage = errorType == 0 ? "account" : "password";
        
        // 将错误信息保存到 session（作为备用）
        request.getSession().setAttribute("loginError", errorMessage);
        
        // 通过 URL 参数传递错误类型，更可靠
        response.sendRedirect("/login?error=" + errorMessage);
    }
}

