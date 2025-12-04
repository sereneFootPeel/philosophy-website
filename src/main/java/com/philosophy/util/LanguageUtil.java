package com.philosophy.util;

import com.philosophy.service.IpLocationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 语言工具类
 * 用于统一处理语言获取逻辑，根据IP地址设置默认语言
 */
@Component
public class LanguageUtil {

    private static final Logger logger = LoggerFactory.getLogger(LanguageUtil.class);
    
    private final IpLocationService ipLocationService;
    
    public LanguageUtil(IpLocationService ipLocationService) {
        this.ipLocationService = ipLocationService;
    }
    
    /**
     * 获取当前语言设置
     * 如果Session中没有语言设置，则根据IP地址判断：
     * - 国外IP默认英语
     * - 国内IP默认中文
     * 
     * @param request HTTP请求
     * @return 语言代码（zh或en）
     */
    public String getLanguage(HttpServletRequest request) {
        if (request == null) {
            return "zh"; // 默认中文
        }
        
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        
        // 如果Session中已有语言设置，直接返回
        if (language != null && !language.trim().isEmpty()) {
            return language;
        }
        
        // Session中没有语言设置，根据IP地址判断
        try {
            boolean isForeign = ipLocationService.isForeignIp(request);
            language = isForeign ? "en" : "zh";
            
            // 将默认语言设置保存到Session，避免重复查询
            session.setAttribute("language", language);
            
            logger.debug("根据IP地址设置默认语言: {} (IP是否国外: {})", language, isForeign);
        } catch (Exception e) {
            logger.warn("根据IP地址判断语言失败，使用默认中文", e);
            language = "zh";
            session.setAttribute("language", language);
        }
        
        return language;
    }
    
    /**
     * 获取当前语言设置（不进行IP判断，仅从Session获取）
     * 
     * @param request HTTP请求
     * @return 语言代码（zh或en），如果Session中没有则返回null
     */
    public String getLanguageFromSession(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        
        HttpSession session = request.getSession();
        return (String) session.getAttribute("language");
    }
    
    /**
     * 设置语言到Session
     * 
     * @param request HTTP请求
     * @param language 语言代码
     */
    public void setLanguage(HttpServletRequest request, String language) {
        if (request == null || language == null) {
            return;
        }
        
        HttpSession session = request.getSession();
        session.setAttribute("language", language);
    }
}

