package com.philosophy.scheduler;

import com.philosophy.service.CsvExportService;
import com.philosophy.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipOutputStream;

@Component
public class DailyReportScheduler {

    private final CsvExportService csvExportService;
    private final EmailService emailService;

    @Value("${app.csv-email.enabled:true}")
    private boolean csvEmailEnabled;

    @Value("${app.daily-report.email.recipient}")
    private String recipientEmail;

    public DailyReportScheduler(CsvExportService csvExportService, EmailService emailService) {
        this.csvExportService = csvExportService;
        this.emailService = emailService;
    }

    @Scheduled(cron = "${app.csv-email.cron:0 0 8 * * ?}")
    public void sendDailyCsvEmail() {
        if (!csvEmailEnabled) {
            return;
        }

        String tempDir = "temp_csv_export_" + System.currentTimeMillis();
        Path tempPath = new File(tempDir).toPath();

        try {
            Files.createDirectories(tempPath);
            csvExportService.exportAllDataToCsv(tempDir);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                csvExportService.zipCsvFiles(tempDir, zos);
            }

            String filename = "export-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip";
            String subject = "哲学网站每日数据导出 - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            String content = "<html><body><h3>每日数据导出</h3><p>请查收附件中的数据压缩包。</p></body></html>";

            emailService.sendReportWithAttachment(recipientEmail, subject, content, baos.toByteArray(), filename);
        } catch (IOException e) {
            // Handle exception, maybe log it
        } finally {
            try {
                csvExportService.cleanUp(tempPath);
            } catch (IOException e) {
                // Handle exception
            }
        }
    }
}