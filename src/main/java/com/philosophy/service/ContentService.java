package com.philosophy.service;

import com.philosophy.model.Content;
import com.philosophy.model.School;
import com.philosophy.model.User;
import com.philosophy.model.UserContentEdit;
import com.philosophy.repository.ContentRepository;
import com.philosophy.repository.UserContentEditRepository;
import com.philosophy.service.ModeratorBlockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ContentService {

    private final ContentRepository contentRepository;
    private final PhilosopherService philosopherService;
    private final UserContentEditRepository userContentEditRepository;
    private final UserBlockService userBlockService;
    private final ModeratorBlockService moderatorBlockService;
    private final SchoolService schoolService;

    private static final Logger logger = LoggerFactory.getLogger(ContentService.class);

    public ContentService(ContentRepository contentRepository, PhilosopherService philosopherService,
                         UserContentEditRepository userContentEditRepository, UserBlockService userBlockService,
                         ModeratorBlockService moderatorBlockService,
                         SchoolService schoolService) {
        this.contentRepository = contentRepository;
        this.philosopherService = philosopherService;
        this.userContentEditRepository = userContentEditRepository;
        this.userBlockService = userBlockService;
        this.moderatorBlockService = moderatorBlockService;
        this.schoolService = schoolService;
    }

    @Transactional(readOnly = true)
    public List<Content> findAll() {
        // 使用自定义查询方法，预先加载关联的philosopher对象
        return contentRepository.findAllWithPhilosopher(); // 已配置JOIN FETCH预加载
    }

    @Transactional(readOnly = true)
    public Optional<Content> findById(Long id) {
        return contentRepository.findByIdWithPhilosopher(id);
    }

    @Transactional
    public Content save(Content content) {
        // 获取原始内容以检查相关信息
        Long originalPhilosopherId = null;
        if (content.getId() != null) {
            Content originalContent = contentRepository.findById(content.getId()).orElse(null);
            if (originalContent != null) {
                if (originalContent.getPhilosopher() != null) {
                    originalPhilosopherId = originalContent.getPhilosopher().getId();
                }
            }
        }

        Content savedContent = contentRepository.save(content);

        // 获取新的哲学家ID
        Long newPhilosopherId = null;
        if (savedContent.getPhilosopher() != null) {
            newPhilosopherId = savedContent.getPhilosopher().getId();
        }

        // 只要内容有关联的哲学家，就重新计算该哲学家的流派
        if (newPhilosopherId != null) {
            philosopherService.recalculatePhilosopherSchools(newPhilosopherId);
        }

        // 如果哲学家被更改，也需要重新计算原哲学家的流派
        if (originalPhilosopherId != null && !originalPhilosopherId.equals(newPhilosopherId)) {
            philosopherService.recalculatePhilosopherSchools(originalPhilosopherId);
        }

        return savedContent;
    }

    @Transactional
    public Content saveContentWithUser(Content content, User user) {
        // 仅在创建新内容时设置创建者，编辑时不覆盖原作者
        if (content.getId() == null && content.getUser() == null) {
            content.setUser(user);
        }

        return save(content);
    }

    @Transactional
    public Content saveContentForAdmin(Content contentToSave, User editor) {
        // 新建时设置作者；编辑已有内容时保持原作者不变
        if (contentToSave.getId() == null || contentToSave.getUser() == null) {
            contentToSave.setUser(editor);
        }

        Content savedContent = save(contentToSave);

        return savedContent;
    }

    // 根据用户ID获取内容
    public List<Content> getContentsByUserId(Long userId) {
        List<Content> contents = contentRepository.findByUserId(userId);
        if (contents == null) {
            return new ArrayList<>();
        }
        // 过滤掉null值
        return contents.stream()
                .filter(content -> content != null)
                .collect(ArrayList::new, (list, content) -> {
                    // 确保内容不为null
                    if (content.getContent() == null) {
                        content.setContent("");
                    }
                    list.add(content);
                }, ArrayList::addAll);
    }

    // 根据用户ID统计内容数量
    public long countContentsByUserId(Long userId) {
        return contentRepository.countByUserId(userId);
    }

    @Transactional
    public void deleteById(Long id) {
        Content content = contentRepository.findByIdWithPhilosopher(id).orElse(null);
        Long philosopherId = null;

        if (content != null && content.getPhilosopher() != null) {
            philosopherId = content.getPhilosopher().getId();
        }

        // 先删除相关的用户内容编辑记录，避免外键约束错误
        userContentEditRepository.deleteByOriginalContentId(id);

        // 使用不依赖版本字段的删除方法
        contentRepository.deleteByIdWithoutVersion(id);

        // 删除内容后重新计算相关哲学家的流派
        if (philosopherId != null) {
            philosopherService.recalculatePhilosopherSchools(philosopherId);
        }
    }

    @Transactional
    public void saveContent(Content content) {
        Content savedContent = save(content);
        
        // 只有在内容有关联哲学家时才重新计算流派
        if (savedContent.getPhilosopher() != null) {
            philosopherService.recalculatePhilosopherSchools(savedContent.getPhilosopher().getId());
        }
    }

    // 根据哲学家ID查找内容
    @Transactional(readOnly = true)
    public List<Content> findByPhilosopherId(Long philosopherId) {
        return contentRepository.findByPhilosopherId(philosopherId);
    }
    
    // 根据哲学家ID查找内容（预加载流派关系）
    @Transactional(readOnly = true)
    public List<Content> findByPhilosopherIdWithSchool(Long philosopherId) {
        return contentRepository.findByPhilosopherIdWithSchool(philosopherId);
    }

    
    // 根据流派ID查找内容
    @Transactional(readOnly = true)
    public List<Content> findByPhilosopherSchoolsId(Long schoolId) {
        return contentRepository.findByPhilosopherSchoolsId(schoolId);
    }
    
    // 获取所有关联指定流派的内容（包括直接关联和通过哲学家关联）
    @Transactional(readOnly = true)
    public List<Content> findAllBySchoolIdWithPhilosopher(Long schoolId) {
        try {
            // 使用JPQL查询获取关联内容，避免懒加载问题
            return contentRepository.findAllBySchoolIdWithPhilosopher(schoolId);
        } catch (Exception e) {
            // 如果出现异常，返回空列表
            return new ArrayList<>();
        }
    }

    // AdminController需要的方法
    @Transactional(readOnly = true)
    public Long countContents() {
        return contentRepository.count();
    }

    @Transactional(readOnly = true)
    public long countContentsBySchoolIds(List<Long> schoolIds) {
        return contentRepository.countBySchoolIds(schoolIds);
    }
    
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIds(List<Long> schoolIds) {
        return contentRepository.findContentsBySchoolIds(schoolIds);
    }

    @Transactional(readOnly = true)
    public List<Content> getAllContents() {
        return contentRepository.findAll();
    }

    // 删除以下重复方法定义
    // @Transactional(readOnly = true)
    // public List<Content> getAllContents() {
    //     return findAll();
    // }

    @Transactional(readOnly = true)
    public Content getContentById(Long id) {
        return findById(id).orElse(null);
    }

    @Transactional
    public void deleteContent(Long id) {
        Content content = contentRepository.findById(id).orElse(null);
        if (content != null) {
            // 先删除相关的用户内容编辑记录
            userContentEditRepository.deleteByOriginalContentId(id);
            
            Long philosopherId = null;
            if (content.getPhilosopher() != null) {
                philosopherId = content.getPhilosopher().getId();
            }
            
            // 使用不依赖版本字段的删除方法
            contentRepository.deleteByIdWithoutVersion(id);

            // 如果内容有关联的哲学家，则重新计算其流派
            if (philosopherId != null) {
                philosopherService.recalculatePhilosopherSchools(philosopherId);
            }
        }
    }
    
    // 搜索内容（支持关键词）
    @Transactional(readOnly = true)
    public List<Content> searchContents(String query) {
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return contentRepository.findByContentContainingIgnoreCase(query.trim());
    }

    // 获取指定哲学家和流派的内容，按用户角色优先级排序
    @Transactional(readOnly = true)
    public List<Content> findByPhilosopherAndSchoolWithPriority(Long philosopherId, Long schoolId) {
        return contentRepository.findByPhilosopherAndSchoolWithPriority(philosopherId, schoolId);
    }

    // 获取指定哲学家和流派的内容，按用户角色优先级排序（包含用户编辑的内容）
    @Transactional(readOnly = true)
    public List<Content> findByPhilosopherAndSchoolWithUserPriority(Long philosopherId, Long schoolId) {
        return contentRepository.findByPhilosopherAndSchoolWithUserPriority(philosopherId, schoolId);
    }
    
    // 获取指定哲学家和流派的所有内容（包括用户编辑的内容）
    @Transactional(readOnly = true)
    public List<Content> findByPhilosopherAndSchoolAll(Long philosopherId, Long schoolId) {
        // 获取原始内容
        List<Content> originalContents = contentRepository.findByPhilosopherAndSchoolAll(philosopherId, schoolId);

        // 获取用户编辑的内容（待审核和已通过的）
        List<UserContentEdit> userEdits = userContentEditRepository.findByPhilosopherIdAndSchoolId(philosopherId, schoolId);

        // 合并内容：将用户编辑的内容添加到原始内容列表中
        // 注意：这里我们需要创建一个包装类或者修改返回类型来同时包含两种类型的内容
        // 暂时只返回原始内容，用户编辑内容需要单独处理
        return originalContents;
    }

    // 获取指定哲学家和流派的原始内容
    @Transactional(readOnly = true)
    public List<Content> findOriginalContentsByPhilosopherAndSchool(Long philosopherId, Long schoolId) {
        return contentRepository.findByPhilosopherAndSchoolAll(philosopherId, schoolId);
    }

    // 获取指定哲学家和流派的用户编辑内容
    @Transactional(readOnly = true)
    public List<UserContentEdit> findUserEditsByPhilosopherAndSchool(Long philosopherId, Long schoolId) {
        return userContentEditRepository.findByPhilosopherIdAndSchoolId(philosopherId, schoolId);
    }
    
    // 获取指定流派的内容，只包含管理员和版主编辑的内容
    @Transactional(readOnly = true)
    public List<Content> findBySchoolIdsAdminModeratorOnly(List<Long> schoolIds) {
        return contentRepository.findBySchoolIdsAdminModeratorOnly(schoolIds);
    }


    /**
     * 根据用户ID查找内容，支持隐私过滤
     * @param userId 用户ID
     * @param currentUser 当前用户（用于权限检查）
     * @return 过滤后的内容列表
     */
    @Transactional(readOnly = true)
    public List<Content> getContentsByUserIdWithPrivacyFilter(Long userId, User currentUser) {
        List<Content> allContents = getContentsByUserId(userId);
        return filterContentsByPrivacy(allContents, currentUser);
    }

    /**
     * 根据流派ID查找内容，支持隐私过滤
     * @param schoolId 流派ID
     * @param currentUser 当前用户（用于权限检查）
     * @return 过滤后的内容列表
     */
    @Transactional(readOnly = true)
    public List<Content> getContentsBySchoolIdWithPrivacyFilter(Long schoolId, User currentUser) {
        List<Content> allContents = findAllBySchoolIdWithPhilosopher(schoolId);
        return filterContentsByPrivacy(allContents, currentUser);
    }

    /**
     * 根据哲学家ID查找内容，支持隐私过滤
     * @param philosopherId 哲学家ID
     * @param currentUser 当前用户（用于权限检查）
     * @return 过滤后的内容列表
     */
    @Transactional(readOnly = true)
    public List<Content> findByPhilosopherIdWithPrivacyFilter(Long philosopherId, User currentUser) {
        List<Content> allContents = findByPhilosopherId(philosopherId);
        return filterContentsByPrivacy(allContents, currentUser);
    }

    /**
     * 设置内容隐私状态
     * @param contentId 内容ID
     * @param isPrivate 是否私密
     * @param currentUser 当前用户
     * @return 是否成功
     */
    @Transactional
    public boolean setContentPrivacy(Long contentId, boolean isPrivate, User currentUser) {
        Content content = getContentById(contentId);
        if (content == null) {
            return false;
        }
        
        // 只有内容作者或管理员可以设置隐私状态
        if (!content.getUser().getId().equals(currentUser.getId()) && 
            !"ADMIN".equals(currentUser.getRole())) {
            return false;
        }
        
        content.setPrivate(isPrivate);
        content.setPrivacySetBy(currentUser);
        content.setPrivacySetAt(java.time.LocalDateTime.now());
        save(content);
        
        return true;
    }

    /**
     * 屏蔽内容
     * @param contentId 内容ID
     * @param blockedBy 屏蔽者
     * @return 是否成功
     */
    @Transactional
    public boolean blockContent(Long contentId, User blockedBy) {
        Content content = getContentById(contentId);
        if (content == null) {
            return false;
        }
        
        content.setBlocked(true);
        content.setBlockedBy(blockedBy);
        content.setBlockedAt(java.time.LocalDateTime.now());
        save(content);
        
        return true;
    }

    /**
     * 取消屏蔽内容
     * @param contentId 内容ID
     * @return 是否成功
     */
    @Transactional
    public boolean unblockContent(Long contentId) {
        Content content = getContentById(contentId);
        if (content == null) {
            return false;
        }
        
        content.setBlocked(false);
        content.setBlockedBy(null);
        content.setBlockedAt(null);
        save(content);
        
        return true;
    }

    // 获取管理员和版主创建的所有内容
    @Transactional(readOnly = true)
    public List<Content> findAllAdminModeratorContents() {
        return contentRepository.findAll().stream()
                .filter(content -> content.getUser() != null &&
                        ("ADMIN".equals(content.getUser().getRole()) ||
                         "MODERATOR".equals(content.getUser().getRole())))
                .collect(java.util.stream.Collectors.toList());
    }

    // 获取用户自己的内容（分页）
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Content> findUserOwnContents(Long userId, org.springframework.data.domain.Pageable pageable) {
        return contentRepository.findByUserId(userId, pageable);
    }

    // 获取用户自己的所有内容
    @Transactional(readOnly = true)
    public List<Content> findUserOwnContents(Long userId) {
        return contentRepository.findByUserId(userId);
    }

    /**
     * 根据内容列表过滤被屏蔽用户的内容
     * @param contents 内容列表
     * @param currentUser 当前用户
     * @return 过滤后的内容列表
     */
    @Transactional(readOnly = true)
    public List<Content> filterContentsByBlockedUsers(List<Content> contents, User currentUser) {
        if (contents == null || contents.isEmpty() || currentUser == null) {
            return contents;
        }

        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        if (isAdmin) {
            // 管理员可以看到所有内容
            return contents;
        }

        List<Content> filteredContents = new ArrayList<>();
        for (Content content : contents) {
            // 如果内容有创建者且当前用户屏蔽了该创建者，则跳过该内容
            if (content.getUser() != null) {
                boolean isBlocked = userBlockService.isBlocked(currentUser.getId(), content.getUser().getId());
                if (isBlocked) {
                    logger.debug("Content {} is from blocked user, skipping", content.getId());
                    continue; // 跳过被屏蔽用户的内容
                }
            }
            filteredContents.add(content);
        }

        return filteredContents;
    }

    /**
     * 根据隐私设置和屏蔽关系过滤内容
     * @param contents 内容列表
     * @param currentUser 当前用户
     * @return 过滤后的内容列表
     */
    @Transactional(readOnly = true)
    public List<Content> filterContentsByPrivacy(List<Content> contents, User currentUser) {
        if (contents == null || contents.isEmpty()) {
            return new ArrayList<>();
        }

        List<Content> filteredContents = new ArrayList<>();
        boolean isAdmin = currentUser != null && "ADMIN".equals(currentUser.getRole());
        
        int totalCount = contents.size();
        int nullOrNoUserCount = 0;
        int userBlockedCount = 0;
        int blockedContentCount = 0;
        int moderatorBlockedCount = 0;
        int adminHiddenCount = 0;
        int privateContentCount = 0;
        int visibleCount = 0;

        for (Content content : contents) {
            if (content == null || content.getUser() == null) {
                nullOrNoUserCount++;
                continue;
            }

            boolean canView = false;

            // 首先检查普通用户屏蔽关系：如果当前用户屏蔽了内容作者，则跳过该内容
            if (currentUser != null && !isAdmin) {
                boolean isBlocked = userBlockService.isBlocked(currentUser.getId(), content.getUser().getId());
                if (isBlocked) {
                    userBlockedCount++;
                    continue; // 跳过被屏蔽用户的内容
                }
            }

            // 检查内容是否被屏蔽
            if (content.isBlocked()) {
                blockedContentCount++;
                // 被屏蔽的内容，只有内容作者本人和管理员可见
                if (currentUser != null &&
                    (content.getUser().getId().equals(currentUser.getId()) || isAdmin)) {
                    canView = true;
                }
                if (canView) {
                    filteredContents.add(content);
                    visibleCount++;
                }
                continue; // 跳过后续检查
            }

            // 检查版主屏蔽关系：如果内容作者在相关流派中被版主屏蔽
            if (currentUser != null && !isAdmin) {
                // 获取内容所属的流派ID
                Long schoolId = content.getSchool() != null ? content.getSchool().getId() : null;
                if (schoolId != null) {
                    // 检查用户是否在该流派及其子流派中被版主屏蔽
                    boolean isModeratorBlocked = moderatorBlockService.isUserBlockedInSchoolAndSubSchools(content.getUser().getId(), schoolId);
                    if (isModeratorBlocked) {
                        moderatorBlockedCount++;
                        // 被版主屏蔽的用户内容，只有内容作者本人和管理员可见
                        if (content.getUser().getId().equals(currentUser.getId()) || isAdmin) {
                            canView = true;
                        }
                        if (canView) {
                            filteredContents.add(content);
                            visibleCount++;
                        }
                        continue; // 跳过后续检查
                    }
                }
            }


            // 检查管理员设置的状态字段
            if (content.getStatus() == 1) {
                adminHiddenCount++;
                // 状态为1（管理员设置隐藏），只有管理员和内容作者可见
                if (currentUser != null &&
                    (content.getUser().getId().equals(currentUser.getId()) || isAdmin)) {
                    canView = true;
                }
            } else {
                // 状态为0（正常），继续检查用户隐私设置
                if (content.isPrivate()) {
                    privateContentCount++;
                    // 内容被设置为私密，只有内容作者和管理员可见
                    if (currentUser != null &&
                        (content.getUser().getId().equals(currentUser.getId()) || isAdmin)) {
                        canView = true;
                    }
                } else {
                    // 内容为公开，所有人都可以看到（包括未登录用户）
                    canView = true;
                }
            }

            if (canView) {
                filteredContents.add(content);
                visibleCount++;
            }
        }
        
        // 输出详细的过滤统计日志
        logger.info("隐私过滤统计 - 总数: {}, null/无用户: {}, 用户屏蔽: {}, 内容被屏蔽: {}, 版主屏蔽: {}, 管理员隐藏: {}, 私有内容: {}, 最终可见: {}", 
                    totalCount, nullOrNoUserCount, userBlockedCount, blockedContentCount, 
                    moderatorBlockedCount, adminHiddenCount, privateContentCount, visibleCount);

        return filteredContents;
    }

    /**
     * 根据哲学家ID查找内容，支持屏蔽和隐私过滤
     * @param philosopherId 哲学家ID
     * @param currentUser 当前用户
     * @return 过滤后的内容列表
     */
    @Transactional(readOnly = true)
    public List<Content> findByPhilosopherIdWithBlockFilter(Long philosopherId, User currentUser) {
        List<Content> contents = findByPhilosopherId(philosopherId);
        return filterContentsByPrivacy(contents, currentUser);
    }

    /**
     * 根据流派ID查找内容，支持屏蔽和隐私过滤
     * @param schoolId 流派ID
     * @param currentUser 当前用户
     * @return 过滤后的内容列表
     */
    @Transactional(readOnly = true)
    public List<Content> findAllBySchoolIdWithBlockFilter(Long schoolId, User currentUser) {
        // 使用更宽泛的查询，显示该流派的所有内容（包括普通用户编辑的内容）
        List<Content> contents = contentRepository.findBySchoolIdDirect(schoolId);
        return filterContentsByPrivacy(contents, currentUser);
    }

    /**
     * 获取所有内容，支持屏蔽和隐私过滤
     * @param currentUser 当前用户
     * @return 过滤后的内容列表
     */
    @Transactional(readOnly = true)
    public List<Content> findAllWithBlockFilter(User currentUser) {
        List<Content> contents = findAll();
        return filterContentsByPrivacy(contents, currentUser);
    }

    /**
     * 获取所有内容并按优先级排序
     * 优先级：管理员 > 版主 > 用户（按点赞数排序）
     * @param currentUser 当前用户，用于隐私过滤
     * @return 按优先级排序的内容列表
     */
    @Transactional(readOnly = true)
    public List<Content> findAllWithPrioritySort(User currentUser) {
        List<Content> allContents = findAll();
        
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

        // 应用隐私过滤
        return filterContentsByPrivacy(result, currentUser);
    }

    /**
     * 获取所有内容并按优先级排序（分页版本）- 用于内容总览页面的无限滚动
     * 优先级：管理员 > 版主 > 用户（按点赞数排序）
     * @param currentUser 当前用户，用于隐私过滤
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 包含内容列表和分页信息的Map
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> findAllWithPrioritySortPaged(User currentUser, int page, int size) {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        
        try {
            // 创建分页请求
            org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(page, size);
            
            // 获取分页数据（已按优先级排序）
            org.springframework.data.domain.Page<Content> contentPage = 
                contentRepository.findAllWithPriorityPaged(pageable);
            
            // 应用隐私过滤
            List<Content> filteredContents = filterContentsByPrivacy(contentPage.getContent(), currentUser);
            
            result.put("contents", filteredContents);
            result.put("hasMore", contentPage.hasNext());
            result.put("totalElements", contentPage.getTotalElements());
            result.put("totalPages", contentPage.getTotalPages());
            result.put("currentPage", page);
            
            return result;
        } catch (Exception e) {
            logger.error("Error getting contents with priority (paged): " + e.getMessage(), e);
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
     * 对内容列表进行优先级排序
     * 优先级：管理员 > 版主 > 用户（按点赞数排序）
     * @param contents 要排序的内容列表
     * @return 排序后的内容列表
     */
    public List<Content> sortContentsByPriority(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return contents;
        }

        // 分离管理员/版主内容和用户内容
        List<Content> adminModeratorContents = new ArrayList<>();
        List<Content> userContents = new ArrayList<>();

        for (Content content : contents) {
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
    }

    /**
     * 锁定内容 - 只有管理员和版主可以锁定自己编辑的内容
     * @param contentId 内容ID
     * @param user 执行锁定的用户
     * @return 是否锁定成功
     */
    @Transactional
    public boolean lockContent(Long contentId, User user) {
        Content content = getContentById(contentId);
        if (content == null) {
            return false;
        }

        // 检查用户是否有权限锁定内容
        if (!canUserLockContent(user, content)) {
            return false;
        }

        // 设置锁定信息
        content.setLockedByUser(user);
        content.setLockedAt(java.time.LocalDateTime.now());
        contentRepository.save(content);
        return true;
    }

    /**
     * 解锁内容 - 只有锁定内容的用户可以解锁
     * @param contentId 内容ID
     * @param user 执行解锁的用户
     * @return 是否解锁成功
     */
    @Transactional
    public boolean unlockContent(Long contentId, User user) {
        Content content = getContentById(contentId);
        if (content == null) {
            return false;
        }

        // 检查用户是否有权限解锁内容
        if (!canUserUnlockContent(user, content)) {
            return false;
        }

        // 清除锁定信息
        content.setLockedByUser(null);
        content.setLockedAt(null);
        contentRepository.save(content);
        return true;
    }

    /**
     * 检查用户是否可以编辑内容（包括锁定检查）
     * @param user 用户
     * @param content 内容
     * @return 是否可以编辑
     */
    @Transactional(readOnly = true)
    public boolean canUserEditContent(User user, Content content) {
        if (user == null || content == null) {
            return false;
        }

        // 管理员可以编辑所有内容
        if ("ADMIN".equals(user.getRole())) {
            return true;
        }

        // 如果内容被锁定
        if (content.isLocked()) {
            // 检查是否是锁定内容的用户
            if (content.getLockedByUser() != null &&
                content.getLockedByUser().getId().equals(user.getId())) {
                return true;
            }
            
            // 如果锁定内容的用户账号已被注销（enabled=false），则管理员和版主可以编辑
            if (content.getLockedByUser() != null && !content.getLockedByUser().isEnabled()) {
                return "ADMIN".equals(user.getRole()) || "MODERATOR".equals(user.getRole());
            }
            
            return false;
        }

        // 版主可以编辑自己负责流派下的未锁定内容
        if ("MODERATOR".equals(user.getRole())) {
            if (user.getAssignedSchoolId() == null) {
                return false;
            }
            
            // 检查内容是否属于版主负责的流派
            boolean hasAccess = false;
            if (content.getSchool() != null) {
                hasAccess = schoolService.canModeratorManageSchool(user.getAssignedSchoolId(), content.getSchool().getId());
            } else if (content.getPhilosopher() != null) {
                List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(user.getAssignedSchoolId());
                for (School school : content.getPhilosopher().getSchools()) {
                    if (accessibleSchoolIds.contains(school.getId())) {
                        hasAccess = true;
                        break;
                    }
                }
            }
            return hasAccess;
        }

        // 普通用户只能编辑自己的内容
        return content.getUser() != null &&
               content.getUser().getId().equals(user.getId());
    }

    /**
     * 检查用户是否可以锁定内容
     * @param user 用户
     * @param content 内容
     * @return 是否可以锁定
     */
    @Transactional(readOnly = true)
    public boolean canUserLockContent(User user, Content content) {
        if (user == null || content == null) {
            return false;
        }

        // 只有管理员和版主可以锁定内容
        if (!"ADMIN".equals(user.getRole()) && !"MODERATOR".equals(user.getRole())) {
            return false;
        }

        // 内容不能已经被锁定
        if (content.isLocked()) {
            return false;
        }

        // 用户必须是内容的创建者或者管理员
        if ("ADMIN".equals(user.getRole())) {
            return true; // 管理员可以锁定任何内容
        }

        // 版主只能锁定自己创建的内容
        return content.getUser() != null &&
               content.getUser().getId().equals(user.getId());
    }

    /**
     * 检查用户是否可以解锁内容
     * @param user 用户
     * @param content 内容
     * @return 是否可以解锁
     */
    @Transactional(readOnly = true)
    public boolean canUserUnlockContent(User user, Content content) {
        if (user == null || content == null) {
            return false;
        }

        // 只有管理员和版主可以解锁内容
        if (!"ADMIN".equals(user.getRole()) && !"MODERATOR".equals(user.getRole())) {
            return false;
        }

        // 内容必须已经被锁定
        if (!content.isLocked()) {
            return false;
        }

        // 管理员可以解锁任何内容
        if ("ADMIN".equals(user.getRole())) {
            return true; // 管理员可以解锁任何内容
        }

        // 如果锁定内容的用户账号已被注销（enabled=false），版主可以解锁
        if (content.getLockedByUser() != null && !content.getLockedByUser().isEnabled()) {
            return "MODERATOR".equals(user.getRole());
        }

        // 版主只能解锁自己锁定的内容
        return content.getLockedByUser() != null &&
               content.getLockedByUser().getId().equals(user.getId());
    }

    /**
     * 批量设置用户所有内容的隐私状态
     * @param userId 用户ID
     * @param isPrivate 是否私密
     * @param currentUser 当前用户（用于权限验证）
     * @return 更新的内容数量
     */
    @Transactional
    public int setAllContentsPrivacyForUser(Long userId, boolean isPrivate, User currentUser) {
        // 权限验证：只有用户本人和管理员可以批量设置隐私状态
        boolean isAdmin = "ADMIN".equals(currentUser.getRole());
        boolean isOwner = currentUser.getId().equals(userId);

        if (!isAdmin && !isOwner) {
            throw new SecurityException("You don't have permission to modify this user's content privacy settings");
        }

        // 获取用户的所有内容
        List<Content> userContents = contentRepository.findByUserId(userId);

        int updatedCount = 0;
        for (Content content : userContents) {
            // 只更新未删除的内容（通过检查content不为null）
            if (content != null && content.getContent() != null) {
                content.setPrivate(isPrivate);
                content.setPrivacySetBy(currentUser);
                content.setPrivacySetAt(java.time.LocalDateTime.now());
                contentRepository.save(content);
                updatedCount++;
            }
        }

        return updatedCount;
    }
    
    /**
     * 获取随机内容（包括所有用户创建的内容）
     * @param count 要获取的内容数量
     * @param currentUser 当前用户，用于隐私过滤
     * @return 随机内容列表
     */
    @Transactional(readOnly = true)
    public List<Content> getRandomContents(int count, User currentUser) {
        try {
            List<Content> allContents = contentRepository.findAllContentsForQuotes();
            
            // 应用隐私过滤
            allContents = filterContentsByPrivacy(allContents, currentUser);
            
            // 随机打乱
            java.util.Collections.shuffle(allContents);
            
            // 限制返回数量
            if (allContents.size() > count) {
                return allContents.subList(0, count);
            }
            return allContents;
        } catch (Exception e) {
            logger.error("获取随机内容失败: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * 获取随机内容（排除指定ID）
     * @param count 要获取的内容数量
     * @param excludeIds 要排除的内容ID列表
     * @param currentUser 当前用户，用于隐私过滤
     * @return 随机内容列表
     */
    @Transactional(readOnly = true)
    public List<Content> getRandomContentsExcluding(int count, List<Long> excludeIds, User currentUser) {
        try {
            List<Content> allContents = contentRepository.findAllContentsForQuotes();
            
            // 应用隐私过滤
            allContents = filterContentsByPrivacy(allContents, currentUser);
            
            // 过滤掉排除的ID
            if (excludeIds != null && !excludeIds.isEmpty()) {
                allContents = allContents.stream()
                    .filter(c -> !excludeIds.contains(c.getId()))
                    .collect(java.util.stream.Collectors.toList());
            }
            
            // 随机打乱
            java.util.Collections.shuffle(allContents);
            
            // 限制返回数量
            if (allContents.size() > count) {
                return allContents.subList(0, count);
            }
            return allContents;
        } catch (Exception e) {
            logger.error("获取随机内容失败: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}