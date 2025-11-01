package com.philosophy.util;

import com.philosophy.service.DataImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;

@Component
@Profile("test-import")
public class DataImportTester implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataImportTester.class);

    @Autowired
    private DataImportService dataImportService;

    @Override
    public void run(String... args) throws Exception {
        logger.info("开始测试数据导入功能...");

        try {
            // 查找测试CSV文件
            File testFile = new File("test-import-data.csv");
            if (!testFile.exists()) {
                // 尝试从resources目录查找
                testFile = ResourceUtils.getFile("classpath:test-import-data.csv");
            }

            if (!testFile.exists()) {
                logger.error("测试CSV文件不存在: {}", testFile.getAbsolutePath());
                return;
            }

            logger.info("找到测试文件: {}", testFile.getAbsolutePath());

            // 创建MultipartFile模拟对象
            TestMultipartFile multipartFile = new TestMultipartFile(testFile);

            // 执行导入
            DataImportService.ImportResult result = dataImportService.importCsvData(multipartFile);

            logger.info("导入测试完成!");
            logger.info("结果: {}", result.isSuccess() ? "成功" : "失败");
            logger.info("消息: {}", result.getMessage());
            logger.info("成功导入: {} 条", result.getTotalImported());
            logger.info("导入失败: {} 条", result.getTotalFailed());

            // 打印详细结果
            result.getResults().forEach((table, stats) -> {
                logger.info("表 {}: 成功 {} 条, 失败 {} 条", table, stats.getSuccess(), stats.getFailed());
            });

        } catch (Exception e) {
            logger.error("导入测试失败", e);
        }
    }

    // 简单的MultipartFile实现用于测试
    private static class TestMultipartFile implements org.springframework.web.multipart.MultipartFile {

        private final File file;

        public TestMultipartFile(File file) {
            this.file = file;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return file.getName();
        }

        @Override
        public String getContentType() {
            return "text/csv";
        }

        @Override
        public boolean isEmpty() {
            return file.length() == 0;
        }

        @Override
        public long getSize() {
            return file.length();
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(file.toPath());
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            return new FileInputStream(file);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(file.toPath(), dest.toPath());
        }
    }
}
