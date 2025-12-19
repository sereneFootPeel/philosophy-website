package com.philosophy.controller;

import com.philosophy.service.DataImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/data-import")
@PreAuthorize("hasRole('ADMIN')")
public class DataImportController {

    private static final Logger logger = LoggerFactory.getLogger(DataImportController.class);

    @Autowired
    private DataImportService dataImportService;

    /**
     * 显示数据导入页面
     */
    @GetMapping
    public String showImportPage(Model model) {
        model.addAttribute("title", "数据导入");
        return "admin/data-import";
    }

    /**
     * 处理CSV文件上传和导入
     */
    @PostMapping("/upload")
    public String uploadCsvFile(@RequestParam("file") MultipartFile file,
                               RedirectAttributes redirectAttributes) {
        DataImportService.ImportResult result = processCsvFile(file);
        
        if (result == null) {
            redirectAttributes.addFlashAttribute("error", "请选择要上传的CSV文件");
            return "redirect:/admin/data-import";
        }

        if (result.isSuccess()) {
            redirectAttributes.addFlashAttribute("success", result.getMessage());
            addImportDetails(redirectAttributes, result);
        } else {
            redirectAttributes.addFlashAttribute("error", result.getMessage());
            if (result.getFailureDetails() != null && !result.getFailureDetails().isEmpty()) {
                addImportDetails(redirectAttributes, result);
            }
        }

        return "redirect:/admin/data-import";
    }

    /**
     * API接口：上传并导入CSV数据
     */
    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<?> uploadCsvFileApi(@RequestParam("file") MultipartFile file) {
        DataImportService.ImportResult result = processCsvFile(file);
        
        if (result == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请选择要上传的CSV文件"));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("success", result.isSuccess());
        response.put("message", result.getMessage());
        response.put("results", result.getResults());
        response.put("totalImported", result.getTotalImported());
        response.put("totalFailed", result.getTotalFailed());
        response.put("failureDetails", result.getFailureDetails());

        return result.isSuccess() 
            ? ResponseEntity.ok(response) 
            : ResponseEntity.badRequest().body(response);
    }
    
    /**
     * 处理CSV文件的通用逻辑
     */
    private DataImportService.ImportResult processCsvFile(MultipartFile file) {
        if (file.isEmpty()) {
            return null;
        }

        // 检查文件类型
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("只支持CSV文件格式");
        }

        try {
            logger.info("开始导入CSV文件: {}, 默认行为：只覆盖相同ID的数据", filename);
            DataImportService.ImportResult result = dataImportService.importCsvData(file, false);
            logger.info("导入服务执行完毕，结果: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("CSV文件导入失败", e);
            throw new RuntimeException("导入失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 添加导入详情到重定向属性
     */
    private void addImportDetails(RedirectAttributes redirectAttributes, DataImportService.ImportResult result) {
        Map<String, Object> importDetails = new HashMap<>();
        importDetails.put("results", result.getResults());
        importDetails.put("totalImported", result.getTotalImported());
        importDetails.put("totalFailed", result.getTotalFailed());
        importDetails.put("failureDetails", result.getFailureDetails());
        redirectAttributes.addFlashAttribute("importDetails", importDetails);
        logger.info("设置导入详情: {}", importDetails);
    }

    /**
     * 清空所有数据
     */
    @PostMapping("/clear-all")
    @ResponseBody
    public ResponseEntity<?> clearAllData() {
        try {
            logger.info("开始清空所有数据");
            dataImportService.clearAllDataSafely();
            logger.info("清空所有数据成功");
            return ResponseEntity.ok(Map.of("success", true, "message", "所有数据已成功清空"));
        } catch (Exception e) {
            logger.error("清空所有数据失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "清空所有数据失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 批量作者修复（页面提交）：上传 [内容ID, 作者ID] 的简单CSV，仅在当前作者为空时修复
     */
    @PostMapping("/repair-authors")
    public String repairAuthors(@RequestParam("file") MultipartFile file,
                                RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "请选择要上传的CSV文件");
            return "redirect:/admin/data-import";
        }
        try {
            DataImportService.ImportResult result = dataImportService.repairAuthorsFromSimpleCsv(file);
            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("success", result.getMessage());
                addImportDetails(redirectAttributes, result);
            } else {
                redirectAttributes.addFlashAttribute("error", result.getMessage());
                if (result.getFailureDetails() != null && !result.getFailureDetails().isEmpty()) {
                    addImportDetails(redirectAttributes, result);
                }
            }
        } catch (Exception e) {
            logger.error("作者修复失败", e);
            redirectAttributes.addFlashAttribute("error", "作者修复失败: " + e.getMessage());
        }
        return "redirect:/admin/data-import";
    }

    /**
     * 批量作者修复（API）：上传 [内容ID, 作者ID] 的简单CSV，仅在当前作者为空时修复
     */
    @PostMapping("/api/repair-authors")
    @ResponseBody
    public ResponseEntity<?> repairAuthorsApi(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请选择要上传的CSV文件"));
        }
        try {
            DataImportService.ImportResult result = dataImportService.repairAuthorsFromSimpleCsv(file);
            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("results", result.getResults());
            response.put("totalImported", result.getTotalImported());
            response.put("totalFailed", result.getTotalFailed());
            response.put("failureDetails", result.getFailureDetails());
            return result.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("作者修复失败", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "作者修复失败: " + e.getMessage()));
        }
    }
}
