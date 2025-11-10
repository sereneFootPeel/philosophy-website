package com.philosophy.service;

import com.philosophy.model.*;
import com.philosophy.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class DataImportService {

    private static final Logger logger = LoggerFactory.getLogger(DataImportService.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PhilosopherRepository philosopherRepository;

    @Autowired
    private SchoolRepository schoolRepository;

    @Autowired
    private ContentRepository contentRepository;


    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private UserContentEditRepository userContentEditRepository;

    @Autowired
    private UserBlockRepository userBlockRepository;

    @Autowired
    private ModeratorBlockRepository moderatorBlockRepository;

    @Autowired
    private SchoolTranslationRepository schoolTranslationRepository;

    @Autowired
    private ContentTranslationRepository contentTranslationRepository;

    @Autowired
    private PhilosopherTranslationRepository philosopherTranslationRepository;

    @Autowired
    private UserLoginInfoRepository userLoginInfoRepository;

    @Autowired
    private UserFollowRepository userFollowRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ConcurrentMap<String, Boolean> columnExistenceCache = new ConcurrentHashMap<>();
    private final Set<String> missingColumnWarnings = ConcurrentHashMap.newKeySet();

    public ImportResult importCsvData(MultipartFile file) {
        return importCsvData(file, false);
    }

    public ImportResult importCsvData(MultipartFile file, boolean clearExistingData) {
        ImportResult result = new ImportResult();
        
        try {
            // 如果需要清空现有数据
            if (clearExistingData) {
                logger.info("开始清空现有数据...");
                try {
                    clearAllDataSafely();
                    logger.info("现有数据清空完成");
                } catch (Exception e) {
                    logger.error("清空现有数据失败", e);
                    result.setSuccess(false);
                    result.setMessage("清空现有数据失败: " + e.getMessage());
                    return result;
                }
            }
            
            Map<String, List<String[]>> dataSections = parseCsvFile(file);
            
            // 检查是否解析到任何数据段
            if (dataSections.isEmpty()) {
                result.setSuccess(false);
                result.setMessage("导入失败: CSV文件中没有找到任何数据段。请确保文件格式正确，包含以'数据'结尾的段标题（如'用户数据'、'学派数据'等）");
                logger.warn("CSV文件解析结果为空，可能的原因：1. 文件格式不正确 2. 缺少数据段标题 3. 文件编码问题");
                return result;
            }
            
            logger.info("成功解析到 {} 个数据段: {}", dataSections.size(), dataSections.keySet());
            
            // 检查是否有任何数据段包含实际数据
            boolean hasData = false;
            for (Map.Entry<String, List<String[]>> entry : dataSections.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    hasData = true;
                    logger.info("数据段 '{}' 包含 {} 行数据", entry.getKey(), entry.getValue().size());
                    break;
                }
            }
            
            if (!hasData) {
                result.setSuccess(false);
                StringBuilder emptySections = new StringBuilder();
                for (String section : dataSections.keySet()) {
                    if (emptySections.length() > 0) emptySections.append(", ");
                    emptySections.append(section);
                }
                result.setMessage("导入失败: 虽然找到了 " + dataSections.size() + " 个数据段（" + emptySections + "），但所有数据段都是空的。请检查CSV文件是否包含实际数据行。");
                logger.warn("所有数据段都是空的。找到的数据段: {}", dataSections.keySet());
                return result;
            }

            // 按依赖顺序导入数据，将基础数据和关联更新放在同一个事务中
            importUsersInTransaction(result, dataSections.get("用户数据"));

            // 学派数据段：兼容“流派数据”等变体
            List<String[]> schoolData = dataSections.get("学派数据");
            if (schoolData == null) {
                schoolData = findSectionByKeywords(dataSections, "学派", "数据");
            }
            if (schoolData == null) {
                schoolData = dataSections.get("流派数据");
            }
            if (schoolData == null) {
                schoolData = findSectionByKeywords(dataSections, "流派", "数据");
            }
            importSchoolsInTransaction(result, schoolData);
            importPhilosophersInTransaction(result, dataSections.get("哲学家数据"));
            
            // 导入哲学家-学派关联（在哲学家和学派导入后）——兼容多种标题/同义词
            List<String[]> assocData = dataSections.get("哲学家学派关联数据");
            if (assocData == null) {
                assocData = dataSections.get("哲学家-学派关联数据");
            }
            if (assocData == null) {
                assocData = findSectionByKeywords(dataSections, "哲学家", "学派", "关联");
            }
            if (assocData == null) {
                assocData = dataSections.get("哲学家流派关联数据");
            }
            if (assocData == null) {
                assocData = findSectionByKeywords(dataSections, "哲学家", "流派", "关联");
            }
            importPhilosopherSchoolAssociationsInTransaction(result, assocData);
            
            // 尝试查找内容数据段，支持可能的变体
            List<String[]> contentData = dataSections.get("内容数据");
            if (contentData == null) {
                // 尝试查找可能的变体
                contentData = dataSections.get("内容数据？");
                if (contentData == null) {
                    // 查找所有包含"内容"的段标题
                    for (String key : dataSections.keySet()) {
                        if (key.contains("内容") && key.contains("数据")) {
                            logger.warn("找到可能的 content 数据段变体: {}", key);
                            contentData = dataSections.get(key);
                            break;
                        }
                    }
                }
            }
            if (contentData == null) {
                logger.warn("未找到'内容数据'段，跳过了内容导入。可用的数据段: {}", dataSections.keySet());
                result.addResult("内容", 0, 0);
            } else {
                importContentsInTransaction(result, contentData);
                
                // 在内容导入完成后，单独处理内容关联
                updateContentAssociationsInTransaction(result, contentData);
            }
            
            // 导入其他数据
            importCommentsInTransaction(result, dataSections.get("评论数据"));
            importLikesInTransaction(result, dataSections.get("点赞数据"));
            importUserContentEditsInTransaction(result, dataSections.get("用户内容编辑数据"));
            importUserBlocksInTransaction(result, dataSections.get("用户屏蔽数据"));
            importModeratorBlocksInTransaction(result, dataSections.get("版主屏蔽数据"));
            importUserLoginInfoInTransaction(result, dataSections.get("用户登录信息数据"));
            importUserFollowsInTransaction(result, dataSections.get("用户关注数据"));
            
            // 导入翻译数据 - 支持多种段标题格式（含“流派”同义词）
            List<String[]> schoolTranslationData = findSectionByKeywords(dataSections, "学派", "翻译");
            if (schoolTranslationData == null) {
                schoolTranslationData = dataSections.get("学派翻译数据");
            }
            if (schoolTranslationData == null) {
                schoolTranslationData = findSectionByKeywords(dataSections, "流派", "翻译");
            }
            if (schoolTranslationData == null) {
                schoolTranslationData = dataSections.get("流派翻译数据");
            }
            importSchoolTranslationsInTransaction(result, schoolTranslationData);
            
            List<String[]> contentTranslationData = findSectionByKeywords(dataSections, "内容", "翻译");
            if (contentTranslationData == null) {
                contentTranslationData = dataSections.get("内容翻译数据");
            }
            importContentTranslationsInTransaction(result, contentTranslationData);
            
            List<String[]> philosopherTranslationData = findSectionByKeywords(dataSections, "哲学家", "翻译");
            if (philosopherTranslationData == null) {
                philosopherTranslationData = dataSections.get("哲学家翻译数据");
            }
            importPhilosopherTranslationsInTransaction(result, philosopherTranslationData);

            // 即使有部分失败，只要不是全部失败，就认为导入成功
            if (result.getTotalImported() > 0) {
                result.setSuccess(true);
                result.setMessage("数据导入完成！总共导入: " +
                    result.getTotalImported() + " 条记录，失败: " + result.getTotalFailed() + " 条");
            } else {
                result.setSuccess(false);
                // 构建详细的诊断信息
                StringBuilder diagnosticMsg = new StringBuilder("导入失败: 没有成功导入任何数据。\n\n");
                diagnosticMsg.append("诊断信息:\n");
                diagnosticMsg.append("- 解析到的数据段: ").append(dataSections.size()).append(" 个\n");
                
                for (Map.Entry<String, List<String[]>> entry : dataSections.entrySet()) {
                    String sectionName = entry.getKey();
                    List<String[]> sectionData = entry.getValue();
                    int dataCount = (sectionData != null) ? sectionData.size() : 0;
                    diagnosticMsg.append("- ").append(sectionName).append(": ").append(dataCount).append(" 行数据\n");
                    
                    // 显示前几行数据示例（用于调试）
                    if (dataCount > 0 && dataCount <= 3 && sectionData != null) {
                        for (int i = 0; i < dataCount; i++) {
                            String[] row = sectionData.get(i);
                            if (row != null) {
                                diagnosticMsg.append("  示例行 ").append(i + 1).append(": ").append(row.length).append(" 个字段\n");
                            }
                        }
                    }
                }
                
                diagnosticMsg.append("\n可能的原因:\n");
                diagnosticMsg.append("1. 数据格式不正确（字段数量不足或格式错误）\n");
                diagnosticMsg.append("2. 数据验证失败（如必需字段为空、ID格式错误等）\n");
                diagnosticMsg.append("3. 数据库约束冲突（如唯一性约束、外键约束等）\n");
                diagnosticMsg.append("4. 请检查服务器日志获取更详细的错误信息");
                
                result.setMessage(diagnosticMsg.toString());
                logger.warn("导入失败详情: {}", diagnosticMsg.toString());
            }

        } catch (Exception e) {
            logger.error("CSV导入过程中发生异常", e);
            result.setSuccess(false);
            result.setMessage("导入失败: " + e.getMessage());
            // 记录详细的错误信息
            logger.error("导入失败详情 - 已导入: {}, 失败: {}, 错误类型: {}", 
                        result.getTotalImported(), result.getTotalFailed(), e.getClass().getSimpleName());
            // 不重新抛出异常，让事务正常提交
        }

        return result;
    }

    /**
     * 安全清空所有数据表，使用TransactionTemplate确保事务正确执行
     */
    public void clearAllDataSafely() {
        logger.info("开始安全清空所有数据表...");
        
        transactionTemplate.execute(status -> {
            try {
                // 禁用外键检查以避免约束问题
                entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
                logger.info("已禁用外键检查");
                
                // 使用原生SQL按正确顺序删除，避免外键约束问题
                clearTablesInOrder();
                
                // 重新启用外键检查
                entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
                logger.info("已重新启用外键检查");
                
                logger.info("所有数据表清空完成");
                return null;
                
            } catch (Exception e) {
                logger.error("清空数据时发生错误", e);
                try {
                    entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
                    logger.info("已重新启用外键检查");
                } catch (Exception ex) {
                    logger.error("重新启用外键检查失败", ex);
                }
                throw new RuntimeException("清空数据失败: " + e.getMessage(), e);
            }
        });
    }

    /**
     * 按顺序清空所有表
     */
    private void clearTablesInOrder() {
        // 1. 删除所有依赖表（没有外键约束的表）
        safeDeleteTable("user_login_info", "用户登录信息表");
        safeDeleteTable("moderator_blocks", "版主屏蔽表");
        safeDeleteTable("user_blocks", "用户屏蔽表");
        safeDeleteTable("user_content_edits", "用户内容编辑表");
        safeDeleteTable("likes", "点赞表");
        
        // 2. 删除用户关注表（依赖用户表）
        safeDeleteTable("user_follows", "用户关注表");
        
        // 3. 删除评论表（依赖用户和内容表）
        safeDeleteTable("comments", "评论表");
        
        // 4. 删除翻译表
        safeDeleteTable("contents_translation", "内容翻译表");
        safeDeleteTable("philosophers_translation", "哲学家翻译表");
        safeDeleteTable("schools_translation", "学派翻译表");
        
            // 5. 删除哲学家-学派关联表
            safeDeleteTable("philosopher_school", "哲学家-学派关联表");
            
            // 6. 删除内容表（有外键约束）
            safeDeleteTable("contents", "内容表");
        
        // 8. 删除哲学家表
        safeDeleteTable("philosophers", "哲学家表");
        
        // 9. 删除学派表
        safeDeleteTable("schools", "学派表");
        
        // 10. 最后删除用户表
        safeDeleteTable("users", "用户表");
    }

    /**
     * 安全删除表，如果表不存在也不会报错
     */
    private void safeDeleteTable(String tableName, String description) {
        try {
            entityManager.createNativeQuery("DELETE FROM " + tableName).executeUpdate();
            logger.info("清空{}", description);
        } catch (Exception e) {
            logger.warn("清空{}失败，可能表不存在: {}", description, e.getMessage());
        }
    }

    @Transactional
    public void clearAllDataInTransaction() {
        logger.info("开始清空所有数据表...");
        
        try {
            // 禁用外键检查以避免约束问题
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 0").executeUpdate();
            logger.info("已禁用外键检查");
            
            // 使用原生SQL按正确顺序删除，避免外键约束问题
            // 按照外键依赖关系的逆序删除
            
            // 1. 删除所有依赖表（没有外键约束的表）
            
            try {
                entityManager.createNativeQuery("DELETE FROM user_login_info").executeUpdate();
                logger.info("清空用户登录信息表");
            } catch (Exception e) {
                logger.warn("清空用户登录信息表失败，可能表不存在: {}", e.getMessage());
            }
            
            try {
                entityManager.createNativeQuery("DELETE FROM moderator_blocks").executeUpdate();
                logger.info("清空版主屏蔽表");
            } catch (Exception e) {
                logger.warn("清空版主屏蔽表失败，可能表不存在: {}", e.getMessage());
            }
            
            try {
                entityManager.createNativeQuery("DELETE FROM user_blocks").executeUpdate();
                logger.info("清空用户屏蔽表");
            } catch (Exception e) {
                logger.warn("清空用户屏蔽表失败，可能表不存在: {}", e.getMessage());
            }
            
            try {
                entityManager.createNativeQuery("DELETE FROM user_content_edits").executeUpdate();
                logger.info("清空用户内容编辑表");
            } catch (Exception e) {
                logger.warn("清空用户内容编辑表失败，可能表不存在: {}", e.getMessage());
            }
            
            try {
                entityManager.createNativeQuery("DELETE FROM likes").executeUpdate();
                logger.info("清空点赞表");
            } catch (Exception e) {
                logger.warn("清空点赞表失败，可能表不存在: {}", e.getMessage());
            }
            
            // 2. 删除用户关注表（依赖用户表）
            try {
                entityManager.createNativeQuery("DELETE FROM user_follows").executeUpdate();
                logger.info("清空用户关注表");
            } catch (Exception e) {
                logger.warn("清空用户关注表失败，可能表不存在: {}", e.getMessage());
            }
            
            // 3. 删除评论表（依赖用户和内容表）
            try {
                entityManager.createNativeQuery("DELETE FROM comments").executeUpdate();
                logger.info("清空评论表");
            } catch (Exception e) {
                logger.warn("清空评论表失败，可能表不存在: {}", e.getMessage());
            }
            
            // 4. 删除翻译表
            try {
                entityManager.createNativeQuery("DELETE FROM contents_translation").executeUpdate();
                logger.info("清空内容翻译表");
            } catch (Exception e) {
                logger.warn("清空内容翻译表失败，可能表不存在: {}", e.getMessage());
            }
            
            try {
                entityManager.createNativeQuery("DELETE FROM philosophers_translation").executeUpdate();
                logger.info("清空哲学家翻译表");
            } catch (Exception e) {
                logger.warn("清空哲学家翻译表失败，可能表不存在: {}", e.getMessage());
            }
            
            try {
                entityManager.createNativeQuery("DELETE FROM schools_translation").executeUpdate();
                logger.info("清空学派翻译表");
            } catch (Exception e) {
                logger.warn("清空学派翻译表失败，可能表不存在: {}", e.getMessage());
            }
            
            // 5. 删除哲学家-学派关联表
            try {
                entityManager.createNativeQuery("DELETE FROM philosopher_school").executeUpdate();
                logger.info("清空哲学家-学派关联表");
            } catch (Exception e) {
                logger.warn("清空哲学家-学派关联表失败，可能表不存在: {}", e.getMessage());
            }
            
            // 6. 删除内容表（有外键约束）
            try {
                entityManager.createNativeQuery("DELETE FROM contents").executeUpdate();
                logger.info("清空内容表");
            } catch (Exception e) {
                logger.warn("清空内容表失败，可能表不存在: {}", e.getMessage());
            }
            
            // 8. 删除哲学家表
            try {
                entityManager.createNativeQuery("DELETE FROM philosophers").executeUpdate();
                logger.info("清空哲学家表");
            } catch (Exception e) {
                logger.warn("清空哲学家表失败，可能表不存在: {}", e.getMessage());
            }
            
            // 9. 删除学派表
            try {
                entityManager.createNativeQuery("DELETE FROM schools").executeUpdate();
                logger.info("清空学派表");
            } catch (Exception e) {
                logger.warn("清空学派表失败，可能表不存在: {}", e.getMessage());
            }
            
            // 10. 最后删除用户表
            try {
                entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
                logger.info("清空用户表");
            } catch (Exception e) {
                logger.warn("清空用户表失败，可能表不存在: {}", e.getMessage());
            }
            
            // 重新启用外键检查
            entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
            logger.info("已重新启用外键检查");
            
            logger.info("所有数据表清空完成");
            
        } catch (Exception e) {
            logger.error("清空数据时发生错误", e);
            try {
                entityManager.createNativeQuery("SET FOREIGN_KEY_CHECKS = 1").executeUpdate();
                logger.info("已重新启用外键检查");
            } catch (Exception ex) {
                logger.error("重新启用外键检查失败", ex);
            }
            throw new RuntimeException("清空数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清空所有数据表
     * 注意：此方法会删除所有数据，请谨慎使用
     */
    @Transactional
    public void clearAllData() {
        logger.info("开始清空所有数据表...");
        
        try {
            // 使用原生SQL按正确顺序删除，避免外键约束问题
            // 按照外键依赖关系的逆序删除
            
            // 1. 删除所有依赖表（没有外键约束的表）
            entityManager.createNativeQuery("DELETE FROM user_login_info").executeUpdate();
            logger.info("清空用户登录信息表");
            
            entityManager.createNativeQuery("DELETE FROM moderator_blocks").executeUpdate();
            logger.info("清空版主屏蔽表");
            
            entityManager.createNativeQuery("DELETE FROM user_blocks").executeUpdate();
            logger.info("清空用户屏蔽表");
            
            entityManager.createNativeQuery("DELETE FROM user_content_edits").executeUpdate();
            logger.info("清空用户内容编辑表");
            
            entityManager.createNativeQuery("DELETE FROM likes").executeUpdate();
            logger.info("清空点赞表");
            
            // 2. 删除用户关注表（依赖用户表）
            entityManager.createNativeQuery("DELETE FROM user_follows").executeUpdate();
            logger.info("清空用户关注表");
            
            // 3. 删除评论表（依赖用户和内容表）
            entityManager.createNativeQuery("DELETE FROM comments").executeUpdate();
            logger.info("清空评论表");
            
            // 4. 删除翻译表
            entityManager.createNativeQuery("DELETE FROM contents_translation").executeUpdate();
            logger.info("清空内容翻译表");
            
            entityManager.createNativeQuery("DELETE FROM philosophers_translation").executeUpdate();
            logger.info("清空哲学家翻译表");
            
            entityManager.createNativeQuery("DELETE FROM schools_translation").executeUpdate();
            logger.info("清空学派翻译表");
            
            // 5. 删除哲学家-学派关联表
            entityManager.createNativeQuery("DELETE FROM philosopher_school").executeUpdate();
            logger.info("清空哲学家-学派关联表");
            
            // 6. 删除内容表（有外键约束）
            entityManager.createNativeQuery("DELETE FROM contents").executeUpdate();
            logger.info("清空内容表");
            
            // 8. 删除哲学家表
            entityManager.createNativeQuery("DELETE FROM philosophers").executeUpdate();
            logger.info("清空哲学家表");
            
            // 9. 删除学派表
            entityManager.createNativeQuery("DELETE FROM schools").executeUpdate();
            logger.info("清空学派表");
            
            // 10. 最后删除用户表
            entityManager.createNativeQuery("DELETE FROM users").executeUpdate();
            logger.info("清空用户表");
            
            logger.info("所有数据表清空完成");
            
        } catch (Exception e) {
            logger.error("清空数据时发生错误", e);
            throw new RuntimeException("清空数据失败: " + e.getMessage(), e);
        }
    }

    private Map<String, List<String[]>> parseCsvFile(MultipartFile file) {
        Map<String, List<String[]>> sections = new HashMap<>();

        try {
            // 读取文件开头以检测并跳过UTF-8 BOM
            java.io.InputStream inputStream = file.getInputStream();
            byte[] bom = new byte[3];
            int bytesRead = inputStream.read(bom);
            
            // 检查是否是UTF-8 BOM (EF BB BF)
            boolean hasBom = (bytesRead == 3 && bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF);
            
            // 如果没有BOM，需要将读取的字节放回去
            if (!hasBom && bytesRead > 0) {
                // 使用PushbackInputStream来放回已读取的字节
                java.io.PushbackInputStream pushbackStream = new java.io.PushbackInputStream(inputStream, 3);
                pushbackStream.unread(bom, 0, bytesRead);
                inputStream = pushbackStream;
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                String line;
                String currentSection = null;
                List<String[]> currentData = null;
                int lineNumber = 0;

                logger.info("开始解析CSV文件: {}, 大小: {} bytes, 检测到UTF-8 BOM: {}", 
                           file.getOriginalFilename(), file.getSize(), hasBom);

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                logger.debug("解析第{}行: [{}]", lineNumber, line);

                // 检测新的数据段 - 更宽松的匹配条件，支持问号结尾
                if ((line.endsWith("数据") || line.endsWith("数据？")) && !line.contains(",")) {
                    if (currentSection != null && currentData != null) {
                        sections.put(currentSection, currentData);
                        logger.info("完成数据段: {}, 数据行数: {}", currentSection, currentData.size());
                    }
                    currentSection = line;
                    currentData = new ArrayList<>();
                    logger.info("开始解析数据段: [{}]", currentSection);
                    continue;
                }
                
                // 如果还没有找到数据段，跳过所有行
                if (currentData == null) {
                    logger.debug("跳过行（未找到数据段）: [{}]", line);
                    continue;
                }
                
                // 跳过标题行 - 更精确的匹配：标题行应该以"ID,"开头
                if (line.startsWith("ID,") || line.startsWith("ID, ")) {
                    logger.debug("跳过标题行: [{}]", line);
                    continue;
                }
                
                // 解析数据行
                if (currentSection != null && currentData != null && !line.isEmpty()) {
                    String[] fields = parseCsvLine(line);
                    if (fields.length > 0) {
                        // 检查第一个字段是否是"ID"（标题行的另一种格式）
                        if (fields.length > 0 && "ID".equals(fields[0].trim())) {
                            logger.debug("跳过标题行（第一个字段是ID）: [{}]", line);
                            continue;
                        }
                        currentData.add(fields);
                        logger.debug("添加数据行到段{}: [{}] -> {} 个字段", currentSection, line, fields.length);
                    } else {
                        logger.warn("解析数据行失败，字段为空: [{}]", line);
                    }
                } else {
                    logger.debug("跳过行: [{}] (currentSection={}, currentData={}, isEmpty={})", 
                               line, currentSection, currentData != null, line.isEmpty());
                }
            }

                // 保存最后一个数据段
                if (currentSection != null && currentData != null) {
                    sections.put(currentSection, currentData);
                    logger.info("完成数据段: {}, 数据行数: {}", currentSection, currentData.size());
                }

                logger.info("CSV解析完成，共解析到{}个数据段: {}", sections.size(), sections.keySet());
                
                // 详细记录每个数据段的内容
                for (Map.Entry<String, List<String[]>> entry : sections.entrySet()) {
                    logger.info("数据段 '{}' 包含 {} 行数据", entry.getKey(), entry.getValue().size());
                    if (entry.getValue().size() > 0) {
                        logger.debug("数据段 '{}' 第一行示例: {}", entry.getKey(), Arrays.toString(entry.getValue().get(0)));
                    }
                }
            }

        } catch (IOException e) {
            logger.error("解析CSV文件失败", e);
            throw new RuntimeException("解析CSV文件失败: " + e.getMessage());
        }

        return sections;
    }

    /**
     * 根据关键词查找数据段（支持模糊匹配）
     * @param dataSections 所有数据段的映射
     * @param keywords 关键词数组
     * @return 找到的数据段，如果未找到则返回null
     */
    private List<String[]> findSectionByKeywords(Map<String, List<String[]>> dataSections, String... keywords) {
        if (dataSections == null || keywords == null || keywords.length == 0) {
            return null;
        }
        
        for (String sectionName : dataSections.keySet()) {
            boolean allKeywordsFound = true;
            for (String keyword : keywords) {
                if (!sectionName.contains(keyword)) {
                    allKeywordsFound = false;
                    break;
                }
            }
            if (allKeywordsFound) {
                logger.info("找到匹配的数据段: {} (关键词: {})", sectionName, Arrays.toString(keywords));
                return dataSections.get(sectionName);
            }
        }
        
        logger.debug("未找到包含关键词 {} 的数据段", Arrays.toString(keywords));
        return null;
    }

    private String[] parseCsvLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return new String[0];
        }
        
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // 转义的引号
                    field.append('"');
                    i++; // 跳过下一个引号
                } else {
                    // 切换引号状态
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // 字段结束
                fields.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }

        // 添加最后一个字段
        fields.add(field.toString().trim());

        String[] result = fields.toArray(new String[0]);
        logger.debug("解析CSV行: [{}] -> {} 个字段: {}", line, result.length, Arrays.toString(result));
        return result;
    }

    private void recordFailureDetail(ImportResult result, String sectionName, int rowIndex, String[] fields, String reason, Exception e) {
        if (result == null || sectionName == null) {
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append("第").append(rowIndex + 1).append("行");
        if (fields != null && fields.length > 0) {
            String idValue = fields[0];
            if (idValue != null && !idValue.isBlank() && !"null".equalsIgnoreCase(idValue.trim())) {
                message.append(" (ID=").append(idValue.trim()).append(")");
            }
        }
        if (reason != null && !reason.isBlank()) {
            message.append(": ").append(reason.trim());
        }
        if (e != null && e.getMessage() != null && !e.getMessage().isBlank()) {
            message.append(" - ").append(e.getMessage().trim());
        }
        result.addFailureDetail(sectionName, message.toString());
    }

    public void importUsersInTransaction(ImportResult result, List<String[]> data) {
        try {
            transactionTemplate.execute(status -> {
                importUsers(result, data);
                return null;
            });
        } catch (Exception e) {
            logger.error("用户导入事务失败", e);
            // 不要重新抛出异常，避免影响整体导入流程
        }
    }

    private void importUsers(ImportResult result, List<String[]> data) {
        if (data == null || data.isEmpty()) {
            logger.info("用户数据段为空，跳过导入");
            result.addResult("用户", 0, 0);
            return;
        }

        logger.info("开始导入用户数据，共 {} 条", data.size());
        final String sectionName = "用户";
        int success = 0, failed = 0;

        final String usersTable = "users";
        final String usersContext = "用户导入";
        ensureColumnExists(usersTable, "id", true, usersContext);
        ensureColumnExists(usersTable, "username", true, usersContext);
        ensureColumnExists(usersTable, "email", true, usersContext);
        ensureColumnExists(usersTable, "password", true, usersContext);
        ensureColumnExists(usersTable, "role", true, usersContext);
        ensureColumnExists(usersTable, "enabled", true, usersContext);

        for (int i = 0; i < data.size(); i++) {
            String[] fields = data.get(i);
            try {
                // 检查字段数量，支持完整字段导入
                if (fields.length < 5) {
                    logger.warn("用户数据第 {} 行被跳过: 字段数量不足 (需要至少5个字段，实际: {})", i + 1, fields.length);
                    failed++;
                    recordFailureDetail(result, sectionName, i, fields,
                        "字段数量不足，至少需要 5 列，实际为 " + fields.length + " 列",
                        null);
                    continue;
                }

                Long userId = Long.parseLong(fields[0]);
                
                // 检查现有用户，以便保留版主的流派分配（如果CSV中未提供）
                User existingUser = userRepository.findById(userId).orElse(null);
                Long existingAssignedSchoolId = (existingUser != null && "MODERATOR".equals(existingUser.getRole())) 
                    ? existingUser.getAssignedSchoolId() : null;
                
                // 直接创建新用户，不查找现有用户
                User user = new User();
                user.setId(userId); // 设置CSV中的ID
                user.setUsername(fields[1]);
                user.setEmail(fields[2]);
                
                // 如果导出的CSV包含密码字段，使用原密码；否则使用默认密码
                if (fields.length > 3 && !fields[3].isEmpty() && !fields[3].equals("null")) {
                    // 如果从CSV导入密码，应该已经是加密的，直接使用
                    user.setPassword(fields[3]);
                } else {
                    // 设置默认密码
                    user.setPassword(passwordEncoder.encode("123456"));
                }
                
                // 处理姓名字段
                if (fields.length > 4 && !fields[4].isEmpty() && !fields[4].equals("null")) {
                    user.setFirstName(fields[4]);
                }
                if (fields.length > 5 && !fields[5].isEmpty() && !fields[5].equals("null")) {
                    user.setLastName(fields[5]);
                }
                if (fields.length > 6 && !fields[6].isEmpty() && !fields[6].equals("null")) {
                    user.setRole(fields[6]);
                } else {
                    user.setRole("USER"); // 默认角色
                }
                
                // 处理启用状态
                if (fields.length > 7 && !fields[7].isEmpty() && !fields[7].equals("null")) {
                    user.setEnabled("1".equals(fields[7]) || "true".equalsIgnoreCase(fields[7]));
                } else {
                    user.setEnabled(true);
                }
                
                // 处理失败登录次数
                if (fields.length > 8 && !fields[8].isEmpty() && !fields[8].equals("null")) {
                    try {
                        user.setFailedLoginAttempts(Integer.parseInt(fields[8]));
                    } catch (NumberFormatException e) {
                        user.setFailedLoginAttempts(0);
                    }
                } else {
                    user.setFailedLoginAttempts(0);
                }
                
                // 处理账户锁定状态 (字段9)
                if (fields.length > 9 && !fields[9].isEmpty() && !fields[9].equals("null")) {
                    user.setAccountLocked("1".equals(fields[9]) || "true".equalsIgnoreCase(fields[9]));
                } else {
                    user.setAccountLocked(false);
                }
                
                // 处理锁定时间 (字段10)
                if (fields.length > 10 && !fields[10].isEmpty() && !fields[10].equals("null")) {
                    try {
                        user.setLockTime(LocalDateTime.parse(fields[10], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    } catch (Exception e) {
                        logger.warn("用户ID {}: 锁定时间格式错误: {}", userId, fields[10]);
                    }
                }
                
                // 处理锁定过期时间 (字段11)
                if (fields.length > 11 && !fields[11].isEmpty() && !fields[11].equals("null")) {
                    try {
                        user.setLockExpireTime(LocalDateTime.parse(fields[11], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    } catch (Exception e) {
                        logger.warn("用户ID {}: 锁定过期时间格式错误: {}", userId, fields[11]);
                    }
                }

                // 处理隐私设置 (字段12-14: 个人资料隐私,评论隐私,内容隐私)
                if (fields.length > 12 && !fields[12].isEmpty() && !fields[12].equals("null")) {
                    user.setProfilePrivate("1".equals(fields[12]) || "true".equalsIgnoreCase(fields[12]));
                } else {
                    user.setProfilePrivate(false);
                }
                if (fields.length > 13 && !fields[13].isEmpty() && !fields[13].equals("null")) {
                    user.setCommentsPrivate("1".equals(fields[13]) || "true".equalsIgnoreCase(fields[13]));
                } else {
                    user.setCommentsPrivate(false);
                }
                if (fields.length > 14 && !fields[14].isEmpty() && !fields[14].equals("null")) {
                    user.setContentsPrivate("1".equals(fields[14]) || "true".equalsIgnoreCase(fields[14]));
                } else {
                    user.setContentsPrivate(false);
                }
                
                // 处理管理员登录尝试次数 (字段15)
                if (fields.length > 15 && !fields[15].isEmpty() && !fields[15].equals("null")) {
                    try {
                        user.setAdminLoginAttempts(Integer.parseInt(fields[15]));
                    } catch (NumberFormatException e) {
                        user.setAdminLoginAttempts(0);
                    }
                } else {
                    user.setAdminLoginAttempts(0);
                }
                
                // 处理点赞数 (字段16)
                if (fields.length > 16 && !fields[16].isEmpty() && !fields[16].equals("null")) {
                    try {
                        user.setLikeCount(Integer.parseInt(fields[16]));
                    } catch (NumberFormatException e) {
                        user.setLikeCount(0);
                    }
                } else {
                    user.setLikeCount(0);
                }
                
                // 处理分配学派ID (字段17)
                if (fields.length > 17 && !fields[17].isEmpty() && !fields[17].equals("null")) {
                    try {
                        user.setAssignedSchoolId(Long.parseLong(fields[17]));
                    } catch (NumberFormatException e) {
                        logger.warn("用户ID {}: 分配学派ID格式错误: {}", userId, fields[17]);
                        user.setAssignedSchoolId(null);
                    }
                } else {
                    // 如果字段为空或不存在，对于版主用户，尝试保留现有的流派分配
                    // 这样可以避免重新导入时丢失版主的流派分配
                    if (existingAssignedSchoolId != null && "MODERATOR".equals(user.getRole())) {
                        user.setAssignedSchoolId(existingAssignedSchoolId);
                        logger.debug("用户ID {}: CSV中未提供流派分配，保留现有流派分配: {}", userId, existingAssignedSchoolId);
                    } else {
                        user.setAssignedSchoolId(null);
                    }
                }
                
                // 处理IP地址和设备信息 (字段18-20: IP地址,设备类型,用户代理)
                if (fields.length > 18 && !fields[18].isEmpty() && !fields[18].equals("null")) {
                    user.setIpAddress(fields[18]);
                }
                if (fields.length > 19 && !fields[19].isEmpty() && !fields[19].equals("null")) {
                    user.setDeviceType(fields[19]);
                }
                if (fields.length > 20 && !fields[20].isEmpty() && !fields[20].equals("null")) {
                    user.setUserAgent(fields[20]);
                }
                
                // 处理头像URL (字段21)
                if (fields.length > 21 && !fields[21].isEmpty() && !fields[21].equals("null")) {
                    user.setAvatarUrl(fields[21]);
                }

                // 处理语言设置 (字段22) - 限制长度为10个字符
                if (fields.length > 22 && !fields[22].isEmpty() && !fields[22].equals("null")) {
                    String language = fields[22];
                    // 截断到10个字符（数据库字段限制）
                    if (language.length() > 10) {
                        language = language.substring(0, 10);
                        logger.warn("用户ID {} 的language字段过长，已截断为: {}", userId, language);
                    }
                    user.setLanguage(language);
                } else {
                    user.setLanguage("zh"); // 默认值
                }

                // 处理主题设置 (字段23)
                if (fields.length > 23 && !fields[23].isEmpty() && !fields[23].equals("null")) {
                    user.setTheme(fields[23]);
                } else {
                    user.setTheme("midnight"); // 默认值
                }

                // 解析创建时间 (字段24)
                if (fields.length > 24 && !fields[24].equals("未知时间") && !fields[24].isEmpty() && 
                    !fields[24].equals("null")) {
                    try {
                        user.setCreatedAt(LocalDateTime.parse(fields[24], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    } catch (Exception e) {
                        logger.warn("用户ID {}: 创建时间格式错误: {}", userId, fields[24]);
                    }
                }
                
                // 解析更新时间 (字段25)
                if (fields.length > 25 && !fields[25].equals("未知时间") && !fields[25].isEmpty() && 
                    !fields[25].equals("null")) {
                    try {
                        user.setUpdatedAt(LocalDateTime.parse(fields[25], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    } catch (Exception e) {
                        logger.warn("用户ID {}: 更新时间格式错误: {}", userId, fields[25]);
                    }
                }

                // 使用原生SQL执行清理并插入，兼容线上已有账号导致的唯一约束冲突
                try {
                    handleUserConflictsBeforeInsert(user);
                    int rowsAffected = insertUserRecord(user);
                    if (rowsAffected > 0) {
                        success++;
                        logger.debug("用户保存成功: ID={}, username={}", user.getId(), user.getUsername());
                    } else {
                        logger.warn("用户ID {} 插入失败", user.getId());
                        failed++;
                        recordFailureDetail(result, sectionName, i, fields, "数据库未插入任何记录", null);
                    }

                } catch (Exception saveException) {
                    logger.error("保存用户时发生异常: ID={}, 错误: {}", user.getId(), saveException.getMessage(), saveException);
                    throw saveException;
                }

            } catch (Exception e) {
                failed++;
                logger.warn("导入用户失败: " + Arrays.toString(fields), e);
                recordFailureDetail(result, sectionName, i, fields, "导入失败", e);
            }
        }

        result.addResult(sectionName, success, failed);
        logger.info("用户数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    private void handleUserConflictsBeforeInsert(User user) {
        if (user == null || user.getId() == null) {
            return;
        }

        Long userId = user.getId();
        Set<Long> conflictUserIds = new LinkedHashSet<>();

        userRepository.findByUsername(user.getUsername()).ifPresent(existing -> conflictUserIds.add(existing.getId()));
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            userRepository.findByEmail(user.getEmail()).ifPresent(existing -> conflictUserIds.add(existing.getId()));
        }

        // 避免删除与当前ID相同的记录（后续会统一清理）
        conflictUserIds.remove(userId);

        for (Long conflictId : conflictUserIds) {
            logger.warn("用户导入: CSV中用户ID {} 与现有用户ID {} 在用户名/邮箱上冲突，清理旧数据以避免唯一约束错误", userId, conflictId);
            deleteUserCascade(conflictId);
        }

        // 清理同ID旧数据，确保插入不会违反外键约束
        deleteUserCascade(userId);
    }

    private void deleteUserCascade(Long userId) {
        if (userId == null) {
            return;
        }

        logger.debug("清理用户依赖数据并删除旧记录: userId={}", userId);

        try {
            jakarta.persistence.Query deleteLoginQuery = entityManager.createNativeQuery("DELETE FROM user_login_info WHERE user_id = ?");
            deleteLoginQuery.setParameter(1, userId);
            deleteLoginQuery.executeUpdate();

            jakarta.persistence.Query deleteFollowQuery = entityManager.createNativeQuery("DELETE FROM user_follows WHERE follower_id = ? OR following_id = ?");
            deleteFollowQuery.setParameter(1, userId);
            deleteFollowQuery.setParameter(2, userId);
            deleteFollowQuery.executeUpdate();

            jakarta.persistence.Query deleteBlockQuery = entityManager.createNativeQuery("DELETE FROM user_blocks WHERE blocker_id = ? OR blocked_id = ?");
            deleteBlockQuery.setParameter(1, userId);
            deleteBlockQuery.setParameter(2, userId);
            deleteBlockQuery.executeUpdate();

            jakarta.persistence.Query deleteModeratorBlockQuery = entityManager.createNativeQuery("DELETE FROM moderator_blocks WHERE moderator_id = ? OR blocked_user_id = ?");
            deleteModeratorBlockQuery.setParameter(1, userId);
            deleteModeratorBlockQuery.setParameter(2, userId);
            deleteModeratorBlockQuery.executeUpdate();

            jakarta.persistence.Query deleteUserContentEditQuery = entityManager.createNativeQuery("DELETE FROM user_content_edits WHERE user_id = ?");
            deleteUserContentEditQuery.setParameter(1, userId);
            deleteUserContentEditQuery.executeUpdate();

            jakarta.persistence.Query deleteLikeQuery = entityManager.createNativeQuery("DELETE FROM likes WHERE user_id = ?");
            deleteLikeQuery.setParameter(1, userId);
            deleteLikeQuery.executeUpdate();

            jakarta.persistence.Query deleteCommentQuery = entityManager.createNativeQuery("DELETE FROM comments WHERE user_id = ?");
            deleteCommentQuery.setParameter(1, userId);
            deleteCommentQuery.executeUpdate();

            jakarta.persistence.Query deleteContentTranslationQuery = entityManager.createNativeQuery(
                    "DELETE FROM contents_translation WHERE content_id IN (SELECT id FROM contents WHERE user_id = ?)");
            deleteContentTranslationQuery.setParameter(1, userId);
            deleteContentTranslationQuery.executeUpdate();

            jakarta.persistence.Query deleteContentQuery = entityManager.createNativeQuery("DELETE FROM contents WHERE user_id = ?");
            deleteContentQuery.setParameter(1, userId);
            deleteContentQuery.executeUpdate();

            jakarta.persistence.Query deleteUserQuery = entityManager.createNativeQuery("DELETE FROM users WHERE id = ?");
            deleteUserQuery.setParameter(1, userId);
            deleteUserQuery.executeUpdate();
        } catch (Exception cleanupException) {
            logger.error("清理用户依赖数据时发生异常: userId={}, 错误={}", userId, cleanupException.getMessage(), cleanupException);
            throw cleanupException;
        }
    }

    private boolean tableColumnExists(String tableName, String columnName) {
        String cacheKey = tableName + "." + columnName;
        return columnExistenceCache.computeIfAbsent(cacheKey, key -> {
            try {
                jakarta.persistence.Query query = entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?");
                query.setParameter(1, tableName);
                query.setParameter(2, columnName);
                Number count = (Number) query.getSingleResult();
                return count != null && count.intValue() > 0;
            } catch (Exception e) {
                logger.warn("检测数据表列存在性失败: {}.{}, 错误: {}", tableName, columnName, e.getMessage());
                return false;
            }
        });
    }

    private boolean ensureColumnExists(String tableName, String columnName, boolean required, String context) {
        boolean exists = tableColumnExists(tableName, columnName);
        if (!exists) {
            String key = tableName + "." + columnName;
            if (missingColumnWarnings.add(key)) {
                String message = String.format("检测到表 %s 缺少列 %s，导入上下文: %s", tableName, columnName, context);
                if (required) {
                    logger.error("{}。该列为必需列，相关数据将无法导入。", message);
                } else {
                    logger.warn("{}。该列将在导入过程中被跳过。", message);
                }
            }
            if (required) {
                throw new IllegalStateException(
                        String.format("数据库表 %s 缺少必需列 %s（上下文: %s）", tableName, columnName, context));
            }
        }
        return exists;
    }

    private boolean addUpdateColumn(List<String> assignments, List<Object> params,
                                    String tableName, String columnName, Object value,
                                    String context, boolean required) {
        if (!ensureColumnExists(tableName, columnName, required, context)) {
            return false;
        }
        assignments.add(columnName + " = ?");
        params.add(value);
        return true;
    }

    private boolean addInsertColumn(List<String> columns, List<Object> params,
                                    String tableName, String columnName, Object value,
                                    String context, boolean required) {
        if (!ensureColumnExists(tableName, columnName, required, context)) {
            return false;
        }
        columns.add(columnName);
        params.add(value);
        return true;
    }

    private String resolvePhilosopherBioColumn() {
        if (tableColumnExists("philosophers", "bio")) {
            return "bio";
        }
        if (tableColumnExists("philosophers", "biography")) {
            return "biography";
        }
        return null;
    }

    private int insertUserRecord(User user) {
        final String tableName = "users";
        final String context = "用户导入";

        List<String> columns = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        addInsertColumn(columns, params, tableName, "id", user.getId(), context, true);
        addInsertColumn(columns, params, tableName, "username", user.getUsername(), context, true);
        addInsertColumn(columns, params, tableName, "email", user.getEmail(), context, true);
        addInsertColumn(columns, params, tableName, "password", user.getPassword(), context, true);

        addInsertColumn(columns, params, tableName, "first_name", user.getFirstName(), context, false);
        addInsertColumn(columns, params, tableName, "last_name", user.getLastName(), context, false);

        addInsertColumn(columns, params, tableName, "role", user.getRole(), context, true);
        addInsertColumn(columns, params, tableName, "enabled", user.isEnabled(), context, true);
        addInsertColumn(columns, params, tableName, "account_locked", user.isAccountLocked(), context, false);
        addInsertColumn(columns, params, tableName, "failed_login_attempts", user.getFailedLoginAttempts(), context, false);
        addInsertColumn(columns, params, tableName, "lock_time", user.getLockTime(), context, false);
        addInsertColumn(columns, params, tableName, "lock_expire_time", user.getLockExpireTime(), context, false);

        addInsertColumn(columns, params, tableName, "profile_private", user.isProfilePrivate(), context, false);
        addInsertColumn(columns, params, tableName, "comments_private", user.isCommentsPrivate(), context, false);
        addInsertColumn(columns, params, tableName, "contents_private", user.isContentsPrivate(), context, false);

        addInsertColumn(columns, params, tableName, "admin_login_attempts", user.getAdminLoginAttempts(), context, false);
        addInsertColumn(columns, params, tableName, "like_count", user.getLikeCount(), context, false);
        addInsertColumn(columns, params, tableName, "assigned_school_id", user.getAssignedSchoolId(), context, false);

        addInsertColumn(columns, params, tableName, "ip_address", user.getIpAddress(), context, false);
        addInsertColumn(columns, params, tableName, "device_type", user.getDeviceType(), context, false);
        addInsertColumn(columns, params, tableName, "user_agent", user.getUserAgent(), context, false);
        addInsertColumn(columns, params, tableName, "avatar_url", user.getAvatarUrl(), context, false);

        addInsertColumn(columns, params, tableName, "language", user.getLanguage(), context, false);
        addInsertColumn(columns, params, tableName, "theme", user.getTheme(), context, false);
        addInsertColumn(columns, params, tableName, "created_at", user.getCreatedAt(), context, false);
        addInsertColumn(columns, params, tableName, "updated_at", user.getUpdatedAt(), context, false);

        String placeholders = String.join(", ", Collections.nCopies(columns.size(), "?"));
        String sql = "INSERT INTO " + tableName + " (" + String.join(", ", columns) + ") VALUES (" + placeholders + ")";

        jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }

        return query.executeUpdate();
    }

    public void importSchoolsInTransaction(ImportResult result, List<String[]> data) {
        try {
            transactionTemplate.execute(status -> {
                importSchools(result, data);
                return null;
            });
        } catch (Exception e) {
            logger.error("学派导入事务失败", e);
            // 不要重新抛出异常，避免影响整体导入流程
        }
    }

    private void importSchools(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入学派数据，共 {} 条", data.size());
        final String sectionName = "学派";
        int success = 0, failed = 0;

        for (int index = 0; index < data.size(); index++) {
            String[] fields = data.get(index);
            try {
                if (fields.length < 5) {
                    failed++;
                    recordFailureDetail(result, sectionName, index, fields,
                        "字段数量不足，至少需要 5 列，实际为 " + fields.length + " 列",
                        null);
                    continue;
                }

                Long schoolId = Long.parseLong(fields[0]);
                
                // 直接创建新学派，不查找现有学派
                School school = new School();
                school.setId(schoolId); // 设置CSV中的ID
                school.setName(fields[1]);
                
                // 处理英文名称
                if (fields.length > 2 && !fields[2].isEmpty() && !fields[2].equals("null")) {
                    school.setNameEn(fields[2]);
                }
                
                // 处理描述
                if (fields.length > 3 && !fields[3].isEmpty() && !fields[3].equals("null")) {
                    school.setDescription(fields[3]);
                }
                
                // 处理英文描述
                if (fields.length > 4 && !fields[4].isEmpty() && !fields[4].equals("null")) {
                    school.setDescriptionEn(fields[4]);
                }

                // 解析父学派ID (字段5)
                if (fields.length > 5 && !fields[5].isEmpty() && !fields[5].equals("null")) {
                    try {
                        Long parentId = Long.parseLong(fields[5]);
                        School parent = schoolRepository.findById(parentId).orElse(null);
                        if (parent != null) {
                            school.setParent(parent);
                        } else {
                            logger.warn("父学派ID {} 不存在，跳过设置父学派", parentId);
                        }
                    } catch (NumberFormatException e) {
                        logger.warn("父学派ID格式错误: {}", fields[5]);
                    }
                }
                
                // 注意：创建者ID (字段6) 暂时跳过处理，因为School模型中需要User关联

                // 处理点赞数 (字段7)
                if (fields.length > 7 && !fields[7].isEmpty() && !fields[7].equals("null")) {
                    try {
                        school.setLikeCount(Integer.parseInt(fields[7]));
                    } catch (NumberFormatException e) {
                        school.setLikeCount(0);
                    }
                } else {
                    school.setLikeCount(0);
                }

                // 解析创建时间 (字段8)
                if (fields.length > 8 && !fields[8].equals("未知时间") && !fields[8].isEmpty() && !fields[8].equals("null")) {
                    try {
                        school.setCreatedAt(LocalDateTime.parse(fields[8], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    } catch (Exception e) {
                        logger.warn("学派ID {}: 创建时间格式错误: {}", schoolId, fields[8]);
                    }
                }
                
                // 解析更新时间 (字段9)
                if (fields.length > 9 && !fields[9].equals("未知时间") && !fields[9].isEmpty() && !fields[9].equals("null")) {
                    try {
                        school.setUpdatedAt(LocalDateTime.parse(fields[9], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    } catch (Exception e) {
                        logger.warn("学派ID {}: 更新时间格式错误: {}", schoolId, fields[9]);
                    }
                }

                // 优先尝试更新现有学派，保留已有作者；若不存在则插入（插入时可带作者ID）
                try {
                    // 解析创建者ID（字段6），允许为空或“已注销”
                    Long creatorUserId = null;
                    if (fields.length > 6 && fields[6] != null) {
                        String u = fields[6].trim();
                        if (!u.isEmpty() && !"null".equalsIgnoreCase(u) && !"已注销".equals(u)) {
                            try {
                                creatorUserId = Long.parseLong(u);
                                // 校验存在
                                String checkUserSql = "SELECT id FROM users WHERE id = ? LIMIT 1";
                                jakarta.persistence.Query cu = entityManager.createNativeQuery(checkUserSql);
                                cu.setParameter(1, creatorUserId);
                                java.util.List<?> ur = cu.getResultList();
                                if (ur.isEmpty()) creatorUserId = null;
                            } catch (NumberFormatException ignore) {
                                creatorUserId = null;
                            }
                        }
                    }

                    final String tableName = "schools";
                    final String context = "学派导入";

                    List<String> updateAssignments = new ArrayList<>();
                    List<Object> updateParams = new ArrayList<>();

                    addUpdateColumn(updateAssignments, updateParams, tableName, "name", school.getName(), context, true);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "name_en", school.getNameEn(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "description", school.getDescription(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "description_en", school.getDescriptionEn(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "parent_id",
                            school.getParent() != null ? school.getParent().getId() : null, context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "like_count", school.getLikeCount(), context, false);

                    if (ensureColumnExists(tableName, "updated_at", false, context)) {
                        updateAssignments.add("updated_at = ?");
                        updateParams.add(school.getUpdatedAt() != null ? school.getUpdatedAt() : java.time.LocalDateTime.now());
                    }
                    if (ensureColumnExists(tableName, "user_id", false, context)) {
                        updateAssignments.add("user_id = COALESCE(?, user_id)");
                        updateParams.add(creatorUserId);
                    }

                    String updateSql = "UPDATE " + tableName + " SET " + String.join(", ", updateAssignments) + " WHERE id = ?";
                    updateParams.add(school.getId());

                    jakarta.persistence.Query updateQ = entityManager.createNativeQuery(updateSql);
                    for (int p = 0; p < updateParams.size(); p++) {
                        updateQ.setParameter(p + 1, updateParams.get(p));
                    }
                    int updated = updateQ.executeUpdate();

                    if (updated == 0) {
                        List<String> insertColumns = new ArrayList<>();
                        List<Object> insertParams = new ArrayList<>();

                        addInsertColumn(insertColumns, insertParams, tableName, "id", school.getId(), context, true);
                        addInsertColumn(insertColumns, insertParams, tableName, "name", school.getName(), context, true);
                        addInsertColumn(insertColumns, insertParams, tableName, "name_en", school.getNameEn(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "description", school.getDescription(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "description_en", school.getDescriptionEn(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "parent_id",
                                school.getParent() != null ? school.getParent().getId() : null, context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "like_count", school.getLikeCount(), context, false);

                        if (ensureColumnExists(tableName, "user_id", false, context)) {
                            insertColumns.add("user_id");
                            insertParams.add(creatorUserId);
                        }
                        if (ensureColumnExists(tableName, "created_at", false, context)) {
                            insertColumns.add("created_at");
                            insertParams.add(school.getCreatedAt());
                        }
                        if (ensureColumnExists(tableName, "updated_at", false, context)) {
                            insertColumns.add("updated_at");
                            insertParams.add(school.getUpdatedAt() != null ? school.getUpdatedAt() : java.time.LocalDateTime.now());
                        }

                        String insertSql = "INSERT INTO " + tableName + " (" + String.join(", ", insertColumns) + ") VALUES (" +
                                String.join(", ", Collections.nCopies(insertColumns.size(), "?")) + ")";
                        jakarta.persistence.Query insertQ = entityManager.createNativeQuery(insertSql);
                        for (int p = 0; p < insertParams.size(); p++) {
                            insertQ.setParameter(p + 1, insertParams.get(p));
                        }
                        int rows = insertQ.executeUpdate();
                        if (rows > 0) {
                            success++;
                            logger.debug("学派插入成功: ID={}, name={}", school.getId(), school.getName());
                        } else {
                            logger.warn("学派ID {} 插入失败", school.getId());
                            failed++;
                            recordFailureDetail(result, sectionName, index, fields, "数据库未插入任何记录", null);
                        }
                    } else {
                        success++;
                        logger.debug("学派更新成功: ID={}, name={}", school.getId(), school.getName());
                    }
                } catch (Exception saveException) {
                    logger.error("保存学派时发生异常: ID={}, 错误: {}", school.getId(), saveException.getMessage(), saveException);
                    throw saveException;
                }

            } catch (Exception e) {
                failed++;
                logger.warn("导入学派失败: " + Arrays.toString(fields), e);
                recordFailureDetail(result, sectionName, index, fields, "导入失败", e);
            }
        }

        result.addResult(sectionName, success, failed);
        logger.info("学派数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    public void importPhilosophersInTransaction(ImportResult result, List<String[]> data) {
        try {
            transactionTemplate.execute(status -> {
                importPhilosophers(result, data);
                return null;
            });
        } catch (Exception e) {
            logger.error("哲学家导入事务失败", e);
            // 不要重新抛出异常，避免影响整体导入流程
        }
    }

    private void importPhilosophers(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入哲学家数据，共 {} 条", data.size());
        final String sectionName = "哲学家";
        final String philosopherBioColumn = resolvePhilosopherBioColumn();
        final boolean philosopherBioColumnExists = philosopherBioColumn != null;
        final boolean philosopherBioEnColumnExists = tableColumnExists("philosophers", "bio_en");
        int success = 0, failed = 0;

        for (int index = 0; index < data.size(); index++) {
            String[] fields = data.get(index);
            try {
                if (fields.length < 5) {
                    logger.warn("哲学家字段数量不足，跳过: {}", Arrays.toString(fields));
                    failed++;
                    recordFailureDetail(result, sectionName, index, fields,
                        "字段数量不足，至少需要 5 列，实际为 " + fields.length + " 列",
                        null);
                    continue;
                }

                Long philosopherId = Long.parseLong(fields[0]);
                logger.debug("处理哲学家ID: {}, 字段: {}", philosopherId, Arrays.toString(fields));
                
                // 直接创建新哲学家，不查找现有哲学家
                Philosopher philosopher = new Philosopher();
                philosopher.setId(philosopherId); // 设置CSV中的ID
                philosopher.setName(fields[1]);
                
                // 处理英文姓名 (第3个字段)
                if (fields.length > 2 && !fields[2].isEmpty() && !fields[2].equals("null")) {
                    philosopher.setNameEn(fields[2]);
                }

                // 解析出生年份 (第4个字段)
                if (fields.length > 3 && !fields[3].isEmpty() && !fields[3].equals("null")) {
                    try {
                        philosopher.setBirthYear(Integer.parseInt(fields[3]));
                    } catch (NumberFormatException e) {
                        logger.warn("哲学家ID {}: 出生年份格式错误: {}", philosopherId, fields[3]);
                    }
                }
                
                // 解析卒年 (第5个字段)
                if (fields.length > 4 && !fields[4].isEmpty() && !fields[4].equals("null")) {
                    try {
                        philosopher.setDeathYear(Integer.parseInt(fields[4]));
                    } catch (NumberFormatException e) {
                        logger.warn("哲学家ID {}: 卒年格式错误: {}", philosopherId, fields[4]);
                    }
                }
                
                // 处理时代 (第6个字段)
                if (fields.length > 5 && !fields[5].isEmpty() && !fields[5].equals("null")) {
                    philosopher.setEra(fields[5]);
                }
                
                // 处理国籍 (第7个字段)
                if (fields.length > 6 && !fields[6].isEmpty() && !fields[6].equals("null")) {
                    philosopher.setNationality(fields[6]);
                }
                
                // 处理传记 (第8个字段)
                if (fields.length > 7 && !fields[7].isEmpty() && !fields[7].equals("null")) {
                    philosopher.setBio(fields[7]);
                }
                
                // 处理英文传记 (第9个字段)
                if (fields.length > 8 && !fields[8].isEmpty() && !fields[8].equals("null")) {
                    philosopher.setBioEn(fields[8]);
                }
                
                // 处理图片URL (第10个字段)
                if (fields.length > 9 && !fields[9].isEmpty() && !fields[9].equals("null")) {
                    philosopher.setImageUrl(fields[9]);
                }
                
                // 处理点赞数 (第12个字段)
                if (fields.length > 11 && !fields[11].isEmpty() && !fields[11].equals("null")) {
                    try {
                        philosopher.setLikeCount(Integer.parseInt(fields[11]));
                    } catch (NumberFormatException e) {
                        philosopher.setLikeCount(0);
                    }
                } else {
                    philosopher.setLikeCount(0);
                }

                // 解析创建时间 (第13个字段)
                if (fields.length > 12 && !fields[12].equals("未知时间") && !fields[12].isEmpty() && !fields[12].equals("null")) {
                    philosopher.setCreatedAt(LocalDateTime.parse(fields[12], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
                
                // 解析更新时间 (第14个字段)
                if (fields.length > 13 && !fields[13].equals("未知时间") && !fields[13].isEmpty() && !fields[13].equals("null")) {
                    philosopher.setUpdatedAt(LocalDateTime.parse(fields[13], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }

                // 优先更新，若不存在再插入；作者为空时不覆盖现有作者（COALESCE）
                try {
                    // 解析创建者ID（字段10后面一列：创建者ID）
                    Long creatorUserId = null;
                    if (fields.length > 10 && fields[10] != null) {
                        String u = fields[10].trim();
                        if (!u.isEmpty() && !"null".equalsIgnoreCase(u) && !"已注销".equals(u)) {
                            try {
                                Long uid = Long.parseLong(u);
                                String checkUserSql = "SELECT id FROM users WHERE id = ? LIMIT 1";
                                jakarta.persistence.Query cu = entityManager.createNativeQuery(checkUserSql);
                                cu.setParameter(1, uid);
                                java.util.List<?> ur = cu.getResultList();
                                if (!ur.isEmpty()) creatorUserId = uid;
                            } catch (NumberFormatException ignore) {}
                        }
                    }

                    final String tableName = "philosophers";
                    final String context = "哲学家导入";

                    List<String> updateAssignments = new ArrayList<>();
                    List<Object> updateParams = new ArrayList<>();

                    addUpdateColumn(updateAssignments, updateParams, tableName, "name", philosopher.getName(), context, true);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "name_en", philosopher.getNameEn(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "birth_year", philosopher.getBirthYear(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "death_year", philosopher.getDeathYear(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "era", philosopher.getEra(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "nationality", philosopher.getNationality(), context, false);

                    if (philosopherBioColumnExists) {
                        updateAssignments.add(philosopherBioColumn + " = ?");
                        updateParams.add(philosopher.getBio());
                    }
                    if (philosopherBioEnColumnExists) {
                        addUpdateColumn(updateAssignments, updateParams, tableName, "bio_en", philosopher.getBioEn(), context, false);
                    }

                    addUpdateColumn(updateAssignments, updateParams, tableName, "image_url", philosopher.getImageUrl(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "like_count",
                            philosopher.getLikeCount() != null ? philosopher.getLikeCount() : 0, context, false);

                    if (ensureColumnExists(tableName, "updated_at", false, context)) {
                        updateAssignments.add("updated_at = ?");
                        updateParams.add(philosopher.getUpdatedAt() != null ? philosopher.getUpdatedAt() : java.time.LocalDateTime.now());
                    }
                    if (ensureColumnExists(tableName, "user_id", false, context)) {
                        updateAssignments.add("user_id = COALESCE(?, user_id)");
                        updateParams.add(creatorUserId);
                    }

                    String updateSql = "UPDATE " + tableName + " SET " + String.join(", ", updateAssignments) + " WHERE id = ?";
                    updateParams.add(philosopher.getId());

                    jakarta.persistence.Query updateQ = entityManager.createNativeQuery(updateSql);
                    for (int p = 0; p < updateParams.size(); p++) {
                        updateQ.setParameter(p + 1, updateParams.get(p));
                    }
                    int updated = updateQ.executeUpdate();

                    if (updated == 0) {
                        List<String> insertColumns = new ArrayList<>();
                        List<Object> insertParams = new ArrayList<>();

                        addInsertColumn(insertColumns, insertParams, tableName, "id", philosopher.getId(), context, true);
                        addInsertColumn(insertColumns, insertParams, tableName, "name", philosopher.getName(), context, true);
                        addInsertColumn(insertColumns, insertParams, tableName, "name_en", philosopher.getNameEn(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "birth_year", philosopher.getBirthYear(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "death_year", philosopher.getDeathYear(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "era", philosopher.getEra(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "nationality", philosopher.getNationality(), context, false);

                        if (philosopherBioColumnExists) {
                            insertColumns.add(philosopherBioColumn);
                            insertParams.add(philosopher.getBio());
                        }
                        if (philosopherBioEnColumnExists) {
                            addInsertColumn(insertColumns, insertParams, tableName, "bio_en", philosopher.getBioEn(), context, false);
                        }

                        addInsertColumn(insertColumns, insertParams, tableName, "image_url", philosopher.getImageUrl(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "like_count",
                                philosopher.getLikeCount() != null ? philosopher.getLikeCount() : 0, context, false);

                        if (ensureColumnExists(tableName, "user_id", false, context)) {
                            insertColumns.add("user_id");
                            insertParams.add(creatorUserId);
                        }
                        if (ensureColumnExists(tableName, "created_at", false, context)) {
                            insertColumns.add("created_at");
                            insertParams.add(philosopher.getCreatedAt());
                        }
                        if (ensureColumnExists(tableName, "updated_at", false, context)) {
                            insertColumns.add("updated_at");
                            insertParams.add(philosopher.getUpdatedAt() != null ? philosopher.getUpdatedAt() : java.time.LocalDateTime.now());
                        }

                        String insertSql = "INSERT INTO " + tableName + " (" + String.join(", ", insertColumns) + ") VALUES (" +
                                String.join(", ", Collections.nCopies(insertColumns.size(), "?")) + ")";
                        jakarta.persistence.Query insertQ = entityManager.createNativeQuery(insertSql);
                        for (int p = 0; p < insertParams.size(); p++) {
                            insertQ.setParameter(p + 1, insertParams.get(p));
                        }
                        int rows = insertQ.executeUpdate();
                        if (rows > 0) {
                            success++;
                            logger.debug("哲学家插入成功: ID={}, name={}", philosopher.getId(), philosopher.getName());
                        } else {
                            logger.warn("哲学家ID {} 插入失败", philosopher.getId());
                            failed++;
                            recordFailureDetail(result, sectionName, index, fields, "数据库未插入任何记录", null);
                        }
                    } else {
                        success++;
                        logger.debug("哲学家更新成功: ID={}, name={}", philosopher.getId(), philosopher.getName());
                    }
                } catch (Exception saveException) {
                    logger.error("保存哲学家时发生异常: ID={}, 错误: {}", philosopher.getId(), saveException.getMessage(), saveException);
                    throw saveException;
                }

            } catch (Exception e) {
                failed++;
                logger.error("导入哲学家失败: " + Arrays.toString(fields), e);
                recordFailureDetail(result, sectionName, index, fields, "导入失败", e);
            }
        }

        result.addResult(sectionName, success, failed);
        logger.info("哲学家数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    public void importContentsInTransaction(ImportResult result, List<String[]> data) {
        try {
            transactionTemplate.execute(status -> {
                importContents(result, data);
                // 在事务内刷新缓存，确保之前导入的数据对后续查询可见
                entityManager.flush();
                entityManager.clear();
                return null;
            });
        } catch (Exception e) {
            logger.error("内容导入事务失败", e);
            // 不要重新抛出异常，避免影响整体导入流程
        }
    }

    private void importContents(ImportResult result, List<String[]> data) {
        if (data == null) {
            logger.warn("内容数据为null，跳过了内容导入");
            result.addResult("内容", 0, 0);
            return;
        }

        logger.info("开始导入内容数据，共 {} 条", data.size());
        final String sectionName = "内容";
        int success = 0, failed = 0;

        for (int index = 0; index < data.size(); index++) {
            String[] fields = data.get(index);
            try {
                if (fields.length < 5) {
                    logger.warn("内容字段数量不足，跳过: {}", Arrays.toString(fields));
                    failed++;
                    recordFailureDetail(result, sectionName, index, fields,
                        "字段数量不足，至少需要 5 列，实际为 " + fields.length + " 列",
                        null);
                    continue;
                }

                Long contentId = Long.parseLong(fields[0]);
                logger.debug("处理内容ID: {}, 字段: {}", contentId, Arrays.toString(fields));
                
                // 直接创建新内容，不查找现有内容
                Content content = new Content();
                content.setId(contentId); // 设置CSV中的ID
                logger.debug("创建新内容，使用CSV中的ID: {}", contentId);
                
                // 设置内容文本
                content.setContent(fields[1]);
                
                // 设置英文内容（如果存在）
                if (fields.length > 2 && !fields[2].isEmpty() && !fields[2].equals("null")) {
                    content.setContentEn(fields[2]);
                }
                
                // 设置标题（如果存在）
                if (fields.length > 6 && !fields[6].isEmpty() && !fields[6].equals("null")) {
                    content.setTitle(fields[6]);
                }
                
                // 设置排序索引（如果存在）
                if (fields.length > 7 && !fields[7].isEmpty() && !fields[7].equals("null")) {
                    try {
                        content.setOrderIndex(Integer.parseInt(fields[7]));
                    } catch (NumberFormatException e) {
                        content.setOrderIndex(0);
                    }
                } else {
                    content.setOrderIndex(0);
                }
                
                // 暂时跳过关联设置，避免乐观锁冲突
                // 关联将在后续的关联导入方法中处理
                logger.debug("跳过内容关联设置，将在后续步骤中处理");
                
                // 设置必需字段的默认值
                content.setLikeCount(0);
                content.setPrivate(false);
                content.setStatus(0);
                content.setBlocked(false);
                content.setVersion(1L); // 设置初始版本号为1，避免乐观锁冲突

                logger.debug("准备保存内容: ID={}, content={}", contentId, fields[1]);

                // 优先尝试更新已有内容，避免删除后丢失既有作者等关联
                try {
                    final String tableName = "contents";
                    final String context = "内容导入";

                    List<String> updateAssignments = new ArrayList<>();
                    List<Object> updateParams = new ArrayList<>();

                    addUpdateColumn(updateAssignments, updateParams, tableName, "content", content.getContent(), context, true);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "content_en", content.getContentEn(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "title", content.getTitle(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "order_index", content.getOrderIndex(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "like_count", content.getLikeCount(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "is_private", content.isPrivate(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "status", content.getStatus(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "is_blocked", content.isBlocked(), context, false);
                    addUpdateColumn(updateAssignments, updateParams, tableName, "version", content.getVersion(), context, false);

                    if (ensureColumnExists(tableName, "updated_at", false, context)) {
                        updateAssignments.add("updated_at = ?");
                        updateParams.add(java.time.LocalDateTime.now());
                    }

                    String updateSql = "UPDATE " + tableName + " SET " + String.join(", ", updateAssignments) + " WHERE id = ?";
                    updateParams.add(contentId);

                    jakarta.persistence.Query updateQuery = entityManager.createNativeQuery(updateSql);
                    for (int p = 0; p < updateParams.size(); p++) {
                        updateQuery.setParameter(p + 1, updateParams.get(p));
                    }

                    int updatedRows = updateQuery.executeUpdate();

                    if (updatedRows == 0) {
                        List<String> insertColumns = new ArrayList<>();
                        List<Object> insertParams = new ArrayList<>();

                        addInsertColumn(insertColumns, insertParams, tableName, "id", contentId, context, true);
                        addInsertColumn(insertColumns, insertParams, tableName, "content", content.getContent(), context, true);
                        addInsertColumn(insertColumns, insertParams, tableName, "content_en", content.getContentEn(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "title", content.getTitle(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "order_index", content.getOrderIndex(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "like_count", content.getLikeCount(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "is_private", content.isPrivate(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "status", content.getStatus(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "is_blocked", content.isBlocked(), context, false);
                        addInsertColumn(insertColumns, insertParams, tableName, "version", content.getVersion(), context, false);

                        if (ensureColumnExists(tableName, "philosopher_id", false, context)) {
                            insertColumns.add("philosopher_id");
                            insertParams.add(null);
                        }
                        if (ensureColumnExists(tableName, "school_id", false, context)) {
                            insertColumns.add("school_id");
                            insertParams.add(null);
                        }
                        if (ensureColumnExists(tableName, "user_id", false, context)) {
                            insertColumns.add("user_id");
                            insertParams.add(null);
                        }

                        java.time.LocalDateTime now = java.time.LocalDateTime.now();
                        if (ensureColumnExists(tableName, "created_at", false, context)) {
                            insertColumns.add("created_at");
                            insertParams.add(now);
                        }
                        if (ensureColumnExists(tableName, "updated_at", false, context)) {
                            insertColumns.add("updated_at");
                            insertParams.add(now);
                        }

                        String insertSql = "INSERT INTO " + tableName + " (" + String.join(", ", insertColumns) + ") VALUES (" +
                                String.join(", ", Collections.nCopies(insertColumns.size(), "?")) + ")";
                        jakarta.persistence.Query insertQuery = entityManager.createNativeQuery(insertSql);
                        for (int p = 0; p < insertParams.size(); p++) {
                            insertQuery.setParameter(p + 1, insertParams.get(p));
                        }

                        int rowsAffected = insertQuery.executeUpdate();
                        if (rowsAffected > 0) {
                            logger.debug("内容插入成功: ID={}", contentId);
                            success++;
                        } else {
                            logger.warn("内容ID {} 插入失败", contentId);
                            failed++;
                            recordFailureDetail(result, sectionName, index, fields, "数据库未插入任何记录", null);
                        }
                    } else {
                        logger.debug("内容更新成功: ID={}", contentId);
                        success++;
                    }
                } catch (Exception saveException) {
                    logger.error("保存内容时发生异常: ID={}, 错误: {}", contentId, saveException.getMessage(), saveException);
                    throw saveException; // 重新抛出异常以便上层捕获
                }

            } catch (Exception e) {
                failed++;
                logger.error("导入内容失败: " + Arrays.toString(fields), e);
                recordFailureDetail(result, sectionName, index, fields, "导入失败", e);
            }
        }

        result.addResult(sectionName, success, failed);
        logger.info("内容数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    public void updateContentAssociationsInTransaction(ImportResult result, List<String[]> data) {
        try {
            transactionTemplate.execute(status -> {
                updateContentAssociations(result, data);
                return null;
            });
        } catch (Exception e) {
            logger.error("内容关联更新事务失败", e);
            // 不要重新抛出异常，避免影响整体导入流程
        }
    }

    private void updateContentAssociations(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始更新内容关联，共 {} 条", data.size());
        int success = 0, failed = 0;

        for (String[] fields : data) {
            try {
                if (fields.length < 5) continue;

                Long contentId = Long.parseLong(fields[0]);
                
                // 使用原生SQL更新关联，避免JPA实体加载
                // 注意：当哲学家/学派/作者列为空或为“已注销”时，不覆盖原值（COALESCE 使 NULL 不改变原值）

                // 查找哲学家ID（字段索引3）- 修复：CSV导出的是哲学家ID，不是名称
                Long philosopherId = null;
                if (fields.length > 3 && !fields[3].isEmpty() && !fields[3].equals("null")) {
                    try {
                        // 直接解析哲学家ID，因为CSV导出的是哲学家ID
                        philosopherId = Long.parseLong(fields[3]);
                        
                        // 验证哲学家是否存在 - 添加重试机制处理事务隔离问题
                        String checkPhilosopherSql = "SELECT id FROM philosophers WHERE id = ? LIMIT 1";
                        jakarta.persistence.Query checkPhilosopherQuery = entityManager.createNativeQuery(checkPhilosopherSql);
                        checkPhilosopherQuery.setParameter(1, philosopherId);
                        List<Object> philosopherResults = checkPhilosopherQuery.getResultList();
                        
                        if (!philosopherResults.isEmpty()) {
                            logger.debug("内容ID {}: 找到哲学家ID {}", contentId, philosopherId);
                        } else {
                            // 如果第一次查询失败，可能是事务隔离问题，尝试刷新缓存后重试
                            logger.warn("内容ID {}: 第一次查询哲学家ID '{}' 失败，尝试刷新缓存后重试", contentId, fields[3]);
                            entityManager.flush();
                            entityManager.clear();
                            
                            // 重试查询
                            checkPhilosopherQuery = entityManager.createNativeQuery(checkPhilosopherSql);
                            checkPhilosopherQuery.setParameter(1, philosopherId);
                            philosopherResults = checkPhilosopherQuery.getResultList();
                            
                            if (!philosopherResults.isEmpty()) {
                                logger.debug("内容ID {}: 重试后找到哲学家ID {}", contentId, philosopherId);
                            } else {
                                logger.warn("内容ID {}: 哲学家ID '{}' 不存在，跳过哲学家关联", contentId, fields[3]);
                                philosopherId = null;
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 如果解析哲学家ID失败，尝试作为名称查找（向后兼容）
                        logger.warn("内容ID {}: 哲学家字段 '{}' 不是有效的ID，尝试作为名称查找", contentId, fields[3]);
                        try {
                            String findPhilosopherSql = "SELECT id FROM philosophers WHERE name = ? LIMIT 1";
                            jakarta.persistence.Query philosopherQuery = entityManager.createNativeQuery(findPhilosopherSql);
                            philosopherQuery.setParameter(1, fields[3]);
                            List<Object> philosopherResults = philosopherQuery.getResultList();
                            if (!philosopherResults.isEmpty()) {
                                philosopherId = ((Number) philosopherResults.get(0)).longValue();
                                logger.debug("内容ID {}: 通过名称找到哲学家 '{}' -> ID {}", contentId, fields[3], philosopherId);
                            } else {
                                logger.warn("内容ID {}: 哲学家 '{}' 不存在，跳过哲学家关联", contentId, fields[3]);
                                philosopherId = null;
                            }
                        } catch (Exception ex) {
                            logger.warn("内容ID {}: 查找哲学家 '{}' 时发生错误: {}", contentId, fields[3], ex.getMessage());
                            philosopherId = null;
                        }
                    } catch (Exception e) {
                        logger.warn("内容ID {}: 查找哲学家ID '{}' 时发生错误: {}", contentId, fields[3], e.getMessage());
                        philosopherId = null;
                    }
                }
                
                // 查找学派ID（字段索引4）- 修复：CSV导出的是学派ID，不是名称
                Long schoolId = null;
                if (fields.length > 4 && !fields[4].isEmpty() && !fields[4].equals("null")) {
                    try {
                        // 直接解析学派ID，因为CSV导出的是学派ID
                        schoolId = Long.parseLong(fields[4]);
                        
                        // 验证学派是否存在 - 添加重试机制处理事务隔离问题
                        String checkSchoolSql = "SELECT id FROM schools WHERE id = ? LIMIT 1";
                        jakarta.persistence.Query checkSchoolQuery = entityManager.createNativeQuery(checkSchoolSql);
                        checkSchoolQuery.setParameter(1, schoolId);
                        List<Object> schoolResults = checkSchoolQuery.getResultList();
                        
                        if (!schoolResults.isEmpty()) {
                            logger.debug("内容ID {}: 找到学派ID {}", contentId, schoolId);
                        } else {
                            // 如果第一次查询失败，可能是事务隔离问题，尝试刷新缓存后重试
                            logger.warn("内容ID {}: 第一次查询学派ID '{}' 失败，尝试刷新缓存后重试", contentId, fields[4]);
                            entityManager.flush();
                            entityManager.clear();
                            
                            // 重试查询
                            checkSchoolQuery = entityManager.createNativeQuery(checkSchoolSql);
                            checkSchoolQuery.setParameter(1, schoolId);
                            schoolResults = checkSchoolQuery.getResultList();
                            
                            if (!schoolResults.isEmpty()) {
                                logger.debug("内容ID {}: 重试后找到学派ID {}", contentId, schoolId);
                            } else {
                                logger.warn("内容ID {}: 学派ID '{}' 不存在，跳过学派关联", contentId, fields[4]);
                                schoolId = null;
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 如果解析学派ID失败，尝试作为名称查找（向后兼容）
                        logger.warn("内容ID {}: 学派字段 '{}' 不是有效的ID，尝试作为名称查找", contentId, fields[4]);
                        try {
                            String findSchoolSql = "SELECT id FROM schools WHERE name = ? LIMIT 1";
                            jakarta.persistence.Query schoolQuery = entityManager.createNativeQuery(findSchoolSql);
                            schoolQuery.setParameter(1, fields[4]);
                            List<Object> schoolResults = schoolQuery.getResultList();
                            if (!schoolResults.isEmpty()) {
                                schoolId = ((Number) schoolResults.get(0)).longValue();
                                logger.debug("内容ID {}: 通过名称找到学派 '{}' -> ID {}", contentId, fields[4], schoolId);
                            } else {
                                logger.warn("内容ID {}: 学派 '{}' 不存在，跳过学派关联", contentId, fields[4]);
                                schoolId = null;
                            }
                        } catch (Exception ex) {
                            logger.warn("内容ID {}: 查找学派 '{}' 时发生错误: {}", contentId, fields[4], ex.getMessage());
                            schoolId = null;
                        }
                    } catch (Exception e) {
                        logger.warn("内容ID {}: 查找学派ID '{}' 时发生错误: {}", contentId, fields[4], e.getMessage());
                        schoolId = null;
                    }
                }
                
                // 查找用户ID（字段索引5）
                Long userId = null;
                String rawUserField = (fields.length > 5) ? fields[5] : null;
                String userIdStr = null;
                if (rawUserField != null) {
                    String trimmed = rawUserField.trim();
                    if (!trimmed.isEmpty() && !"null".equalsIgnoreCase(trimmed) && !"已注销".equals(trimmed)) {
                        userIdStr = trimmed;
                    }
                }

                if (userIdStr != null) {
                    try {
                        // 直接解析用户ID，因为CSV导出的是用户ID
                        userId = Long.parseLong(userIdStr);
                        
                        // 验证用户是否存在 - 添加重试机制处理事务隔离问题
                        String checkUserSql = "SELECT id FROM users WHERE id = ? LIMIT 1";
                        jakarta.persistence.Query checkUserQuery = entityManager.createNativeQuery(checkUserSql);
                        checkUserQuery.setParameter(1, userId);
                        List<Object> userResults = checkUserQuery.getResultList();
                        
                        if (!userResults.isEmpty()) {
                            logger.info("内容ID {}: 找到作者ID {}", contentId, userId);
                        } else {
                            // 如果第一次查询失败，可能是事务隔离问题，尝试刷新缓存后重试
                            logger.warn("内容ID {}: 第一次查询作者ID '{}' 失败，尝试刷新缓存后重试", contentId, userIdStr);
                            entityManager.flush();
                            entityManager.clear();
                            
                            // 重试查询
                            checkUserQuery = entityManager.createNativeQuery(checkUserSql);
                            checkUserQuery.setParameter(1, userId);
                            userResults = checkUserQuery.getResultList();
                            
                            if (!userResults.isEmpty()) {
                                logger.info("内容ID {}: 重试后找到作者ID {}", contentId, userId);
                            } else {
                                // 当在数据库中找不到用户时，记录警告但继续导入（将user_id设为NULL）
                                logger.warn("内容ID {}: 指定的作者ID '{}' 在数据库中不存在，将user_id设为NULL", contentId, userIdStr);
                                userId = null;
                            }
                        }
                    } catch (NumberFormatException e) {
                        // 如果解析用户ID失败，尝试作为用户名或邮箱查找（向后兼容增强）
                        logger.warn("内容ID {}: 作者字段 '{}' 不是有效的用户ID，尝试作为用户名/邮箱查找", contentId, userIdStr);
                        try {
                            // 1) 按用户名查找
                            String findByUsernameSql = "SELECT id FROM users WHERE username = ? LIMIT 1";
                            jakarta.persistence.Query byUsername = entityManager.createNativeQuery(findByUsernameSql);
                            byUsername.setParameter(1, userIdStr);
                            List<Object> userResults = byUsername.getResultList();
                            if (!userResults.isEmpty()) {
                                userId = ((Number) userResults.get(0)).longValue();
                                logger.info("内容ID {}: 通过用户名找到作者 '{}' -> ID {}", contentId, userIdStr, userId);
                            } else {
                                // 2) 按邮箱查找
                                String findByEmailSql = "SELECT id FROM users WHERE email = ? LIMIT 1";
                                jakarta.persistence.Query byEmail = entityManager.createNativeQuery(findByEmailSql);
                                byEmail.setParameter(1, userIdStr);
                                List<Object> userByEmail = byEmail.getResultList();
                                if (!userByEmail.isEmpty()) {
                                    userId = ((Number) userByEmail.get(0)).longValue();
                                    logger.info("内容ID {}: 通过邮箱找到作者 '{}' -> ID {}", contentId, userIdStr, userId);
                                } else {
                                    logger.warn("内容ID {}: 指定的作者 '{}' 在数据库中不存在，将user_id设为NULL", contentId, userIdStr);
                                    userId = null;
                                }
                            }
                        } catch (Exception ex) {
                            logger.error("内容ID {}: 在查找作者 '{}' 时发生数据库错误: {}，将user_id设为NULL", contentId, userIdStr, ex.getMessage());
                            userId = null;
                        }
                    } catch (Exception e) {
                        // 捕获其他潜在的数据库查询异常，记录错误但继续导入
                        logger.error("内容ID {}: 在查找作者ID '{}' 时发生数据库错误: {}，将user_id设为NULL", contentId, userIdStr, e.getMessage());
                        userId = null;
                    }
                } else {
                    // 如果CSV中没有提供作者，将user_id设为NULL（这是正常情况）
                    logger.debug("内容ID {}: CSV文件中未提供作者ID，将user_id设为NULL", contentId);
                    userId = null;
                }
                
                final String assocTable = "contents";
                final String assocContext = "内容关联更新";
                List<String> associationClauses = new ArrayList<>();
                List<Object> associationParams = new ArrayList<>();

                if (ensureColumnExists(assocTable, "philosopher_id", false, assocContext)) {
                    associationClauses.add("philosopher_id = COALESCE(?, philosopher_id)");
                    associationParams.add(philosopherId);
                }
                if (ensureColumnExists(assocTable, "school_id", false, assocContext)) {
                    associationClauses.add("school_id = COALESCE(?, school_id)");
                    associationParams.add(schoolId);
                }
                if (ensureColumnExists(assocTable, "user_id", false, assocContext)) {
                    associationClauses.add("user_id = COALESCE(?, user_id)");
                    associationParams.add(userId);
                }

                if (associationClauses.isEmpty()) {
                    logger.warn("内容ID {}: 数据库缺少内容关联列，跳过该记录的关联更新", contentId);
                    failed++;
                    continue;
                }

                String sql = "UPDATE " + assocTable + " SET " + String.join(", ", associationClauses) + " WHERE id = ?";
                associationParams.add(contentId);

                jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
                for (int p = 0; p < associationParams.size(); p++) {
                    query.setParameter(p + 1, associationParams.get(p));
                }

                int updated = query.executeUpdate();
                if (updated > 0) {
                    success++;
                    logger.debug("内容关联更新成功: ID={}, philosopher={}, school={}, user={}", 
                               contentId, philosopherId, schoolId, userId);
                } else {
                    failed++;
                    logger.warn("内容关联更新失败: ID={} (内容不存在)", contentId);
                }

            } catch (Exception e) {
                failed++;
                logger.warn("更新内容关联失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("内容关联", success, failed);
        logger.info("内容关联更新完成，成功: {}, 失败: {}", success, failed);
    }

    /**
     * 批量修复作者：仅当当前作者为 NULL 时，按映射设置作者ID
     * CSV 预期两列：[内容ID, 作者ID]
     */
    @Transactional
    public ImportResult repairContentAuthorsFromCsv(MultipartFile file) {
        ImportResult result = new ImportResult();
        int success = 0, failed = 0;
        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    lines.add(line);
                }
            }

            for (String line : lines) {
                try {
                    String[] fields = parseCsvLine(line);
                    if (fields.length < 2) {
                        failed++;
                        continue;
                    }
                    Long contentId = Long.parseLong(fields[0].trim());
                    Long userId = Long.parseLong(fields[1].trim());

                    String sql = "UPDATE contents SET user_id = ? WHERE id = ? AND user_id IS NULL";
                    jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
                    query.setParameter(1, userId);
                    query.setParameter(2, contentId);
                    int updated = query.executeUpdate();
                    if (updated > 0) {
                        success++;
                    } else {
                        // 可能是内容不存在，或已有作者非空
                        failed++;
                    }
                } catch (Exception e) {
                    failed++;
                }
            }

            result.setSuccess(true);
            result.setMessage("作者批量修复完成");
            result.addResult("作者修复", success, failed);
            result.setTotalImported(success);
            result.setTotalFailed(failed);
        } catch (Exception e) {
            logger.error("作者批量修复失败", e);
            result.setSuccess(false);
            result.setMessage("作者批量修复失败: " + e.getMessage());
        }
        return result;
    }


    @Transactional
    public void importCommentsInTransaction(ImportResult result, List<String[]> data) {
        importComments(result, data);
    }

    private void importComments(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入评论数据，共 {} 条", data.size());
        int success = 0, failed = 0;

        for (String[] fields : data) {
            try {
                if (fields.length < 6) continue;

                Comment comment = new Comment();
                comment.setId(Long.parseLong(fields[0]));
                comment.setBody(fields[1]);
                comment.setLikeCount(0);
                comment.setStatus(0);
                comment.setPrivate(false);

                // 解析内容关联
                if (!fields[3].isEmpty() && !fields[3].equals("null")) {
                    Optional<Content> content = contentRepository.findById(Long.parseLong(fields[3]));
                    if (content.isPresent()) {
                        comment.setContent(content.get());
                    } else {
                        logger.warn("内容ID '{}' 不存在，跳过评论", fields[3]);
                        continue;
                    }
                } else {
                    logger.warn("评论缺少内容关联，跳过");
                    continue;
                }

                // 解析用户关联（支持用户名或用户ID）
                if (!fields[2].isEmpty() && !fields[2].equals("null")) {
                    Optional<User> user;
                    try {
                        // 尝试作为用户ID解析
                        Long userId = Long.parseLong(fields[2]);
                        user = userRepository.findById(userId);
                    } catch (NumberFormatException e) {
                        // 如果解析失败，则作为用户名查找
                        user = userRepository.findByUsername(fields[2]);
                    }
                    
                    if (user.isPresent()) {
                        comment.setUser(user.get());
                    } else {
                        logger.warn("用户 '{}' 不存在，跳过评论", fields[2]);
                        continue;
                    }
                } else {
                    logger.warn("评论缺少用户关联，跳过");
                    continue;
                }

                // 解析父评论关联（可选）
                if (!fields[4].isEmpty() && !fields[4].equals("null")) {
                    Optional<Comment> parent = commentRepository.findById(Long.parseLong(fields[4]));
                    if (parent.isPresent()) {
                        comment.setParent(parent.get());
                    } else {
                        logger.warn("父评论ID '{}' 不存在，跳过父评论关联", fields[4]);
                    }
                }

                // 解析创建时间
                LocalDateTime createdAt = null;
                if (!fields[5].equals("未知时间") && !fields[5].isEmpty() && !fields[5].equals("null")) {
                    createdAt = LocalDateTime.parse(fields[5], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    comment.setCreatedAt(createdAt);
                }

                // 使用原生SQL先删除再插入，避免乐观锁冲突
                try {
                    // 先删除现有记录
                    String deleteSql = "DELETE FROM comments WHERE id = ?";
                    jakarta.persistence.Query deleteQuery = entityManager.createNativeQuery(deleteSql);
                    deleteQuery.setParameter(1, comment.getId());
                    deleteQuery.executeUpdate();
                    
                    final String tableName = "comments";
                    final String context = "评论导入";

                    List<String> insertColumns = new ArrayList<>();
                    List<Object> insertParams = new ArrayList<>();

                    addInsertColumn(insertColumns, insertParams, tableName, "id", comment.getId(), context, true);
                    addInsertColumn(insertColumns, insertParams, tableName, "body", comment.getBody(), context, true);
                    addInsertColumn(insertColumns, insertParams, tableName, "like_count",
                            comment.getLikeCount() != null ? comment.getLikeCount() : 0, context, false);
                    addInsertColumn(insertColumns, insertParams, tableName, "status", comment.getStatus(), context, false);

                    if (ensureColumnExists(tableName, "is_private", false, context)) {
                        insertColumns.add("is_private");
                        insertParams.add(comment.isPrivate() ? 1 : 0);
                    }
                    addInsertColumn(insertColumns, insertParams, tableName, "content_id", comment.getContent().getId(), context, false);
                    addInsertColumn(insertColumns, insertParams, tableName, "user_id", comment.getUser().getId(), context, false);
                    addInsertColumn(insertColumns, insertParams, tableName, "parent_id",
                            comment.getParent() != null ? comment.getParent().getId() : null, context, false);

                    java.time.LocalDateTime createdValue = createdAt != null ? createdAt : java.time.LocalDateTime.now();
                    if (ensureColumnExists(tableName, "created_at", false, context)) {
                        insertColumns.add("created_at");
                        insertParams.add(createdValue);
                    }
                    if (ensureColumnExists(tableName, "updated_at", false, context)) {
                        insertColumns.add("updated_at");
                        insertParams.add(createdValue);
                    }

                    String insertSql = "INSERT INTO " + tableName + " (" + String.join(", ", insertColumns) + ") VALUES (" +
                            String.join(", ", Collections.nCopies(insertColumns.size(), "?")) + ")";
                    jakarta.persistence.Query insertQuery = entityManager.createNativeQuery(insertSql);
                    for (int p = 0; p < insertParams.size(); p++) {
                        insertQuery.setParameter(p + 1, insertParams.get(p));
                    }
                    insertQuery.executeUpdate();
                    success++;
                } catch (Exception e) {
                    throw e; // 重新抛出异常，让外层catch处理
                }

            } catch (Exception e) {
                failed++;
                logger.warn("导入评论失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("评论", success, failed);
        logger.info("评论数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    // 用户登录信息导入方法
    public void importUserLoginInfoInTransaction(ImportResult result, List<String[]> data) {
        try {
            transactionTemplate.execute(status -> {
                importUserLoginInfo(result, data);
                return null;
            });
        } catch (Exception e) {
            logger.error("用户登录信息导入事务失败", e);
            // 不要重新抛出异常，避免影响整体导入流程
        }
    }

    private void importUserLoginInfo(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入用户登录信息数据，共 {} 条", data.size());
        int success = 0, failed = 0;

        for (String[] fields : data) {
            try {
                if (fields.length < 7) continue;

                Long loginInfoId = Long.parseLong(fields[0]);
                Long userId = Long.parseLong(fields[1]);
                String ipAddress = fields[2];
                String browser = fields[3];
                String operatingSystem = fields[4];
                String deviceType = fields[5];
                String loginTimeStr = fields[6];

                // 验证用户是否存在
                try {
                    String checkUserSql = "SELECT id FROM users WHERE id = ? LIMIT 1";
                    jakarta.persistence.Query checkUserQuery = entityManager.createNativeQuery(checkUserSql);
                    checkUserQuery.setParameter(1, userId);
                    Object userResult = checkUserQuery.getSingleResult();
                    if (userResult == null) {
                        logger.warn("用户ID '{}' 不存在，跳过登录信息", userId);
                        failed++;
                        continue;
                    }
                } catch (Exception e) {
                    logger.warn("用户ID '{}' 不存在，跳过登录信息", userId);
                    failed++;
                    continue;
                }

                // 使用原生SQL插入，先删除现有记录再插入新记录
                try {
                    // 先删除现有记录
                    String deleteSql = "DELETE FROM user_login_info WHERE id = ?";
                    jakarta.persistence.Query deleteQuery = entityManager.createNativeQuery(deleteSql);
                    deleteQuery.setParameter(1, loginInfoId);
                    deleteQuery.executeUpdate();
                    
                    final String tableName = "user_login_info";
                    final String context = "用户登录信息导入";

                    List<String> insertColumns = new ArrayList<>();
                    List<Object> insertParams = new ArrayList<>();

                    addInsertColumn(insertColumns, insertParams, tableName, "id", loginInfoId, context, true);
                    addInsertColumn(insertColumns, insertParams, tableName, "user_id", userId, context, true);
                    addInsertColumn(insertColumns, insertParams, tableName, "ip_address", ipAddress, context, true);
                    addInsertColumn(insertColumns, insertParams, tableName, "browser", browser, context, false);
                    addInsertColumn(insertColumns, insertParams, tableName, "operating_system", operatingSystem, context, false);
                    addInsertColumn(insertColumns, insertParams, tableName, "device_type", deviceType, context, false);

                    LocalDateTime loginTime = null;
                    if (!loginTimeStr.equals("未知时间") && !loginTimeStr.isEmpty() && !loginTimeStr.equals("null")) {
                        loginTime = LocalDateTime.parse(loginTimeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    }
                    addInsertColumn(insertColumns, insertParams, tableName, "login_time", loginTime, context, false);

                    String sql = "INSERT INTO " + tableName + " (" + String.join(", ", insertColumns) + ") VALUES (" +
                            String.join(", ", Collections.nCopies(insertColumns.size(), "?")) + ")";
                    
                    jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
                    for (int p = 0; p < insertParams.size(); p++) {
                        query.setParameter(p + 1, insertParams.get(p));
                    }
                    
                    int rowsAffected = query.executeUpdate();
                    if (rowsAffected > 0) {
                        success++;
                        logger.debug("用户登录信息保存成功: ID={}", loginInfoId);
                    } else {
                        logger.warn("用户登录信息ID {} 插入失败", loginInfoId);
                        failed++;
                    }

                } catch (Exception saveException) {
                    logger.error("保存用户登录信息时发生异常: ID={}, 错误: {}", loginInfoId, saveException.getMessage(), saveException);
                    failed++;
                }

            } catch (Exception e) {
                failed++;
                logger.warn("导入用户登录信息失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("用户登录信息", success, failed);
        logger.info("用户登录信息数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    // 其他表的导入方法可以继续添加...

    @Transactional
    public void importLikesInTransaction(ImportResult result, List<String[]> data) {
        importLikes(result, data);
    }

    private void importLikes(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入点赞数据，共 {} 条", data.size());
        int success = 0, failed = 0;

        for (String[] fields : data) {
            try {
                if (fields.length < 5) continue;

                Like like = new Like();
                like.setId(Long.parseLong(fields[0]));

                // 解析用户
                if (!fields[1].isEmpty()) {
                    Optional<User> user = userRepository.findById(Long.parseLong(fields[1]));
                    if (user.isPresent()) {
                        like.setUser(user.get());
                    } else {
                        logger.warn("用户ID '{}' 不存在，跳过点赞记录", fields[1]);
                        continue;
                    }
                }

                // 解析实体类型
                try {
                    like.setEntityType(Like.EntityType.valueOf(fields[2]));
                } catch (IllegalArgumentException e) {
                    logger.warn("无效的实体类型 '{}'", fields[2]);
                    continue;
                }

                like.setEntityId(Long.parseLong(fields[3]));

                // 解析创建时间
                if (!fields[4].equals("未知时间") && !fields[4].isEmpty() && !fields[4].equals("null")) {
                    like.setCreatedAt(LocalDateTime.parse(fields[4], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }

                likeRepository.save(like);
                success++;

            } catch (Exception e) {
                failed++;
                logger.warn("导入点赞失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("点赞", success, failed);
        logger.info("点赞数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    @Transactional
    public void importUserContentEditsInTransaction(ImportResult result, List<String[]> data) {
        importUserContentEdits(result, data);
    }

    private void importUserContentEdits(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入用户内容编辑数据，共 {} 条", data.size());
        int success = 0, failed = 0;

        for (String[] fields : data) {
            try {
                if (fields.length < 8) continue;

                UserContentEdit edit = new UserContentEdit();
                edit.setId(Long.parseLong(fields[0]));

                // 解析用户
                if (!fields[1].isEmpty()) {
                    Optional<User> user = userRepository.findById(Long.parseLong(fields[1]));
                    if (user.isPresent()) {
                        edit.setUser(user.get());
                    } else {
                        logger.warn("用户ID '{}' 不存在，跳过内容编辑记录", fields[1]);
                        continue;
                    }
                }

                edit.setContent(fields[2]);
                edit.setTitle(fields[3]);

                // 解析哲学家
                if (!fields[4].isEmpty() && !fields[4].equals("null")) {
                    Optional<Philosopher> philosopher = philosopherRepository.findById(Long.parseLong(fields[4]));
                    if (philosopher.isPresent()) {
                        edit.setPhilosopher(philosopher.get());
                    } else {
                        logger.warn("哲学家ID '{}' 不存在，跳过内容编辑记录", fields[4]);
                        continue;
                    }
                }

                // 解析学派
                if (!fields[5].isEmpty() && !fields[5].equals("null")) {
                    Optional<School> school = schoolRepository.findById(Long.parseLong(fields[5]));
                    if (school.isPresent()) {
                        edit.setSchool(school.get());
                    } else {
                        logger.warn("学派ID '{}' 不存在，跳过内容编辑记录", fields[5]);
                        continue;
                    }
                }

                // 解析状态
                try {
                    edit.setStatus(UserContentEdit.EditStatus.valueOf(fields[6]));
                } catch (IllegalArgumentException e) {
                    logger.warn("无效的状态 '{}', 使用默认状态PENDING", fields[6]);
                    edit.setStatus(UserContentEdit.EditStatus.PENDING);
                }

                // 解析创建时间
                if (!fields[7].equals("未知时间") && !fields[7].isEmpty() && !fields[7].equals("null")) {
                    edit.setCreatedAt(LocalDateTime.parse(fields[7], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }

                userContentEditRepository.save(edit);
                success++;

            } catch (Exception e) {
                failed++;
                logger.warn("导入用户内容编辑失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("用户内容编辑", success, failed);
        logger.info("用户内容编辑数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    @Transactional
    public void importUserBlocksInTransaction(ImportResult result, List<String[]> data) {
        importUserBlocks(result, data);
    }

    private void importUserBlocks(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入用户屏蔽数据，共 {} 条", data.size());
        int success = 0, failed = 0;

        for (String[] fields : data) {
            try {
                if (fields.length < 4) continue;

                UserBlock block = new UserBlock();
                block.setId(Long.parseLong(fields[0]));

                // 解析屏蔽者
                if (!fields[1].isEmpty() && !fields[1].equals("null")) {
                    Optional<User> blocker = userRepository.findById(Long.parseLong(fields[1]));
                    if (blocker.isPresent()) {
                        block.setBlocker(blocker.get());
                    } else {
                        logger.warn("屏蔽者用户ID '{}' 不存在，跳过屏蔽记录", fields[1]);
                        continue;
                    }
                }

                // 解析被屏蔽者
                if (!fields[2].isEmpty() && !fields[2].equals("null")) {
                    Optional<User> blocked = userRepository.findById(Long.parseLong(fields[2]));
                    if (blocked.isPresent()) {
                        block.setBlocked(blocked.get());
                    } else {
                        logger.warn("被屏蔽者用户ID '{}' 不存在，跳过屏蔽记录", fields[2]);
                        continue;
                    }
                }

                // 解析创建时间
                if (!fields[3].equals("未知时间") && !fields[3].isEmpty() && !fields[3].equals("null")) {
                    block.setCreatedAt(LocalDateTime.parse(fields[3], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }

                userBlockRepository.save(block);
                success++;

            } catch (Exception e) {
                failed++;
                logger.warn("导入用户屏蔽失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("用户屏蔽", success, failed);
        logger.info("用户屏蔽数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    @Transactional
    public void importModeratorBlocksInTransaction(ImportResult result, List<String[]> data) {
        importModeratorBlocks(result, data);
    }

    private void importModeratorBlocks(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入版主屏蔽数据，共 {} 条", data.size());
        int success = 0, failed = 0;

        for (String[] fields : data) {
            try {
                if (fields.length < 6) continue;

                ModeratorBlock block = new ModeratorBlock();
                block.setId(Long.parseLong(fields[0]));

                // 解析版主
                if (!fields[1].isEmpty()) {
                    Optional<User> moderator = userRepository.findById(Long.parseLong(fields[1]));
                    if (moderator.isPresent()) {
                        block.setModerator(moderator.get());
                    } else {
                        logger.warn("版主用户ID '{}' 不存在，跳过版主屏蔽记录", fields[1]);
                        continue;
                    }
                }

                // 解析被屏蔽用户
                if (!fields[2].isEmpty() && !fields[2].equals("null")) {
                    Optional<User> blockedUser = userRepository.findById(Long.parseLong(fields[2]));
                    if (blockedUser.isPresent()) {
                        block.setBlockedUser(blockedUser.get());
                    } else {
                        logger.warn("被屏蔽用户ID '{}' 不存在，跳过版主屏蔽记录", fields[2]);
                        continue;
                    }
                }

                // 解析学派
                if (!fields[3].isEmpty() && !fields[3].equals("null")) {
                    Optional<School> school = schoolRepository.findById(Long.parseLong(fields[3]));
                    if (school.isPresent()) {
                        block.setSchool(school.get());
                    } else {
                        logger.warn("学派ID '{}' 不存在，跳过版主屏蔽记录", fields[3]);
                        continue;
                    }
                }

                block.setReason(fields[4]);

                // 解析创建时间
                if (!fields[5].equals("未知时间") && !fields[5].isEmpty() && !fields[5].equals("null")) {
                    block.setCreatedAt(LocalDateTime.parse(fields[5], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }

                moderatorBlockRepository.save(block);
                success++;

            } catch (Exception e) {
                failed++;
                logger.warn("导入版主屏蔽失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("版主屏蔽", success, failed);
        logger.info("版主屏蔽数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    public void importSchoolTranslationsInTransaction(ImportResult result, List<String[]> data) {
        try {
            transactionTemplate.execute(status -> {
                importSchoolTranslations(result, data);
                return null;
            });
        } catch (Exception e) {
            logger.error("学派翻译导入事务失败", e);
            // 不要重新抛出异常，避免影响整体导入流程
        }
    }

    private void importSchoolTranslations(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入学派翻译数据，共 {} 条", data.size());
        int success = 0, failed = 0;

        for (String[] fields : data) {
            try {
                if (fields.length < 6) {
                    logger.warn("学派翻译字段数量不足（需要至少6个字段，实际{}个），跳过: {}", fields.length, Arrays.toString(fields));
                    failed++;
                    continue;
                }

                SchoolTranslation translation = new SchoolTranslation();
                translation.setId(Long.parseLong(fields[0]));

                // 解析学派
                if (!fields[1].isEmpty() && !fields[1].equals("null")) {
                    Optional<School> school = schoolRepository.findById(Long.parseLong(fields[1]));
                    if (school.isPresent()) {
                        translation.setSchool(school.get());
                    } else {
                        logger.warn("学派ID '{}' 不存在，跳过学派翻译记录", fields[1]);
                        continue;
                    }
                }

                translation.setLanguageCode(fields[2]);
                translation.setNameEn(fields[3]);
                translation.setDescriptionEn(fields[4]);

                // 解析创建时间
                LocalDateTime createdAt = null;
                if (!fields[5].equals("未知时间") && !fields[5].isEmpty() && !fields[5].equals("null")) {
                    createdAt = LocalDateTime.parse(fields[5], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    translation.setCreatedAt(createdAt);
                }

                // 使用 REPLACE INTO 并动态适配列
                try {
                    final String tableName = "schools_translation";
                    final String context = "学派翻译导入";

                    List<String> replaceColumns = new ArrayList<>();
                    List<Object> replaceParams = new ArrayList<>();

                    addInsertColumn(replaceColumns, replaceParams, tableName, "id", translation.getId(), context, true);
                    addInsertColumn(replaceColumns, replaceParams, tableName, "school_id", translation.getSchool().getId(), context, true);
                    addInsertColumn(replaceColumns, replaceParams, tableName, "language_code", translation.getLanguageCode(), context, true);
                    addInsertColumn(replaceColumns, replaceParams, tableName, "name_en", translation.getNameEn(), context, false);
                    addInsertColumn(replaceColumns, replaceParams, tableName, "description_en", translation.getDescriptionEn(), context, false);

                    java.time.LocalDateTime createdValue = createdAt != null ? createdAt : java.time.LocalDateTime.now();
                    if (ensureColumnExists(tableName, "created_at", false, context)) {
                        replaceColumns.add("created_at");
                        replaceParams.add(createdValue);
                    }
                    if (ensureColumnExists(tableName, "updated_at", false, context)) {
                        replaceColumns.add("updated_at");
                        replaceParams.add(createdValue);
                    }

                    String replaceSql = "REPLACE INTO " + tableName + " (" + String.join(", ", replaceColumns) + ") VALUES (" +
                            String.join(", ", Collections.nCopies(replaceColumns.size(), "?")) + ")";
                    jakarta.persistence.Query replaceQuery = entityManager.createNativeQuery(replaceSql);
                    for (int p = 0; p < replaceParams.size(); p++) {
                        replaceQuery.setParameter(p + 1, replaceParams.get(p));
                    }
                    replaceQuery.executeUpdate();
                    success++;
                } catch (Exception e) {
                    throw e; // 重新抛出异常，让外层catch处理
                }

            } catch (Exception e) {
                failed++;
                logger.warn("导入学派翻译失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("学派翻译", success, failed);
        logger.info("学派翻译数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    public void importContentTranslationsInTransaction(ImportResult result, List<String[]> data) {
        try {
            transactionTemplate.execute(status -> {
                importContentTranslations(result, data);
                return null;
            });
        } catch (Exception e) {
            logger.error("内容翻译导入事务失败", e);
            // 不要重新抛出异常，避免影响整体导入流程
        }
    }

    private void importContentTranslations(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入内容翻译数据，共 {} 条", data.size());
        int success = 0, failed = 0;

        for (String[] fields : data) {
            try {
                if (fields.length < 5) {
                    logger.warn("内容翻译字段数量不足（需要至少5个字段，实际{}个），跳过: {}", fields.length, Arrays.toString(fields));
                    failed++;
                    continue;
                }

                ContentTranslation translation = new ContentTranslation();
                translation.setId(Long.parseLong(fields[0]));

                // 解析内容
                if (!fields[1].isEmpty() && !fields[1].equals("null")) {
                    Optional<Content> content = contentRepository.findById(Long.parseLong(fields[1]));
                    if (content.isPresent()) {
                        translation.setContent(content.get());
                    } else {
                        logger.warn("内容ID '{}' 不存在，跳过内容翻译记录", fields[1]);
                        continue;
                    }
                }

                translation.setLanguageCode(fields[2]);
                translation.setContentEn(fields[3]);

                // 解析创建时间
                LocalDateTime createdAt = null;
                if (!fields[4].equals("未知时间") && !fields[4].isEmpty() && !fields[4].equals("null")) {
                    createdAt = LocalDateTime.parse(fields[4], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    translation.setCreatedAt(createdAt);
                }

                // 使用 REPLACE INTO 并动态适配列
                try {
                    final String tableName = "contents_translation";
                    final String context = "内容翻译导入";

                    List<String> replaceColumns = new ArrayList<>();
                    List<Object> replaceParams = new ArrayList<>();

                    addInsertColumn(replaceColumns, replaceParams, tableName, "id", translation.getId(), context, true);
                    addInsertColumn(replaceColumns, replaceParams, tableName, "content_id", translation.getContent().getId(), context, true);
                    addInsertColumn(replaceColumns, replaceParams, tableName, "language_code", translation.getLanguageCode(), context, true);
                    addInsertColumn(replaceColumns, replaceParams, tableName, "content_en", translation.getContentEn(), context, false);

                    java.time.LocalDateTime createdValue = createdAt != null ? createdAt : java.time.LocalDateTime.now();
                    if (ensureColumnExists(tableName, "created_at", false, context)) {
                        replaceColumns.add("created_at");
                        replaceParams.add(createdValue);
                    }
                    if (ensureColumnExists(tableName, "updated_at", false, context)) {
                        replaceColumns.add("updated_at");
                        replaceParams.add(createdValue);
                    }

                    String replaceSql = "REPLACE INTO " + tableName + " (" + String.join(", ", replaceColumns) + ") VALUES (" +
                            String.join(", ", Collections.nCopies(replaceColumns.size(), "?")) + ")";
                    jakarta.persistence.Query replaceQuery = entityManager.createNativeQuery(replaceSql);
                    for (int p = 0; p < replaceParams.size(); p++) {
                        replaceQuery.setParameter(p + 1, replaceParams.get(p));
                    }
                    replaceQuery.executeUpdate();
                    success++;
                } catch (Exception e) {
                    throw e; // 重新抛出异常，让外层catch处理
                }

            } catch (Exception e) {
                failed++;
                logger.warn("导入内容翻译失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("内容翻译", success, failed);
        logger.info("内容翻译数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    public void importPhilosopherTranslationsInTransaction(ImportResult result, List<String[]> data) {
        try {
            transactionTemplate.execute(status -> {
                importPhilosopherTranslations(result, data);
                return null;
            });
        } catch (Exception e) {
            logger.error("哲学家翻译导入事务失败", e);
            // 不要重新抛出异常，避免影响整体导入流程
        }
    }

    private void importPhilosopherTranslations(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入哲学家翻译数据，共 {} 条", data.size());
        int success = 0, failed = 0;

        for (String[] fields : data) {
            try {
                if (fields.length < 6) {
                    logger.warn("哲学家翻译字段数量不足（需要至少6个字段，实际{}个），跳过: {}", fields.length, Arrays.toString(fields));
                    failed++;
                    continue;
                }

                PhilosopherTranslation translation = new PhilosopherTranslation();
                translation.setId(Long.parseLong(fields[0]));

                // 解析哲学家
                if (!fields[1].isEmpty() && !fields[1].equals("null")) {
                    Optional<Philosopher> philosopher = philosopherRepository.findById(Long.parseLong(fields[1]));
                    if (philosopher.isPresent()) {
                        translation.setPhilosopher(philosopher.get());
                    } else {
                        logger.warn("哲学家ID '{}' 不存在，跳过哲学家翻译记录", fields[1]);
                        continue;
                    }
                }

                translation.setLanguageCode(fields[2]);
                translation.setNameEn(fields[3]);
                translation.setBiographyEn(fields[4]);

                // 解析创建时间
                LocalDateTime createdAt = null;
                if (!fields[5].equals("未知时间") && !fields[5].isEmpty() && !fields[5].equals("null")) {
                    createdAt = LocalDateTime.parse(fields[5], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    translation.setCreatedAt(createdAt);
                }

                // 使用 REPLACE INTO 并动态适配列
                try {
                    final String tableName = "philosophers_translation";
                    final String context = "哲学家翻译导入";

                    List<String> replaceColumns = new ArrayList<>();
                    List<Object> replaceParams = new ArrayList<>();

                    addInsertColumn(replaceColumns, replaceParams, tableName, "id", translation.getId(), context, true);
                    addInsertColumn(replaceColumns, replaceParams, tableName, "philosopher_id", translation.getPhilosopher().getId(), context, true);
                    addInsertColumn(replaceColumns, replaceParams, tableName, "language_code", translation.getLanguageCode(), context, true);
                    addInsertColumn(replaceColumns, replaceParams, tableName, "name_en", translation.getNameEn(), context, false);
                    addInsertColumn(replaceColumns, replaceParams, tableName, "biography_en", translation.getBiographyEn(), context, false);

                    java.time.LocalDateTime createdValue = createdAt != null ? createdAt : java.time.LocalDateTime.now();
                    if (ensureColumnExists(tableName, "created_at", false, context)) {
                        replaceColumns.add("created_at");
                        replaceParams.add(createdValue);
                    }
                    if (ensureColumnExists(tableName, "updated_at", false, context)) {
                        replaceColumns.add("updated_at");
                        replaceParams.add(createdValue);
                    }

                    String replaceSql = "REPLACE INTO " + tableName + " (" + String.join(", ", replaceColumns) + ") VALUES (" +
                            String.join(", ", Collections.nCopies(replaceColumns.size(), "?")) + ")";
                    jakarta.persistence.Query replaceQuery = entityManager.createNativeQuery(replaceSql);
                    for (int p = 0; p < replaceParams.size(); p++) {
                        replaceQuery.setParameter(p + 1, replaceParams.get(p));
                    }
                    replaceQuery.executeUpdate();
                    success++;
                } catch (Exception e) {
                    throw e; // 重新抛出异常，让外层catch处理
                }

            } catch (Exception e) {
                failed++;
                logger.warn("导入哲学家翻译失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("哲学家翻译", success, failed);
        logger.info("哲学家翻译数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    // 用户关注数据导入方法
    @Transactional
    public void importUserFollowsInTransaction(ImportResult result, List<String[]> data) {
        importUserFollows(result, data);
    }

    private void importUserFollows(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入用户关注数据，共 {} 条", data.size());
        int success = 0, failed = 0;

        for (String[] fields : data) {
            try {
                if (fields.length < 3) continue;

                UserFollow follow = new UserFollow();
                follow.setId(Long.parseLong(fields[0]));

                // 解析关注者
                if (!fields[1].isEmpty()) {
                    Optional<User> follower = userRepository.findById(Long.parseLong(fields[1]));
                    if (follower.isPresent()) {
                        follow.setFollower(follower.get());
                    } else {
                        logger.warn("关注者用户ID '{}' 不存在，跳过关注记录", fields[1]);
                        continue;
                    }
                }

                // 解析被关注者
                if (!fields[2].isEmpty()) {
                    Optional<User> following = userRepository.findById(Long.parseLong(fields[2]));
                    if (following.isPresent()) {
                        follow.setFollowing(following.get());
                    } else {
                        logger.warn("被关注者用户ID '{}' 不存在，跳过关注记录", fields[2]);
                        continue;
                    }
                }

                // 解析创建时间
                if (fields.length > 3 && !fields[3].equals("未知时间") && !fields[3].isEmpty() && !fields[3].equals("null")) {
                    follow.setCreatedAt(LocalDateTime.parse(fields[3], DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }

                userFollowRepository.save(follow);
                success++;

            } catch (Exception e) {
                failed++;
                logger.warn("导入用户关注失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("用户关注", success, failed);
        logger.info("用户关注数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    // 哲学家-学派关联数据导入方法
    public void importPhilosopherSchoolAssociationsInTransaction(ImportResult result, List<String[]> data) {
        try {
            transactionTemplate.execute(status -> {
                importPhilosopherSchoolAssociations(result, data);
                // 在事务内刷新缓存，确保之前导入的数据对后续查询可见
                entityManager.flush();
                entityManager.clear();
                return null;
            });
        } catch (Exception e) {
            logger.error("哲学家学派关联导入事务失败", e);
            // 不要重新抛出异常，避免影响整体导入流程
        }
    }

    private void importPhilosopherSchoolAssociations(ImportResult result, List<String[]> data) {
        if (data == null) return;

        logger.info("开始导入哲学家学派关联数据，共 {} 条", data.size());
        int success = 0, failed = 0;

        int rowIndex = 0;
        for (String[] fields : data) {
            try {
                rowIndex++;
                if (fields.length < 2) continue;

                String rawPhilosopher = fields[0] != null ? fields[0].trim() : "";
                String rawSchool = fields[1] != null ? fields[1].trim() : "";

                // 跳过表头或无效行
                if (rawPhilosopher.isEmpty() || rawSchool.isEmpty() ||
                    "哲学家ID".equals(rawPhilosopher) || "学派ID".equals(rawSchool) || "流派ID".equals(rawSchool)) {
                    logger.debug("跳过关联表头/空行: {}", java.util.Arrays.toString(fields));
                    continue;
                }

                Long philosopherId = null;
                Long schoolId = null;

                // 解析哲学家：优先按ID，否则按名称/英文名称回退
                try {
                    philosopherId = Long.parseLong(rawPhilosopher);
                } catch (NumberFormatException nf) {
                    try {
                        String findPhilosopherSql = "SELECT id FROM philosophers WHERE name = ? OR name_en = ? LIMIT 1";
                        jakarta.persistence.Query pq = entityManager.createNativeQuery(findPhilosopherSql);
                        pq.setParameter(1, rawPhilosopher);
                        pq.setParameter(2, rawPhilosopher);
                        java.util.List<?> pr = pq.getResultList();
                        if (!pr.isEmpty()) {
                            philosopherId = ((Number) pr.get(0)).longValue();
                            logger.debug("通过名称匹配到哲学家: '{}' -> {}", rawPhilosopher, philosopherId);
                        } else {
                            logger.warn("第{}行: 无法解析哲学家 '{}' 为有效ID或名称，跳过", rowIndex, rawPhilosopher);
                            failed++;
                            continue;
                        }
                    } catch (Exception ex) {
                        logger.warn("第{}行: 查找哲学家 '{}' 发生错误: {}", rowIndex, rawPhilosopher, ex.getMessage());
                        failed++;
                        continue;
                    }
                }

                // 解析学派：优先按ID，否则按名称/英文名称回退
                try {
                    schoolId = Long.parseLong(rawSchool);
                } catch (NumberFormatException nf) {
                    try {
                        String findSchoolSql = "SELECT id FROM schools WHERE name = ? OR name_en = ? LIMIT 1";
                        jakarta.persistence.Query sq = entityManager.createNativeQuery(findSchoolSql);
                        sq.setParameter(1, rawSchool);
                        sq.setParameter(2, rawSchool);
                        java.util.List<?> sr = sq.getResultList();
                        if (!sr.isEmpty()) {
                            schoolId = ((Number) sr.get(0)).longValue();
                            logger.debug("通过名称匹配到学派: '{}' -> {}", rawSchool, schoolId);
                        } else {
                            logger.warn("第{}行: 无法解析学派 '{}' 为有效ID或名称，跳过", rowIndex, rawSchool);
                            failed++;
                            continue;
                        }
                    } catch (Exception ex) {
                        logger.warn("第{}行: 查找学派 '{}' 发生错误: {}", rowIndex, rawSchool, ex.getMessage());
                        failed++;
                        continue;
                    }
                }

                // 查找哲学家
                Optional<Philosopher> philosopherOpt = philosopherRepository.findById(philosopherId);
                if (!philosopherOpt.isPresent()) {
                    logger.warn("哲学家ID '{}' 不存在，跳过关联", philosopherId);
                    failed++;
                    continue;
                }

                // 查找学派
                Optional<School> schoolOpt = schoolRepository.findById(schoolId);
                if (!schoolOpt.isPresent()) {
                    logger.warn("学派ID '{}' 不存在，跳过关联", schoolId);
                    failed++;
                    continue;
                }

                // 使用原生SQL插入关联关系，先删除现有记录再插入新记录
                try {
                    // 先删除现有关联记录
                    String deleteSql = "DELETE FROM philosopher_school WHERE philosopher_id = ? AND school_id = ?";
                    jakarta.persistence.Query deleteQuery = entityManager.createNativeQuery(deleteSql);
                    deleteQuery.setParameter(1, philosopherId);
                    deleteQuery.setParameter(2, schoolId);
                    deleteQuery.executeUpdate();
                    
                    // 插入新关联记录
                    String sql = "INSERT INTO philosopher_school (philosopher_id, school_id) VALUES (?, ?)";
                    jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
                    query.setParameter(1, philosopherId);
                    query.setParameter(2, schoolId);
                    int updated = query.executeUpdate();
                    
                    if (updated > 0) {
                        success++;
                        logger.debug("哲学家学派关联创建成功: philosopher={}, school={}", philosopherId, schoolId);
                    } else {
                        logger.warn("哲学家学派关联插入失败: philosopher={}, school={}", philosopherId, schoolId);
                        failed++;
                    }
                } catch (Exception sqlException) {
                    logger.error("创建哲学家学派关联时发生SQL异常: philosopher={}, school={}, 错误: {}", 
                               philosopherId, schoolId, sqlException.getMessage());
                    failed++;
                }

            } catch (Exception e) {
                failed++;
                logger.warn("导入哲学家学派关联失败: " + Arrays.toString(fields), e);
            }
        }

        result.addResult("哲学家学派关联", success, failed);
        logger.info("哲学家学派关联数据导入完成，成功: {}, 失败: {}", success, failed);
    }

    /**
     * 修复作者：从简单CSV中按 [内容ID, 作者ID] 批量设置作者，仅在当前作者为 NULL 时更新
     */
    public ImportResult repairAuthorsFromSimpleCsv(org.springframework.web.multipart.MultipartFile file) {
        ImportResult result = new ImportResult();
        int success = 0, failed = 0;
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(file.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] fields = parseCsvLine(line);
                // 跳过可能的表头
                if (!headerSkipped && fields.length >= 2 && ("内容ID".equals(fields[0]) || !fields[0].matches("\\d+"))) {
                    headerSkipped = true;
                    continue;
                }
                if (fields.length < 2) {
                    failed++;
                    continue;
                }
                try {
                    Long contentId = Long.parseLong(fields[0].trim());
                    Long userId = Long.parseLong(fields[1].trim());

                    // 校验用户是否存在
                    String checkUserSql = "SELECT id FROM users WHERE id = ? LIMIT 1";
                    jakarta.persistence.Query checkUserQuery = entityManager.createNativeQuery(checkUserSql);
                    checkUserQuery.setParameter(1, userId);
                    java.util.List<?> userRes = checkUserQuery.getResultList();
                    if (userRes.isEmpty()) {
                        logger.warn("作者修复跳过：用户不存在 userId={} (contentId={})", userId, contentId);
                        failed++;
                        continue;
                    }

                    // 校验内容是否存在
                    String checkContentSql = "SELECT id FROM contents WHERE id = ? LIMIT 1";
                    jakarta.persistence.Query checkContentQuery = entityManager.createNativeQuery(checkContentSql);
                    checkContentQuery.setParameter(1, contentId);
                    java.util.List<?> contentRes = checkContentQuery.getResultList();
                    if (contentRes.isEmpty()) {
                        logger.warn("作者修复跳过：内容不存在 contentId={}", contentId);
                        failed++;
                        continue;
                    }

                    // 仅当当前作者为 NULL 时更新
                    String updateSql = "UPDATE contents SET user_id = ? WHERE id = ? AND user_id IS NULL";
                    jakarta.persistence.Query updateQuery = entityManager.createNativeQuery(updateSql);
                    updateQuery.setParameter(1, userId);
                    updateQuery.setParameter(2, contentId);
                    int updated = updateQuery.executeUpdate();
                    if (updated > 0) {
                        success++;
                    } else {
                        // 未更新（可能已有作者或不满足条件）算失败以便关注
                        failed++;
                    }
                } catch (Exception perLineEx) {
                    failed++;
                    logger.warn("作者修复处理行失败: {}", java.util.Arrays.toString(fields), perLineEx);
                }
            }
            result.addResult("作者修复", success, failed);
            result.setSuccess(true);
            result.setMessage("作者修复完成");
        } catch (Exception e) {
            logger.error("作者修复失败", e);
            result.setSuccess(false);
            result.setMessage("作者修复失败: " + e.getMessage());
        }
        return result;
    }

    public static class ImportResult {
        private boolean success;
        private String message;
        private Map<String, ImportStats> results = new LinkedHashMap<>();
        private Map<String, List<String>> failureDetails = new LinkedHashMap<>();
        private int totalImported = 0;
        private int totalFailed = 0;

        public void addResult(String tableName, int success, int failed) {
            results.put(tableName, new ImportStats(success, failed));
            totalImported += success;
            totalFailed += failed;
        }

        // Getters and setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public Map<String, ImportStats> getResults() { return results; }

        public Map<String, List<String>> getFailureDetails() { return failureDetails; }

        private static final int MAX_FAILURE_DETAILS_PER_SECTION = 50;

        public void addFailureDetail(String tableName, String detail) {
            if (detail == null || detail.trim().isEmpty()) {
                return;
            }
            List<String> list = failureDetails.computeIfAbsent(tableName, key -> new ArrayList<>());
            if (list.size() >= MAX_FAILURE_DETAILS_PER_SECTION) {
                return;
            }
            list.add(detail);
        }

        public int getTotalImported() { return totalImported; }
        public int getTotalFailed() { return totalFailed; }

        public void setTotalImported(int totalImported) { this.totalImported = totalImported; }
        public void setTotalFailed(int totalFailed) { this.totalFailed = totalFailed; }

        public static class ImportStats {
            private int success;
            private int failed;

            public ImportStats(int success, int failed) {
                this.success = success;
                this.failed = failed;
            }

            public int getSuccess() { return success; }
            public int getFailed() { return failed; }
        }
    }
}
