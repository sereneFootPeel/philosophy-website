package com.philosophy.service;

import com.philosophy.model.School;
import com.philosophy.model.Philosopher;
import com.philosophy.model.Content;
import com.philosophy.model.User;
import com.philosophy.repository.SchoolRepository;
import com.philosophy.repository.PhilosopherRepository;
import com.philosophy.repository.ContentRepository;
import com.philosophy.repository.UserContentEditRepository;
import com.philosophy.repository.SchoolTranslationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SchoolService {

    private static final Logger logger = LoggerFactory.getLogger(SchoolService.class);

    private final SchoolRepository schoolRepository;
    private final PhilosopherRepository philosopherRepository;
    private final ContentRepository contentRepository;
    private final UserContentEditRepository userContentEditRepository;
    private final TranslationService translationService;
    private final UserFollowService userFollowService;
    private final SchoolTranslationRepository schoolTranslationRepository;

    public SchoolService(SchoolRepository schoolRepository, PhilosopherRepository philosopherRepository, ContentRepository contentRepository, UserContentEditRepository userContentEditRepository, TranslationService translationService, UserFollowService userFollowService, SchoolTranslationRepository schoolTranslationRepository) {
        this.schoolRepository = schoolRepository;
        this.philosopherRepository = philosopherRepository;
        this.contentRepository = contentRepository;
        this.userContentEditRepository = userContentEditRepository;
        this.translationService = translationService;
        this.userFollowService = userFollowService;
        this.schoolTranslationRepository = schoolTranslationRepository;
    }

    @Transactional(readOnly = true)
    public List<School> findAll() {
        return schoolRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<School> findById(Long id) {
        return schoolRepository.findById(id);
    }

    @Transactional
    public School save(School school) {
        return schoolRepository.save(school);
    }

    @Transactional
    public void deleteById(Long id) {
        schoolRepository.deleteById(id);
    }

    // 查找顶级学派（没有父学派的学派）
    @Transactional(readOnly = true)
    public List<School> findTopLevelSchools() {
        return schoolRepository.findByParentIsNullOrderByName();
    }

    // 根据父学派ID查找子学派
    @Transactional(readOnly = true)
    public List<School> findByParentId(Long parentId) {
        return schoolRepository.findByParentId(parentId);
    }

    // AdminController需要的方法
    @Transactional(readOnly = true)
    public Long countSchools() {
        return schoolRepository.count();
    }

    @Transactional(readOnly = true)
    public List<School> getAllSchools() {
        return findAll();
    }

    @Transactional
    public void saveSchool(School school) {
        save(school);
    }

    @Transactional
    public School saveSchoolForAdmin(School schoolFromForm, User editor) {
        School oldSchoolState = null;
        School schoolToSave;
        String newName = schoolFromForm.getName() != null ? schoolFromForm.getName().trim() : null;

        if (newName == null || newName.isEmpty()) {
            throw new IllegalArgumentException("学派名称不能为空");
        }

        if (schoolFromForm.getId() != null) {
            // 更新现有学校
            schoolToSave = schoolRepository.findById(schoolFromForm.getId())
                    .orElseThrow(() -> new RuntimeException("School not found with id: " + schoolFromForm.getId()));

            oldSchoolState = new School();
            oldSchoolState.setName(schoolToSave.getName());
            oldSchoolState.setDescription(schoolToSave.getDescription());

            // 检查名称是否已更改，如果更改了，检查新名称是否已存在
            if (!newName.equals(schoolToSave.getName())) {
                Optional<School> existingSchool = schoolRepository.findByName(newName);
                if (existingSchool.isPresent() && !existingSchool.get().getId().equals(schoolFromForm.getId())) {
                    throw new IllegalArgumentException("学派名称 '" + newName + "' 已存在，请使用其他名称");
                }
            }

            schoolToSave.setName(newName);
            schoolToSave.setDescription(schoolFromForm.getDescription());
            schoolToSave.setParent(schoolFromForm.getParent());
            // 如果是新创建（用户字段为空），设置创建者
            if (schoolToSave.getUser() == null) {
                schoolToSave.setUser(editor);
            }
        } else {
            // 创建新学校
            // 检查名称是否已存在
            if (schoolRepository.existsByName(newName)) {
                throw new IllegalArgumentException("学派名称 '" + newName + "' 已存在，请使用其他名称");
            }
            
            schoolToSave = schoolFromForm;
            schoolToSave.setName(newName);
            // 为新创建的流派设置创建者
            schoolToSave.setUser(editor);
        }

        School savedSchool = schoolRepository.save(schoolToSave);

        return savedSchool;
    }

    @Transactional(readOnly = true)
    public School getSchoolById(Long id) {
        return findById(id).orElse(null);
    }

    @Transactional
    public void deleteSchool(Long id) {
        // 先获取流派对象，确保在事务内加载所有需要的数据
        School school = schoolRepository.findById(id).orElse(null);
        if (school != null) {
            // 1. 处理与哲学家的多对多关联关系
            // 创建副本以避免在迭代过程中修改集合
            List<Philosopher> philosophers = new ArrayList<>(school.getPhilosophers());
            for (Philosopher philosopher : philosophers) {
                // 双向移除关联
                philosopher.removeSchool(school);
                school.removePhilosopher(philosopher);
            }
            
            // 2. 处理父子关系
            // 如果有父学派，从父学派中移除该学派
            School parent = school.getParent();
            if (parent != null) {
                parent.removeChild(school);
            }
            
            // 3. 处理关联的内容 - 自动关联到父流派
            List<Content> contents = new ArrayList<>(school.getContents());
            for (Content content : contents) {
                if (parent != null) {
                    // 如果有父流派，将内容关联到父流派
                    content.setSchool(parent);
                    parent.addContent(content);
                    contentRepository.save(content);
                    logger.info("Content {} reassigned from school {} to parent school {}", 
                               content.getId(), school.getName(), parent.getName());
                } else {
                    // 如果没有父流派（顶级流派），将内容设置为无流派关联
                    content.setSchool(null);
                    contentRepository.save(content);
                    logger.info("Content {} unassigned from school {} (no parent school)", 
                               content.getId(), school.getName());
                }
            }
            
            // 4. 删除关联的翻译记录
            schoolTranslationRepository.deleteBySchoolId(id);
            logger.info("Deleted all translations for school {}", school.getName());
            
            // 5. 注意：由于在实体类中设置了cascade = CascadeType.ALL, orphanRemoval = true
            // 子学派会自动被删除，不需要手动处理
            
            // 6. 最后删除流派
            schoolRepository.delete(school);
        }
    }

    // 获取所有学派（首页使用）
    @Transactional(readOnly = true)
    public List<School> findAllSchools() {
        return findAll();
    }

    // 根据学派ID查找哲学家
    @Transactional(readOnly = true)
    public List<Philosopher> getPhilosophersBySchoolId(Long id) {
        return philosopherRepository.findBySchoolId(id);
    }

    // 搜索学派（支持关键词）
    @Transactional(readOnly = true)
    public List<School> searchSchools(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return schoolRepository.searchByNameOrNameEn(query.trim());
    }
    
    // 获取指定流派及其所有子孙流派的ID集合
    @Transactional(readOnly = true)
    public List<Long> getSchoolIdWithDescendants(Long schoolId) {
        List<Long> schoolIds = new ArrayList<>();
        School school = schoolRepository.findById(schoolId).orElse(null);
        if (school != null) {
            collectSchoolIdsWithDescendants(school, schoolIds);
        }
        return schoolIds;
    }
    
    // 递归收集流派及其子孙流派的ID
    private void collectSchoolIdsWithDescendants(School school, List<Long> schoolIds) {
        if (school == null || schoolIds.contains(school.getId())) {
            return;
        }
        schoolIds.add(school.getId());
        if (school.getChildren() != null) {
            for (School child : school.getChildren()) {
                collectSchoolIdsWithDescendants(child, schoolIds);
            }
        }
    }
    
    // 根据流派ID集合查找哲学家
    @Transactional(readOnly = true)
    public List<Philosopher> getPhilosophersBySchoolIds(List<Long> schoolIds) {
        if (schoolIds == null || schoolIds.isEmpty()) {
            return philosopherRepository.findAll();
        }
        return philosopherRepository.findBySchoolIds(schoolIds);
    }
    
    // 获取指定流派的所有内容，包括直接关联和通过哲学家关联的内容
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolId(Long schoolId) {
        try {
            List<Long> schoolIds = getSchoolIdWithDescendants(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }
            List<Content> contents = contentRepository.findContentsBySchoolIds(schoolIds);
            // 过滤掉null值并确保所有必要字段都有值
            return contents.stream()
                    .filter(content -> content != null)
                    .collect(ArrayList::new, (list, content) -> {
                        // 确保内容不为null
                        if (content.getContent() == null) {
                            content.setContent("");
                        }
                        list.add(content);
                    }, ArrayList::addAll);
        } catch (Exception e) {
            // 记录异常并返回空列表
            logger.error("Error getting contents by school ID: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    // 获取指定流派及其所有子流派的内容（从上往下筛选）
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIdWithDescendants(Long schoolId) {
        try {
            List<Long> schoolIds = getSchoolIdWithDescendants(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }
            // 使用findContentsBySchoolIds方法，包含通过哲学家关联的内容
            List<Content> contents = contentRepository.findContentsBySchoolIds(schoolIds);
            // 过滤掉null值并确保所有必要字段都有值
            return contents.stream()
                    .filter(content -> content != null)
                    .collect(ArrayList::new, (list, content) -> {
                        // 确保内容不为null
                        if (content.getContent() == null) {
                            content.setContent("");
                        }
                        list.add(content);
                    }, ArrayList::addAll);
        } catch (Exception e) {
            // 记录异常并返回空列表
            logger.error("Error getting contents by school ID with descendants: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    // 获取指定流派的直接内容（不包含子流派）
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIdOnly(Long schoolId) {
        try {
            // 使用findAllBySchoolIdWithPhilosopher方法，包含通过哲学家关联的内容
            List<Content> contents = contentRepository.findAllBySchoolIdWithPhilosopher(schoolId);
            // 过滤掉null值并确保所有必要字段都有值
            return contents.stream()
                    .filter(content -> content != null)
                    .collect(ArrayList::new, (list, content) -> {
                        // 确保内容不为null
                        if (content.getContent() == null) {
                            content.setContent("");
                        }
                        list.add(content);
                    }, ArrayList::addAll);
        } catch (Exception e) {
            // 记录异常并返回空列表
            logger.error("Error getting contents by school ID only: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    // 获取指定流派的直接哲学家（不包含子流派）
    @Transactional(readOnly = true)
    public List<Philosopher> getPhilosophersBySchoolIdOnly(Long schoolId) {
        try {
            return philosopherRepository.findBySchoolIds(List.of(schoolId));
        } catch (Exception e) {
            logger.error("Error getting philosophers by school ID only: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    // 获取指定流派及其所有父流派的内容（从子流派开始计算，包含所有父流派）
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIdWithAncestors(Long schoolId) {
        try {
            List<Long> schoolIds = getSchoolIdWithAncestors(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }
            List<Content> contents = contentRepository.findContentsBySchoolIds(schoolIds);
            // 过滤掉null值并确保所有必要字段都有值
            return contents.stream()
                    .filter(content -> content != null)
                    .collect(ArrayList::new, (list, content) -> {
                        // 确保内容不为null
                        if (content.getContent() == null) {
                            content.setContent("");
                        }
                        list.add(content);
                    }, ArrayList::addAll);
        } catch (Exception e) {
            // 记录异常并返回空列表
            logger.error("Error getting contents by school ID with ancestors: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    // 获取指定流派及其所有子流派的哲学家（从上往下筛选）
    @Transactional(readOnly = true)
    public List<Philosopher> getPhilosophersBySchoolIdWithDescendants(Long schoolId) {
        try {
            List<Long> schoolIds = getSchoolIdWithDescendants(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }
            return philosopherRepository.findBySchoolIds(schoolIds);
        } catch (Exception e) {
            logger.error("Error getting philosophers by school ID with descendants: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    // 获取指定流派及其所有父流派的哲学家（从子流派开始计算，包含所有父流派）
    @Transactional(readOnly = true)
    public List<Philosopher> getPhilosophersBySchoolIdWithAncestors(Long schoolId) {
        try {
            List<Long> schoolIds = getSchoolIdWithAncestors(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }
            return philosopherRepository.findBySchoolIds(schoolIds);
        } catch (Exception e) {
            logger.error("Error getting philosophers by school ID with ancestors: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    // 获取指定流派及其所有父流派的ID集合（从子流派向上追溯）
    @Transactional(readOnly = true)
    public List<Long> getSchoolIdWithAncestors(Long schoolId) {
        List<Long> schoolIds = new ArrayList<>();
        School school = schoolRepository.findById(schoolId).orElse(null);
        if (school != null) {
            collectSchoolIdsWithAncestors(school, schoolIds);
        }
        return schoolIds;
    }
    
    // 递归收集流派及其所有父流派的ID（向上追溯）
    private void collectSchoolIdsWithAncestors(School school, List<Long> schoolIds) {
        if (school == null || schoolIds.contains(school.getId())) {
            return;
        }
        schoolIds.add(school.getId());
        
        // 向上追溯父流派
        School parent = school.getParent();
        if (parent != null) {
            collectSchoolIdsWithAncestors(parent, schoolIds);
        }
    }
    
    // ==================== 多语言支持方法 ====================
    
    /**
     * 获取流派的显示名称（支持多语言）
     */
    public String getSchoolDisplayName(School school, String languageCode) {
        return translationService.getSchoolDisplayName(school, languageCode);
    }
    
    /**
     * 获取流派的显示描述（支持多语言）
     */
    public String getSchoolDisplayDescription(School school, String languageCode) {
        return translationService.getSchoolDisplayDescription(school, languageCode);
    }
    
    /**
     * 获取多个流派的显示名称映射（支持多语言）
     */
    public Map<Long, String> getSchoolDisplayNames(List<School> schools, String languageCode) {
        return translationService.getSchoolDisplayNames(schools, languageCode);
    }
    
    /**
     * 获取内容的显示文本（支持多语言）
     */
    public String getContentDisplayText(Content content, String languageCode) {
        return translationService.getContentDisplayText(content, languageCode);
    }
    
    /**
     * 获取多个内容的显示文本映射（支持多语言）
     */
    public Map<Long, String> getContentDisplayTexts(List<Content> contents, String languageCode) {
        return translationService.getContentDisplayTexts(contents, languageCode);
    }
    
    /**
     * 保存流派翻译
     */
    @Transactional
    public void saveSchoolTranslation(Long schoolId, String languageCode, String nameEn, String descriptionEn) {
        translationService.saveSchoolTranslation(schoolId, languageCode, nameEn, descriptionEn);
    }
    
    /**
     * 保存内容翻译
     */
    @Transactional
    public void saveContentTranslation(Long contentId, String languageCode, String contentEn) {
        translationService.saveContentTranslation(contentId, languageCode, contentEn);
    }

    /**
     * 获取指定流派的内容，按用户角色优先级排序
     * 优先级：管理员和版主的内容优先，如果没有则显示用户写的点赞最多的内容
     */
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIdWithPriority(Long schoolId) {
        try {
            List<Long> schoolIds = getSchoolIdWithDescendants(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }

            // 获取该流派的所有内容
            List<Content> allContents = contentRepository.findContentsBySchoolIds(schoolIds);

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
            logger.error("Error getting contents by school ID with priority: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取指定流派及其所有父流派的内容，按用户角色优先级排序
     * 优先级：管理员和版主的内容优先，如果没有则显示用户写的点赞最多的内容
     */
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIdWithAncestorsPriority(Long schoolId) {
        try {
            List<Long> schoolIds = getSchoolIdWithAncestors(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }

            // 获取该流派及其父流派的所有内容
            List<Content> allContents = contentRepository.findContentsBySchoolIds(schoolIds);

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
            logger.error("Error getting contents by school ID with ancestors priority: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取用户角色优先级
     * ADMIN = 1, MODERATOR = 2, 其他 = 3
     */
    private int getUserPriority(User user) {
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
    private boolean isAdminOrModerator(User user) {
        if (user == null) {
            return false;
        }
        String role = user.getRole();
        return "ADMIN".equals(role) || "MODERATOR".equals(role);
    }

    /**
     * 获取指定流派的所有内容（用于 contents 页面）
     * 显示该流派和子流派的所有内容，包括用户、版主、管理员的内容，不按优先级筛选
     */
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIdAll(Long schoolId) {
        try {
            List<Long> schoolIds = getSchoolIdWithDescendants(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }

            // 获取该流派的所有内容，不按优先级排序
            List<Content> allContents = contentRepository.findContentsBySchoolIds(schoolIds);

            // 过滤掉null值并确保所有必要字段都有值
            return allContents.stream()
                    .filter(content -> content != null)
                    .collect(ArrayList::new, (list, content) -> {
                        // 确保内容不为null
                        if (content.getContent() == null) {
                            content.setContent("");
                        }
                        list.add(content);
                    }, ArrayList::addAll);
        } catch (Exception e) {
            logger.error("Error getting all contents by school ID: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取指定流派的内容，只显示管理员、版主和用户关注的作者的内容
     * 用于 /schools/filter/{id} 端点
     */
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIdFiltered(Long schoolId, Long currentUserId) {
        try {
            List<Long> schoolIds = getSchoolIdWithDescendants(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }

            // 获取该流派的所有内容
            List<Content> allContents = contentRepository.findContentsBySchoolIds(schoolIds);

            // 获取用户关注的所有作者ID
            List<Long> followingIds = new ArrayList<>();
            if (currentUserId != null) {
                followingIds = userFollowService.getFollowingIds(currentUserId);
            }

            // 过滤内容：只显示管理员、版主和用户关注的作者的内容
            List<Content> filteredContents = new ArrayList<>();
            for (Content content : allContents) {
                if (content == null) {
                    continue;
                }
                
                User contentUser = content.getUser();
                if (contentUser == null) {
                    continue;
                }

                // 检查是否为管理员或版主
                if (isAdminOrModerator(contentUser)) {
                    filteredContents.add(content);
                } else if (currentUserId != null && followingIds.contains(contentUser.getId())) {
                    // 检查是否为用户关注的作者
                    filteredContents.add(content);
                }
            }

            // 按优先级排序：管理员和版主的内容优先，然后是按点赞数排序
            filteredContents.sort((c1, c2) -> {
                int priority1 = getUserPriority(c1.getUser());
                int priority2 = getUserPriority(c2.getUser());
                int roleComparison = Integer.compare(priority1, priority2);
                if (roleComparison != 0) {
                    return roleComparison;
                }
                return Integer.compare(c2.getLikeCount(), c1.getLikeCount());
            });

            // 确保所有必要字段都有值
            return filteredContents.stream()
                    .collect(ArrayList::new, (list, content) -> {
                        if (content.getContent() == null) {
                            content.setContent("");
                        }
                        list.add(content);
                    }, ArrayList::addAll);
        } catch (Exception e) {
            logger.error("Error getting filtered contents by school ID: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取指定流派的内容，只包含管理员和版主编辑的内容（用于 schools 页面）
     */
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIdAdminModeratorOnly(Long schoolId) {
        try {
            List<Long> schoolIds = getSchoolIdWithDescendants(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }

            // 获取该流派的管理员和版主内容
            List<Content> adminModeratorContents = contentRepository.findBySchoolIdsAdminModeratorOnly(schoolIds);

            // 过滤掉null值并确保所有必要字段都有值
            return adminModeratorContents.stream()
                    .filter(content -> content != null)
                    .collect(ArrayList::new, (list, content) -> {
                        // 确保内容不为null
                        if (content.getContent() == null) {
                            content.setContent("");
                        }
                        list.add(content);
                    }, ArrayList::addAll);
        } catch (Exception e) {
            logger.error("Error getting admin/moderator contents by school ID: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取指定流派的内容（分页），只包含管理员和版主编辑的内容（用于 schools 页面的无限滚动）
     * @param schoolId 流派ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 内容列表和是否有更多数据的结果对象
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getContentsBySchoolIdAdminModeratorOnlyPaged(Long schoolId, int page, int size) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Long> schoolIds = getSchoolIdWithDescendants(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                result.put("contents", new ArrayList<>());
                result.put("hasMore", false);
                result.put("totalElements", 0L);
                return result;
            }

            // 创建分页请求
            org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(page, size);

            // 获取该流派的管理员和版主内容（分页）
            org.springframework.data.domain.Page<Content> contentPage = 
                contentRepository.findBySchoolIdsAdminModeratorOnlyPaged(schoolIds, pageable);

            // 过滤掉null值并确保所有必要字段都有值
            List<Content> contents = contentPage.getContent().stream()
                    .filter(content -> content != null)
                    .peek(content -> {
                        // 确保内容不为null
                        if (content.getContent() == null) {
                            content.setContent("");
                        }
                    })
                    .collect(java.util.stream.Collectors.toList());

            result.put("contents", contents);
            result.put("hasMore", contentPage.hasNext());
            result.put("totalElements", contentPage.getTotalElements());
            result.put("totalPages", contentPage.getTotalPages());
            result.put("currentPage", page);

            return result;
        } catch (Exception e) {
            logger.error("Error getting admin/moderator contents by school ID (paged): {} - {}", schoolId, e.getMessage(), e);
            result.put("contents", new ArrayList<>());
            result.put("hasMore", false);
            result.put("totalElements", 0L);
            return result;
        }
    }

    /**
     * 获取指定流派及其所有父流派的内容，只包含管理员和版主编辑的内容（用于 schools 页面）
     */
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIdWithAncestorsAdminModeratorOnly(Long schoolId) {
        try {
            List<Long> schoolIds = getSchoolIdWithAncestors(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }

            // 获取该流派及其父流派的管理员和版主内容
            List<Content> adminModeratorContents = contentRepository.findBySchoolIdsAdminModeratorOnly(schoolIds);

            // 过滤掉null值并确保所有必要字段都有值
            return adminModeratorContents.stream()
                    .filter(content -> content != null)
                    .collect(ArrayList::new, (list, content) -> {
                        // 确保内容不为null
                        if (content.getContent() == null) {
                            content.setContent("");
                        }
                        list.add(content);
                    }, ArrayList::addAll);
        } catch (Exception e) {
            logger.error("Error getting admin/moderator contents by school ID with ancestors: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取指定流派的内容，包含管理员、版主编辑的内容以及用户点赞的作者的内容（用于 schools/filter 页面）
     */
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIdWithLikedAuthors(Long schoolId, Long currentUserId) {
        try {
            List<Long> schoolIds = getSchoolIdWithDescendants(schoolId);
            if (schoolIds == null || schoolIds.isEmpty()) {
                return new ArrayList<>();
            }

            // 如果用户未登录，只返回管理员和版主的内容
            if (currentUserId == null) {
                return getContentsBySchoolIdAdminModeratorOnly(schoolId);
            }

            // 获取该流派的管理员、版主内容以及用户点赞的作者的内容
            List<Content> contents = contentRepository.findBySchoolIdsWithLikedAuthors(schoolIds, currentUserId);

            // 过滤掉null值并确保所有必要字段都有值
            return contents.stream()
                    .filter(content -> content != null)
                    .collect(ArrayList::new, (list, content) -> {
                        // 确保内容不为null
                        if (content.getContent() == null) {
                            content.setContent("");
                        }
                        list.add(content);
                    }, ArrayList::addAll);
        } catch (Exception e) {
            logger.error("Error getting contents with liked authors by school ID: {} - {}", schoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取版主可管理的流派列表（包括负责的流派及其子流派）
     * @param moderatorAssignedSchoolId 版主负责的流派ID
     * @return 可管理的流派列表
     */
    @Transactional(readOnly = true)
    public List<School> getModeratorManageableSchools(Long moderatorAssignedSchoolId) {
        if (moderatorAssignedSchoolId == null) {
            return new ArrayList<>();
        }

        try {
            // 获取版主负责的流派
            School assignedSchool = findById(moderatorAssignedSchoolId).orElse(null);
            if (assignedSchool == null) {
                return new ArrayList<>();
            }

            List<School> manageableSchools = new ArrayList<>();
            manageableSchools.add(assignedSchool);

            // 递归添加所有子流派
            addChildSchoolsRecursive(manageableSchools, assignedSchool);

            return manageableSchools;
        } catch (Exception e) {
            logger.error("Error getting moderator manageable schools: {} - {}", moderatorAssignedSchoolId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 递归添加子流派到列表中
     */
    private void addChildSchoolsRecursive(List<School> schools, School parent) {
        if (parent.getChildren() != null && !parent.getChildren().isEmpty()) {
            for (School child : parent.getChildren()) {
                schools.add(child);
                addChildSchoolsRecursive(schools, child);
            }
        }
    }

    /**
     * 验证版主是否有权限管理指定的流派
     * @param moderatorAssignedSchoolId 版主负责的流派ID
     * @param targetSchoolId 目标流派ID
     * @return 是否有权限
     */
    @Transactional(readOnly = true)
    public boolean canModeratorManageSchool(Long moderatorAssignedSchoolId, Long targetSchoolId) {
        if (moderatorAssignedSchoolId == null || targetSchoolId == null) {
            return false;
        }

        // 获取版主可管理的流派ID列表
        List<Long> manageableSchoolIds = getSchoolIdWithDescendants(moderatorAssignedSchoolId);
        return manageableSchoolIds.contains(targetSchoolId);
    }

    /**
     * 获取版主可管理的流派ID列表（包括负责的流派及其子流派）
     * @param moderatorAssignedSchoolId 版主负责的流派ID
     * @return 可管理的流派ID列表
     */
    @Transactional(readOnly = true)
    public List<Long> getModeratorManageableSchoolIds(Long moderatorAssignedSchoolId) {
        if (moderatorAssignedSchoolId == null) {
            return new ArrayList<>();
        }
        return getSchoolIdWithDescendants(moderatorAssignedSchoolId);
    }
}