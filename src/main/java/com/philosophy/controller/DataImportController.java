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
                               @RequestParam(value = "clearExistingData", defaultValue = "false") boolean clearExistingData,
                               RedirectAttributes redirectAttributes) {

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "请选择要上传的CSV文件");
            return "redirect:/admin/data-import";
        }

        // 检查文件类型
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            redirectAttributes.addFlashAttribute("error", "只支持CSV文件格式");
            return "redirect:/admin/data-import";
        }

        try {
            logger.info("开始导入CSV文件: {}, 清空现有数据: {}", filename, clearExistingData);

            DataImportService.ImportResult result = dataImportService.importCsvData(file, clearExistingData);
            logger.info("导入服务执行完毕，结果: {}", result);

            if (result.isSuccess()) {
                redirectAttributes.addFlashAttribute("success", result.getMessage());
                logger.info("设置成功消息: {}", result.getMessage());

                // 添加详细的导入结果
                Map<String, Object> importDetails = new HashMap<>();
                importDetails.put("results", result.getResults());
                importDetails.put("totalImported", result.getTotalImported());
                importDetails.put("totalFailed", result.getTotalFailed());
                redirectAttributes.addFlashAttribute("importDetails", importDetails);
                logger.info("设置导入详情: {}", importDetails);

            } else {
                redirectAttributes.addFlashAttribute("error", result.getMessage());
                logger.warn("设置失败消息: {}", result.getMessage());
            }

        } catch (Exception e) {
            logger.error("CSV文件导入失败", e);
            redirectAttributes.addFlashAttribute("error", "导入失败: " + e.getMessage());
        }

        return "redirect:/admin/data-import";
    }

    /**
     * API接口：上传并导入CSV数据
     */
    @PostMapping("/api/upload")
    @ResponseBody
    public ResponseEntity<?> uploadCsvFileApi(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "clearExistingData", defaultValue = "false") boolean clearExistingData) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请选择要上传的CSV文件"));
        }

        // 检查文件类型
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "只支持CSV文件格式"));
        }

        try {
            logger.info("API: 开始导入CSV文件: {}, 清空现有数据: {}", filename, clearExistingData);

            DataImportService.ImportResult result = dataImportService.importCsvData(file, clearExistingData);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("results", result.getResults());
            response.put("totalImported", result.getTotalImported());
            response.put("totalFailed", result.getTotalFailed());

            if (result.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            logger.error("API: CSV文件导入失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "导入失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取导入模板
     */
    @GetMapping("/template")
    @ResponseBody
    public ResponseEntity<String> getImportTemplate() {
        String template = """
            用户数据
            ID,用户名,邮箱,密码,名字,姓氏,角色,启用状态,失败登录次数,账户锁定,锁定时间,锁定过期时间,个人资料隐私,评论隐私,内容隐私,管理员登录尝试,点赞数,分配学派ID,IP地址,设备类型,用户代理,头像URL,创建时间,更新时间
            1,testuser,test@example.com,encrypted_password,张,三,USER,1,0,0,,,,0,0,0,0,0,,192.168.1.1,Desktop,Chrome,,2024-01-01T00:00:00,2024-01-01T00:00:00

            学派数据
            ID,名称,英文名称,描述,英文描述,父学派ID,创建者ID,点赞数,创建时间,更新时间
            1,古希腊哲学,Ancient Greek Philosophy,古希腊时期的哲学思想,Philosophical thought from ancient Greece,,,0,2024-01-01T00:00:00,2024-01-01T00:00:00

            哲学家数据
            ID,姓名,英文姓名,生年,卒年,时代,国籍,传记,英文传记,图片URL,创建者ID,点赞数,创建时间,更新时间
            1,苏格拉底,Socrates,-470,-399,古典时期,古希腊人,"古希腊哲学家，西方哲学的奠基人之一",Ancient Greek philosopher,,,0,2024-01-01T00:00:00,2024-01-01T00:00:00

            哲学家学派关联数据
            哲学家ID,学派ID
            1,1

            内容数据
            ID,内容,内容英文,哲学家ID,学派ID,作者ID,标题,排序索引,锁定用户ID,锁定时间,锁定至,历史置顶,点赞数,是否私有,隐私设置者ID,隐私设置时间,状态,是否屏蔽,屏蔽者ID,屏蔽时间,版本,创建时间,更新时间
            1,"苏格拉底的主要思想...","Socrates' main thoughts...",1,1,1,"哲学思想探讨",0,,,,0,0,0,,,0,0,,,1,2024-01-01T00:00:00,2024-01-01T00:00:00

            评论数据
            ID,内容,用户名,内容ID,父评论ID,创建时间
            1,这是一个测试评论,admin,1,,2024-01-01T00:00:00

            点赞数据
            ID,用户ID,实体类型,实体ID,创建时间
            1,1,CONTENT,1,2024-01-01T00:00:00

            用户内容编辑数据
            ID,用户ID,内容,标题,哲学家ID,学派ID,状态,创建时间
            1,1,"编辑后的内容","编辑标题",1,1,PENDING,2024-01-01T00:00:00

            用户屏蔽数据
            ID,屏蔽者ID,被屏蔽者ID,创建时间
            1,1,2,2024-01-01T00:00:00

            版主屏蔽数据
            ID,版主ID,被屏蔽用户ID,学派ID,原因,创建时间
            1,1,2,1,违规行为,2024-01-01T00:00:00

            用户登录信息数据
            ID,用户ID,IP地址,浏览器,操作系统,设备类型,登录时间
            1,1,192.168.1.1,Chrome,Windows,Desktop,2024-01-01T00:00:00

            用户关注数据
            ID,关注者ID,被关注者ID,创建时间
            1,1,2,2024-01-01T00:00:00

            学派翻译数据
            ID,学派ID,语言代码,英文名称,英文描述,创建时间
            1,1,en,Ancient Greek Philosophy,Philosophical thought from ancient Greece,2024-01-01T00:00:00

            内容翻译数据
            ID,内容ID,语言代码,英文内容,创建时间
            1,1,en,"Socrates' main thoughts...",2024-01-01T00:00:00

            哲学家翻译数据
            ID,哲学家ID,语言代码,英文名称,英文传记,创建时间
            1,1,en,Socrates,Ancient Greek philosopher,2024-01-01T00:00:00
            """;

        return ResponseEntity.ok()
                .header("Content-Type", "text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"import-template.csv\"")
                .body(template);
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
                Map<String, Object> details = new HashMap<>();
                details.put("results", result.getResults());
                details.put("totalImported", result.getTotalImported());
                details.put("totalFailed", result.getTotalFailed());
                redirectAttributes.addFlashAttribute("importDetails", details);
            } else {
                redirectAttributes.addFlashAttribute("error", result.getMessage());
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
            return result.isSuccess() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            logger.error("作者修复失败", e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "作者修复失败: " + e.getMessage()));
        }
    }
}
