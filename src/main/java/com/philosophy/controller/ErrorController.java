package com.philosophy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

    private static final Logger logger = LoggerFactory.getLogger(ErrorController.class);

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute("jakarta.servlet.error.status_code");
        Object message = request.getAttribute("jakarta.servlet.error.message");
        Object exception = request.getAttribute("jakarta.servlet.error.exception");
        
        int statusCode = 500; // Default to 500
        try {
            if (status != null) {
                statusCode = Integer.parseInt(status.toString());
            }
        } catch (NumberFormatException e) {
            logger.error("Failed to parse status code: {}", status, e);
        }
        
        String errorMessage = message != null ? message.toString() : "服务器内部错误";
        
        if (exception != null) {
            Throwable throwable = (Throwable) exception;
            errorMessage = throwable.getMessage() != null ? throwable.getMessage() : errorMessage;
            logger.error("Error occurred: Status={}, Message={}", statusCode, errorMessage, throwable);
        }
        
        model.addAttribute("statusCode", statusCode);
        model.addAttribute("errorMessage", errorMessage);
        
        return "error";
    }
}