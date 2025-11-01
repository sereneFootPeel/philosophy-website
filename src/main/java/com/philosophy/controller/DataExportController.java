package com.philosophy.controller;

import com.philosophy.service.DataExportService;
import com.philosophy.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class DataExportController {

    private static final Logger logger = LoggerFactory.getLogger(DataExportController.class);
    private final DataExportService dataExportService;
    private final EmailService emailService;

    public DataExportController(DataExportService dataExportService, EmailService emailService) {
        this.dataExportService = dataExportService;
        this.emailService = emailService;
    }

    @GetMapping("/export/download")
    public ResponseEntity<byte[]> downloadCsvFile() {
        ExportData exportData = generateExportData();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", exportData.filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(exportData.csvBytes);
    }

    @GetMapping("/export/email")
    public String emailCsvFile(Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            ExportData exportData = generateExportData();
            
            String userEmail = "18819028068@163.com"; 
            String subject = "哲学网站数据导出 - " + exportData.timestamp;
            String content = "<html><body><h3>数据导出文件</h3><p>请查收附件中的数据导出文件。</p></body></html>";
            
            emailService.sendReportWithAttachment(userEmail, subject, content, exportData.csvBytes, exportData.filename);
            
            redirectAttributes.addFlashAttribute("success", "Export file has been sent to your email.");

        } catch (Exception e) {
            logger.error("Failed to send email with CSV attachment.", e);
            redirectAttributes.addFlashAttribute("error", "Failed to send email with export file.");
        }

        return "redirect:/admin/dashboard";
    }

    private ExportData generateExportData() {
        String csvData = dataExportService.exportAllDataToCsv();
        byte[] csvBytes = csvData.getBytes(StandardCharsets.UTF_8);
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String filename = "philosophy_data_export_" + timestamp + ".csv";
        
        return new ExportData(csvBytes, filename, timestamp);
    }

    private static class ExportData {
        final byte[] csvBytes;
        final String filename;
        final String timestamp;

        ExportData(byte[] csvBytes, String filename, String timestamp) {
            this.csvBytes = csvBytes;
            this.filename = filename;
            this.timestamp = timestamp;
        }
    }
}
