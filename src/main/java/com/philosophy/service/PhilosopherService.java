package com.philosophy.service;

import com.philosophy.model.Philosopher;
import com.philosophy.model.Content;
import com.philosophy.model.School;
import com.philosophy.model.User;
import com.philosophy.repository.ContentRepository;
import com.philosophy.repository.PhilosopherRepository;
import com.philosophy.repository.PhilosopherTranslationRepository;
import com.philosophy.repository.UserContentEditRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PhilosopherService {

    private static final Logger logger = LoggerFactory.getLogger(PhilosopherService.class);

    private final PhilosopherRepository philosopherRepository;
    private final ContentRepository contentRepository;
    private final UserContentEditRepository userContentEditRepository;
    private final PhilosopherTranslationRepository philosopherTranslationRepository;
    private static final String UPLOAD_DIR = "uploads/"; // 上传目录

    public PhilosopherService(PhilosopherRepository philosopherRepository, ContentRepository contentRepository, UserContentEditRepository userContentEditRepository, PhilosopherTranslationRepository philosopherTranslationRepository) {
        this.philosopherRepository = philosopherRepository;
        this.contentRepository = contentRepository;
        this.userContentEditRepository = userContentEditRepository;
        this.philosopherTranslationRepository = philosopherTranslationRepository;
    }

    @Transactional(readOnly = true)
    public List<Philosopher> findAll() {
        return philosopherRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Philosopher> findById(Long id) {
        return philosopherRepository.findById(id);
    }

    @Transactional
    public Philosopher save(Philosopher philosopher) {
        return philosopherRepository.save(philosopher);
    }

    @Transactional
    public void deleteById(Long id) {
        // 首先删除引用此哲学家的用户内容编辑记录
        userContentEditRepository.deleteByPhilosopherId(id);
        // 强制刷新以确保UserContentEdit的删除被提交到数据库
        philosopherRepository.flush();
        
        // 删除哲学家的翻译记录
        philosopherTranslationRepository.deleteByPhilosopherId(id);
        // 强制刷新以确保翻译记录的删除被提交到数据库
        philosopherRepository.flush();
        
        // 然后处理引用此哲学家的内容 - 将它们断开关联
        List<Content> contents = contentRepository.findByPhilosopherId(id);
        if (!contents.isEmpty()) {
            for (Content content : contents) {
                content.setPhilosopher(null);
                contentRepository.save(content);
            }
            // 强制刷新以确保更改被提交
            contentRepository.flush();
        }

        // 现在安全地删除哲学家
        philosopherRepository.deleteById(id);
    }

    // 根据名称搜索哲学家
    @Transactional(readOnly = true)
    public List<Philosopher> findByNameContainingIgnoreCase(String name) {
        return philosopherRepository.findByNameContainingIgnoreCase(name);
    }

    // 根据学派ID查找哲学家
    @Transactional(readOnly = true)
    public List<Philosopher> findBySchoolId(Long schoolId) {
        return philosopherRepository.findBySchoolId(schoolId);
    }

    // AdminController需要的方法
    @Transactional(readOnly = true)
    public Long countPhilosophers() {
        return philosopherRepository.count();
    }

    @Transactional(readOnly = true)
    public long countPhilosophersBySchoolIds(List<Long> schoolIds) {
        return philosopherRepository.countBySchoolIds(schoolIds);
    }
    
    @Transactional(readOnly = true)
    public List<Philosopher> getPhilosophersBySchoolIds(List<Long> schoolIds) {
        return philosopherRepository.findBySchoolIds(schoolIds);
    }

    @Transactional(readOnly = true)
    public List<Philosopher> getAllPhilosophers() {
        return findAll();
    }

    @Transactional
    public Philosopher savePhilosopher(Philosopher philosopher) {
        // 直接保存哲学家，不重新计算流派
        return philosopherRepository.save(philosopher);
    }

    @Transactional
    public Philosopher savePhilosopherForAdmin(Philosopher philosopherFromForm, User editor) {
        Philosopher oldPhilosopherState = null;
        Philosopher philosopherToSave;

        if (philosopherFromForm.getId() != null) {
            philosopherToSave = philosopherRepository.findById(philosopherFromForm.getId())
                    .orElseThrow(() -> new RuntimeException("Philosopher not found with id: " + philosopherFromForm.getId()));

            oldPhilosopherState = new Philosopher();
            oldPhilosopherState.setName(philosopherToSave.getName());
            oldPhilosopherState.setBio(philosopherToSave.getBio());

            philosopherToSave.setName(philosopherFromForm.getName());
            philosopherToSave.setBio(philosopherFromForm.getBio());
            philosopherToSave.setEra(philosopherFromForm.getEra());
            // 只有在明确提供了新的 birthYear 时才更新，否则保留原有值
            if (philosopherFromForm.getBirthYear() != null) {
                philosopherToSave.setBirthYear(philosopherFromForm.getBirthYear());
            }
            // 只有在明确提供了新的 deathYear 时才更新，否则保留原有值
            if (philosopherFromForm.getDeathYear() != null) {
                philosopherToSave.setDeathYear(philosopherFromForm.getDeathYear());
            }
            // 只有在明确提供了新的 imageUrl 时才更新，否则保留原有的照片
            if (philosopherFromForm.getImageUrl() != null) {
                philosopherToSave.setImageUrl(philosopherFromForm.getImageUrl());
            }
            // 如果是新创建（用户字段为空），设置创建者
            if (philosopherToSave.getUser() == null) {
                philosopherToSave.setUser(editor);
            }
        } else {
            philosopherToSave = philosopherFromForm;
            // 为新创建的哲学家设置创建者
            philosopherToSave.setUser(editor);
        }

        Philosopher savedPhilosopher = philosopherRepository.save(philosopherToSave);

        return savedPhilosopher;
    }
    
    @Transactional
    public Philosopher savePhilosopherWithImage(Philosopher philosopher, MultipartFile imageFile) throws IOException {
        if (imageFile != null && !imageFile.isEmpty()) {
            String imageUrl = uploadImage(imageFile);
            philosopher.setImageUrl(imageUrl);
        }
        return savePhilosopher(philosopher);
    }
    
    @Transactional
    public Philosopher savePhilosopherWithSchoolRecalculation(Philosopher philosopher) {
        // 先保存哲学家以获取ID（如果是新哲学家）
        Philosopher savedPhilosopher = philosopherRepository.save(philosopher);
        
        // 自动根据关联内容推断流派
        List<Content> contents = contentRepository.findByPhilosopherId(savedPhilosopher.getId());
        
        if (!contents.isEmpty()) {
            Set<School> inferredSchools = new HashSet<>();
            
            for (Content content : contents) {
                if (content.getSchool() != null) {
                    // 添加内容直接关联的流派
                    inferredSchools.add(content.getSchool());
                    
                    // 添加该流派的所有父流派
                    School currentSchool = content.getSchool();
                    while (currentSchool.getParent() != null) {
                        currentSchool = currentSchool.getParent();
                        inferredSchools.add(currentSchool);
                    }
                }
            }
            
            // 如果推断出流派，更新哲学家
            if (!inferredSchools.isEmpty()) {
                savedPhilosopher.setSchools(new ArrayList<>(inferredSchools));
                savedPhilosopher = philosopherRepository.save(savedPhilosopher);
            } else {
                // 如果没有推断出流派，清空现有流派关联
                savedPhilosopher.setSchools(new ArrayList<>());
                savedPhilosopher = philosopherRepository.save(savedPhilosopher);
            }
        } else {
            // 如果没有内容，清空现有流派关联
            savedPhilosopher.setSchools(new ArrayList<>());
            savedPhilosopher = philosopherRepository.save(savedPhilosopher);
        }
        
        return savedPhilosopher;
    }

    @Transactional(readOnly = true)
    public Philosopher getPhilosopherById(Long id) {
        return findById(id).orElse(null);
    }

    @Transactional
    public void deletePhilosopher(Long id) {
        deleteById(id);
    }

    // 上传图片方法
    public String uploadImage(MultipartFile file) throws IOException {
        // 确保上传目录存在
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 生成唯一文件名
        String fileName = UUID.randomUUID().toString() + "-" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);

        // 保存文件
        Files.copy(file.getInputStream(), filePath);

        // 返回文件URL
        return "/" + UPLOAD_DIR + fileName;
    }

    // 删除图片文件方法
    public void deleteImageFile(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return;
        }
        
        try {
            // 从URL中提取文件路径
            // URL格式应该是 /uploads/filename 或类似的格式
            String filePath = imageUrl;
            if (filePath.startsWith("/")) {
                filePath = filePath.substring(1);
            }
            
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                logger.info("Deleted image file: {}", filePath);
            } else {
                logger.warn("Image file not found: {}", filePath);
            }
        } catch (IOException e) {
            logger.error("Error deleting image file: {}", imageUrl, e);
            // 不抛出异常，因为文件可能已经被删除或不存在
        }
    }

    // 获取精选哲学家（首页使用）
    @Transactional(readOnly = true)
    public List<Philosopher> getFeaturedPhilosophers() {
        // 这里可以返回所有哲学家，或者根据特定条件筛选
        return findAll();
    }

    // 搜索哲学家（支持关键词）
    @Transactional(readOnly = true)
    public List<Philosopher> searchPhilosophers(String query) {
        return philosopherRepository.searchByNameOrNameEn(query);
    }

    /**
     * 重新计算所有哲学家的流派关联
     * 当内容或流派发生变化时调用此方法
     */
    @Transactional
    public void recalculateAllPhilosopherSchools() {
        List<Philosopher> allPhilosophers = philosopherRepository.findAll();
        
        for (Philosopher philosopher : allPhilosophers) {
            savePhilosopherWithSchoolRecalculation(philosopher);
        }
    }

    /**
     * 重新计算特定哲学家的流派关联
     * 当哲学家的内容发生变化时调用此方法
     */
    @Transactional
    public void recalculatePhilosopherSchools(Long philosopherId) {
        Philosopher philosopher = philosopherRepository.findById(philosopherId).orElse(null);
        if (philosopher != null) {
            savePhilosopherWithSchoolRecalculation(philosopher);
        }
    }

    /**
     * 获取指定哲学家的内容，按用户角色优先级排序
     * 优先级：管理员和版主的内容优先，如果没有则显示用户写的点赞最多的内容
     */
    @Transactional(readOnly = true)
    public List<Content> getContentsByPhilosopherIdWithPriority(Long philosopherId) {
        try {
            // 获取该哲学家的所有内容（包括流派信息）
            List<Content> allContents = contentRepository.findByPhilosopherIdWithSchool(philosopherId);

            // 分离管理员/版主内容和用户内容
            List<Content> adminModeratorContents = new ArrayList<>();
            List<Content> userContents = new ArrayList<>();

            for (Content content : allContents) {
                if (isAdminOrModerator(content.getUser())) {
                    adminModeratorContents.add(content);
                } else {
                    userContents.add(content);
                }
            }

            // 对管理员/版主内容按角色优先级和点赞数排序
            adminModeratorContents.sort((c1, c2) -> {
                int priority1 = getUserPriority(c1.getUser());
                int priority2 = getUserPriority(c2.getUser());
                int roleComparison = Integer.compare(priority1, priority2);
                if (roleComparison != 0) {
                    return roleComparison;
                }
                return Integer.compare(c2.getLikeCount(), c1.getLikeCount());
            });

            // 对用户内容按点赞数降序排序
            userContents.sort((c1, c2) -> Integer.compare(c2.getLikeCount(), c1.getLikeCount()));

            // 合并结果：先显示管理员/版主内容，再显示用户内容
            List<Content> result = new ArrayList<>();
            result.addAll(adminModeratorContents);
            result.addAll(userContents);

            return result;
        } catch (Exception e) {
            logger.error("Error getting contents by philosopher ID with priority: {} - {}", philosopherId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取指定哲学家的内容（分页版本），按用户角色优先级排序 - 用于哲学家页面的无限滚动
     * 优先级：管理员和版主的内容优先，如果没有则显示用户写的点赞最多的内容
     * @param philosopherId 哲学家ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 包含内容列表和分页信息的Map
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getContentsByPhilosopherIdWithPriorityPaged(Long philosopherId, int page, int size) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        try {
            // 创建分页请求
            org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(page, size);
            
            // 获取分页数据（已按优先级排序）
            org.springframework.data.domain.Page<Content> contentPage = 
                contentRepository.findByPhilosopherIdWithPriorityPaged(philosopherId, pageable);
            
            result.put("contents", contentPage.getContent());
            result.put("hasMore", contentPage.hasNext());
            result.put("totalElements", contentPage.getTotalElements());
            result.put("totalPages", contentPage.getTotalPages());
            result.put("currentPage", page);
            
            return result;
        } catch (Exception e) {
            logger.error("Error getting contents by philosopher ID with priority (paged): {} - {}", philosopherId, e.getMessage(), e);
            result.put("contents", new ArrayList<>());
            result.put("hasMore", false);
            result.put("totalElements", 0L);
            return result;
        }
    }

    /**
     * 获取用户角色优先级
     * ADMIN = 1, MODERATOR = 2, 其他 = 3
     */
    private int getUserPriority(com.philosophy.model.User user) {
        if (user == null) {
            return 3; // 没有用户的默认为最低优先级
        }

        String role = user.getRole();
        if ("ADMIN".equals(role)) {
            return 1;
        } else if ("MODERATOR".equals(role)) {
            return 2;
        } else {
            return 3;
        }
    }

    /**
     * 判断用户是否为管理员或版主
     */
    private boolean isAdminOrModerator(com.philosophy.model.User user) {
        if (user == null) {
            return false;
        }
        String role = user.getRole();
        return "ADMIN".equals(role) || "MODERATOR".equals(role);
    }
}