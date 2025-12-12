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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDispositionFormData("attachment", exportData.filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(exportData.csvBytes);
    }

    @GetMapping("/export/download-zip")
    public ResponseEntity<byte[]> downloadZipFile() {
        try {
            ExportData exportData = generateExportData();
            List<String> imageFiles = dataExportService.collectImageFiles();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                // 添加CSV文件到ZIP
                ZipEntry csvEntry = new ZipEntry(exportData.filename);
                zos.putNextEntry(csvEntry);
                zos.write(exportData.csvBytes);
                zos.closeEntry();
                
                // 添加图片文件到ZIP
                for (String imagePath : imageFiles) {
                    Path path = Paths.get(imagePath);
                    if (Files.exists(path) && Files.isRegularFile(path)) {
                        // 使用文件名作为ZIP中的路径
                        String fileName = path.getFileName().toString();
                        ZipEntry imageEntry = new ZipEntry("images/" + fileName);
                        zos.putNextEntry(imageEntry);
                        
                        try (FileInputStream fis = new FileInputStream(path.toFile())) {
                            byte[] buffer = new byte[1024];
                            int len;
                            while ((len = fis.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }
                        }
                        zos.closeEntry();
                    }
                }
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/zip"));
            String zipFilename = exportData.filename.replace(".csv", ".zip");
            headers.setContentDispositionFormData("attachment", zipFilename);
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            logger.error("Failed to create ZIP file", e);
            // 如果ZIP创建失败，返回CSV文件
            return downloadCsvFile();
        }
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
        
        // 添加UTF-8 BOM以便Windows Excel正确识别编码
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csvBytes = csvData.getBytes(StandardCharsets.UTF_8);
        byte[] csvBytesWithBom = new byte[bom.length + csvBytes.length];
        System.arraycopy(bom, 0, csvBytesWithBom, 0, bom.length);
        System.arraycopy(csvBytes, 0, csvBytesWithBom, bom.length, csvBytes.length);
        
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String filename = "philosophy_data_export_" + timestamp + ".csv";
        
        return new ExportData(csvBytesWithBom, filename, timestamp);
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
