package com.philosophy.util;

/**
 * 输入验证工具类
 * 提供统一的输入验证功能，防止恶意输入和DoS攻击
 */
public class InputValidator {

    /**
     * 验证字符串是否为空或超过最大长度
     * 
     * @param value 要验证的字符串
     * @param fieldName 字段名称（用于错误消息）
     * @param maxLength 最大长度
     * @param allowEmpty 是否允许为空
     * @return 验证通过返回null，否则返回错误消息
     */
    public static String validateString(String value, String fieldName, int maxLength, boolean allowEmpty) {
        if (value == null || value.trim().isEmpty()) {
            if (!allowEmpty) {
                return fieldName + "不能为空";
            }
            return null;
        }
        
        if (value.length() > maxLength) {
            return fieldName + "长度不能超过" + maxLength + "个字符";
        }
        
        return null;
    }
    
    /**
     * 验证必填字符串字段
     */
    public static String validateRequired(String value, String fieldName, int maxLength) {
        return validateString(value, fieldName, maxLength, false);
    }
    
    /**
     * 验证可选字符串字段
     */
    public static String validateOptional(String value, String fieldName, int maxLength) {
        return validateString(value, fieldName, maxLength, true);
    }
    
    /**
     * 验证邮箱格式
     */
    public static String validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return "邮箱不能为空";
        }
        
        if (email.length() > 100) {
            return "邮箱长度不能超过100个字符";
        }
        
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return "请输入有效的邮箱地址";
        }
        
        return null;
    }
    
    /**
     * 验证用户名
     */
    public static String validateUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "用户名不能为空";
        }
        
        if (username.length() < 3 || username.length() > 50) {
            return "用户名长度必须在3到50个字符之间";
        }
        
        if (!username.matches("^[a-zA-Z0-9_\\u4e00-\\u9fa5]+$")) {
            return "用户名只能包含字母、数字、下划线和中文";
        }
        
        return null;
    }
    
    /**
     * 验证密码
     */
    public static String validatePassword(String password) {
        if (password == null || password.trim().isEmpty()) {
            return "密码不能为空";
        }
        
        if (password.length() < 6) {
            return "密码长度至少为6位";
        }
        
        if (password.length() > 100) {
            return "密码长度不能超过100个字符";
        }
        
        return null;
    }
    
    /**
     * 验证内容文本（用于文章、描述等）
     */
    public static String validateContent(String content, String fieldName) {
        if (content == null || content.trim().isEmpty()) {
            return fieldName + "不能为空";
        }
        
        if (content.length() > 50000) {
            return fieldName + "长度不能超过50000个字符";
        }
        
        return null;
    }
    
    /**
     * 验证评论
     */
    public static String validateComment(String comment) {
        if (comment == null || comment.trim().isEmpty()) {
            return "评论内容不能为空";
        }
        
        if (comment.length() > 5000) {
            return "评论内容不能超过5000个字符";
        }
        
        return null;
    }
    
    /**
     * 验证标题
     */
    public static String validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "标题不能为空";
        }
        
        if (title.length() > 200) {
            return "标题长度不能超过200个字符";
        }
        
        return null;
    }
    
    /**
     * 清理输入，移除潜在的危险字符
     */
    public static String sanitizeInput(String input) {
        if (input == null) {
            return null;
        }
        
        // 移除控制字符
        return input.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "").trim();
    }
}
