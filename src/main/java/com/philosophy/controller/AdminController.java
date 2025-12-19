package com.philosophy.controller;

import com.philosophy.model.Content;
import com.philosophy.model.Philosopher;
import com.philosophy.model.School;
import com.philosophy.model.User;
import com.philosophy.service.ContentService;
import com.philosophy.service.PhilosopherService;
import com.philosophy.service.SchoolService;
import com.philosophy.service.UserService;
import com.philosophy.service.TranslationService;
import com.philosophy.service.ModeratorBlockService;
import com.philosophy.util.UserInfoCollector;
import com.philosophy.util.InputValidator;
import com.philosophy.util.DateUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    private final PhilosopherService philosopherService;
    private final SchoolService schoolService;
    private final ContentService contentService;
    private final UserService userService;
    private final UserInfoCollector userInfoCollector;
    private final TranslationService translationService;
    private final ModeratorBlockService moderatorBlockService;
    
    public AdminController(PhilosopherService philosopherService, SchoolService schoolService, ContentService contentService, UserService userService, UserInfoCollector userInfoCollector, TranslationService translationService, ModeratorBlockService moderatorBlockService) {
        this.philosopherService = philosopherService;
        this.schoolService = schoolService;
        this.contentService = contentService;
        this.userService = userService;
        this.userInfoCollector = userInfoCollector;
        this.translationService = translationService;
        this.moderatorBlockService = moderatorBlockService;
    }

    @GetMapping
    public String redirectToDashboard() {
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    public String adminDashboard(Model model, HttpServletRequest request) {
        model.addAttribute("philosophersCount", philosopherService.countPhilosophers());
        model.addAttribute("schoolsCount", schoolService.countSchools());
        model.addAttribute("contentsCount", contentService.countContents());
        model.addAttribute("usersCount", userService.countUsers());
        return "admin/dashboard";
    }

    // 用户管理
    @GetMapping("/users")
    public String listUsers(Model model, HttpServletRequest request) {
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("userInfoCollector", userInfoCollector);
        return "admin/users/list";
    }
    
    @PostMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return "redirect:/admin/users";
    }

    // 添加用户锁定切换功能
    @PostMapping("/users/toggle-lock/{id}")
    public String toggleUserLock(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
        User user = userService.getUserById(id);
        if (user != null) {
            user.setAccountLocked(!user.isAccountLocked());
            if (user.isAccountLocked()) {
                user.setLockTime(LocalDateTime.now());
                user.setLockExpireTime(LocalDateTime.now().plusHours(24)); // 24小时后过期
            } else {
                user.setLockTime(null);
                user.setLockExpireTime(null);
                user.setFailedLoginAttempts(0); // 重置失败次数
            }
            userService.saveUser(user);
        }
        return "redirect:/admin/users/view/" + id;
    }

    // 添加用户解锁功能（立即解锁）
    @PostMapping("/users/unlock/{id}")
    public String unlockUser(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
        User user = userService.getUserById(id);
        if (user != null) {
            user.setAccountLocked(false);
            user.setLockTime(null);
            user.setLockExpireTime(null);
            user.setFailedLoginAttempts(0);
            userService.saveUser(user);
        }
        return "redirect:/admin/users/view/" + id;
    }

    // 哲学家管理
    @GetMapping("/philosophers")
    public String listPhilosophers(Model model, HttpServletRequest request) {
        List<Philosopher> philosophers = philosopherService.getAllPhilosophers();
        model.addAttribute("philosophers", philosophers);

        // 检查是否有哲学家缺少birthYear字段
        boolean hasPhilosophersWithoutBirthYear = philosophers.stream()
                .anyMatch(p -> p.getBirthYear() == null);

        if (hasPhilosophersWithoutBirthYear) {
            String language = getLanguage(request);
            model.addAttribute("missingBirthYearMessage", "en".equals(language) ?
                "Note: Some philosophers are missing birth year information. It is recommended to add birth years for automatic sorting on the frontend." :
                "注意：部分哲学家缺少出生年份信息，建议为其添加出生年份以便在前端自动排序。");
        }

        return "admin/philosophers/list";
    }

    @GetMapping("/philosophers/new")
    public String newPhilosopher(Model model, HttpServletRequest request) {
        model.addAttribute("philosopher", new Philosopher());
        model.addAttribute("birthDeathDateRange", "");
        return "admin/philosophers/form";
    }

    @PostMapping("/philosophers")
    public String savePhilosopher(@ModelAttribute("philosopher") Philosopher philosopher,
                                 @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                 @RequestParam(value = "nameEn", required = false) String nameEn,
                                 @RequestParam(value = "biographyEn", required = false) String biographyEn,
                                 @RequestParam(value = "birthDeathDate", required = false) String birthDeathDate,
                                 @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
                                 org.springframework.security.core.Authentication authentication,
                                 RedirectAttributes redirectAttributes) throws IOException {
        // 输入验证
        String nameError = InputValidator.validateRequired(philosopher.getName(), "哲学家姓名", 100);
        if (nameError != null) {
            redirectAttributes.addFlashAttribute("error", nameError);
            return "redirect:/admin/philosophers/new";
        }
        
        if (philosopher.getBio() != null) {
            String bioError = InputValidator.validateOptional(philosopher.getBio(), "传记", 10000);
            if (bioError != null) {
                redirectAttributes.addFlashAttribute("error", bioError);
                return "redirect:/admin/philosophers/new";
            }
        }
        
        if (nameEn != null) {
            String nameEnError = InputValidator.validateOptional(nameEn, "英文姓名", 100);
            if (nameEnError != null) {
                redirectAttributes.addFlashAttribute("error", nameEnError);
                return "redirect:/admin/philosophers/new";
            }
        }
        
        if (biographyEn != null) {
            String bioEnError = InputValidator.validateOptional(biographyEn, "英文传记", 10000);
            if (bioEnError != null) {
                redirectAttributes.addFlashAttribute("error", bioEnError);
                return "redirect:/admin/philosophers/new";
            }
        }
        
        User currentUser = (User) authentication.getPrincipal();
        
        // 解析出生死亡日期范围，自动生成YYYYMMDD格式的整数用于排序
        // 输入格式支持：
        // 1. "1914.11.18 - 1975.3.4" (完整日期范围，会解析为19141118和19750304)
        // 2. "1914.11.18" (仅出生日期，会解析为19141118)
        // 解析后的YYYYMMDD格式数字存储在数据库birth_year和death_year字段中，用于从小到大排序
        if (birthDeathDate != null && !birthDeathDate.trim().isEmpty()) {
            Integer birthDateInt = DateUtils.parseBirthDateFromRange(birthDeathDate);
            if (birthDateInt != null) {
                philosopher.setBirthYear(birthDateInt);
            }
            // 解析死亡日期
            Integer deathDateInt = DateUtils.parseDeathYearFromRange(birthDeathDate);
            if (deathDateInt != null) {
                philosopher.setDeathYear(deathDateInt);
            }
        }
        
        // 处理文件上传
        Philosopher savedPhilosopher;
        if (imageFile != null && !imageFile.isEmpty()) {
            // 如果上传了新图片，先删除旧图片（如果存在）
            if (philosopher.getId() != null) {
                Philosopher existingPhilosopher = philosopherService.getPhilosopherById(philosopher.getId());
                if (existingPhilosopher != null && existingPhilosopher.getImageUrl() != null) {
                    philosopherService.deleteImageFile(existingPhilosopher.getImageUrl());
                }
            }
            // 上传新图片
            String imageUrl = philosopherService.uploadImage(imageFile);
            philosopher.setImageUrl(imageUrl);
            savedPhilosopher = philosopherService.savePhilosopherForAdmin(philosopher, currentUser);
        } else {
            // 如果没有上传新图片，保留原有的 imageUrl
            if (philosopher.getId() != null) {
                Philosopher existingPhilosopher = philosopherService.getPhilosopherById(philosopher.getId());
                if (existingPhilosopher != null && existingPhilosopher.getImageUrl() != null) {
                    // 设置原有的 imageUrl，确保不会被清除
                    philosopher.setImageUrl(existingPhilosopher.getImageUrl());
                }
            }
            savedPhilosopher = philosopherService.savePhilosopherForAdmin(philosopher, currentUser);
        }

        // 保存或删除英文翻译
        if (savedPhilosopher.getId() != null) {
            String nameEnTrimmed = (nameEn != null) ? nameEn.trim() : "";
            String biographyEnTrimmed = (biographyEn != null) ? biographyEn.trim() : "";
            boolean hasNameEn = !nameEnTrimmed.isEmpty();
            boolean hasBioEn = !biographyEnTrimmed.isEmpty();
            if (hasNameEn || hasBioEn) {
                // 如果 nameEn 为空但 biographyEn 不为空，且翻译不存在，使用中文名称作为默认值
                String finalNameEn;
                if (hasNameEn) {
                    // 如果用户提供了 nameEn，使用它
                    finalNameEn = nameEnTrimmed;
                } else if (hasBioEn) {
                    // 如果用户没有提供 nameEn 但提供了 biographyEn
                    // 检查翻译是否存在
                    boolean translationExists = translationService.existsPhilosopherTranslation(savedPhilosopher.getId(), "en");
                    if (!translationExists) {
                        // 如果翻译不存在，使用哲学家的中文名称作为默认的 nameEn（创建新翻译需要 nameEn）
                        finalNameEn = savedPhilosopher.getName();
                    } else {
                        // 如果翻译已存在，传递 null 以保留原有的 nameEn（更新时不修改 nameEn）
                        finalNameEn = null;
                    }
                } else {
                    // 这种情况不应该发生（hasNameEn || hasBioEn 为 true）
                    finalNameEn = null;
                }
                translationService.savePhilosopherTranslation(
                    savedPhilosopher.getId(),
                    "en",
                    finalNameEn,
                    hasBioEn ? biographyEnTrimmed : null
                );
            } else {
                // 两个字段都为空时，删除已有的英文翻译
                translationService.deletePhilosopherTranslation(savedPhilosopher.getId(), "en");
            }
        }
        
        if (redirectUrl != null && !redirectUrl.isEmpty() && redirectUrl.startsWith("/")) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/admin/philosophers";
    }

    @GetMapping("/philosophers/edit/{id}")
    public String editPhilosopher(@PathVariable Long id, Model model) {
        Philosopher philosopher = philosopherService.getPhilosopherById(id);
        model.addAttribute("philosopher", philosopher);
        
        // 加载英文翻译数据
        String nameEn = translationService.getPhilosopherDisplayName(philosopher, "en");
        String biographyEn = translationService.getPhilosopherDisplayBiography(philosopher, "en");
        
        // 如果翻译存在且与中文不同，则使用翻译，否则为空
        model.addAttribute("nameEn", nameEn != null && !nameEn.equals(philosopher.getName()) ? nameEn : "");
        model.addAttribute("biographyEn", biographyEn != null && !biographyEn.equals(philosopher.getBio()) ? biographyEn : "");
        
        // 将 birthYear 和 deathYear 转换为日期范围字符串
        String birthDeathDateRange = DateUtils.formatBirthYearToDateRange(philosopher.getBirthYear(), philosopher.getDeathYear());
        model.addAttribute("birthDeathDateRange", birthDeathDateRange);
        
        return "admin/philosophers/form";
    }

    @PostMapping("/philosophers/delete/{id}")
    public String deletePhilosopher(@PathVariable Long id) {
        philosopherService.deletePhilosopher(id);
        return "redirect:/admin/philosophers";
    }

    // 流派管理
    @GetMapping("/schools")
    public String listSchools(Model model, HttpServletRequest request) {
        model.addAttribute("schools", schoolService.getAllSchools());
        return "admin/schools/list";
    }

    @GetMapping("/schools/new")
    public String newSchool(Model model, HttpServletRequest request) {
        model.addAttribute("school", new School());
        model.addAttribute("parentSchools", schoolService.getAllSchools());
        return "admin/schools/form";
    }

    @PostMapping("/schools")
    public String saveSchool(@ModelAttribute("school") School school,
                           @RequestParam(value = "nameEn", required = false) String nameEn,
                           @RequestParam(value = "descriptionEn", required = false) String descriptionEn,
                           @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
                           org.springframework.security.core.Authentication authentication,
                           RedirectAttributes redirectAttributes) {
        // 输入验证
        String nameError = InputValidator.validateRequired(school.getName(), "学派名称", 100);
        if (nameError != null) {
            redirectAttributes.addFlashAttribute("error", nameError);
            return "redirect:/admin/schools/new";
        }
        
        if (school.getDescription() != null) {
            String descError = InputValidator.validateOptional(school.getDescription(), "描述", 10000);
            if (descError != null) {
                redirectAttributes.addFlashAttribute("error", descError);
                return "redirect:/admin/schools/new";
            }
        }
        
        if (nameEn != null) {
            String nameEnError = InputValidator.validateOptional(nameEn, "英文名称", 100);
            if (nameEnError != null) {
                redirectAttributes.addFlashAttribute("error", nameEnError);
                return "redirect:/admin/schools/new";
            }
        }
        
        if (descriptionEn != null) {
            String descEnError = InputValidator.validateOptional(descriptionEn, "英文描述", 10000);
            if (descEnError != null) {
                redirectAttributes.addFlashAttribute("error", descEnError);
                return "redirect:/admin/schools/new";
            }
        }
        
        User currentUser = (User) authentication.getPrincipal();
        School savedSchool;
        try {
            savedSchool = schoolService.saveSchoolForAdmin(school, currentUser);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            if (school.getId() != null) {
                return "redirect:/admin/schools/edit/" + school.getId();
            } else {
                return "redirect:/admin/schools/new";
            }
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("error", "学派名称已存在，请使用其他名称");
            if (school.getId() != null) {
                return "redirect:/admin/schools/edit/" + school.getId();
            } else {
                return "redirect:/admin/schools/new";
            }
        }

        // 保存或删除英文翻译
        if (savedSchool.getId() != null) {
            String nameEnTrimmed = (nameEn != null) ? nameEn.trim() : "";
            String descriptionEnTrimmed = (descriptionEn != null) ? descriptionEn.trim() : "";
            boolean hasNameEn = !nameEnTrimmed.isEmpty();
            boolean hasDescriptionEn = !descriptionEnTrimmed.isEmpty();
            if (hasNameEn || hasDescriptionEn) {
                translationService.saveSchoolTranslation(
                    savedSchool.getId(),
                    "en",
                    hasNameEn ? nameEnTrimmed : null,
                    hasDescriptionEn ? descriptionEnTrimmed : null
                );
            } else {
                // 两个字段都为空时，删除已有的英文翻译
                translationService.deleteSchoolTranslation(savedSchool.getId(), "en");
            }
        }
        
        if (redirectUrl != null && !redirectUrl.isEmpty() && redirectUrl.startsWith("/")) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/admin/schools";
    }

    @GetMapping("/schools/edit/{id}")
    public String editSchool(@PathVariable Long id, Model model) {
        School school = schoolService.getSchoolById(id);
        List<School> availableParents = schoolService.getAllSchools().stream()
            .filter(s -> !s.getId().equals(id)) // 排除自己
            .filter(s -> !isDescendantOf(s, id)) // 排除自己的子流派
            .toList();
        model.addAttribute("school", school);
        model.addAttribute("parentSchools", availableParents);
        
        // 加载英文翻译数据
        String nameEn = translationService.getSchoolDisplayName(school, "en");
        String descriptionEn = translationService.getSchoolDisplayDescription(school, "en");
        
        // 如果翻译存在且与中文不同，则使用翻译，否则为空
        model.addAttribute("nameEn", nameEn != null && !nameEn.equals(school.getName()) ? nameEn : "");
        model.addAttribute("descriptionEn", descriptionEn != null && !descriptionEn.equals(school.getDescription()) ? descriptionEn : "");
        
        return "admin/schools/form";
    }

    private boolean isDescendantOf(School potentialChild, Long ancestorId) {
        School current = potentialChild;
        while (current != null && current.getParent() != null) {
            if (current.getParent().getId().equals(ancestorId)) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    @PostMapping("/schools/delete/{id}")
    public String deleteSchool(@PathVariable Long id) {
        schoolService.deleteSchool(id);
        return "redirect:/admin/schools";
    }

    // 内容管理
    @GetMapping("/contents")
    public String listContents(Model model, HttpServletRequest request) {
        // 只显示管理员和版主创建的内容
        model.addAttribute("contents", contentService.findAllAdminModeratorContents());
        return "admin/contents/list";
    }

    @GetMapping("/contents/new")
    public String newContent(Model model) {
        model.addAttribute("contentForm", new Content());
        model.addAttribute("philosophers", philosopherService.getAllPhilosophers());
        model.addAttribute("schools", schoolService.getAllSchools());
        model.addAttribute("contentEn", ""); // 初始化英文翻译为空
        return "admin/contents/form";
    }

    @GetMapping("/contents/edit/{id}")
    public String editContent(@PathVariable Long id,
                              Model model,
                              org.springframework.security.core.Authentication authentication) {
        Content content = contentService.getContentById(id);
        if (content == null) {
            model.addAttribute("errorMessage", "内容不存在");
            return "error";
        }

        model.addAttribute("contentForm", content);
        model.addAttribute("philosophers", philosopherService.getAllPhilosophers());
        model.addAttribute("schools", schoolService.getAllSchools());

        // 加载英文翻译数据
        String contentEn = translationService.getContentDisplayText(content, "en");
        // 如果翻译存在且与中文不同，则使用翻译，否则为空
        model.addAttribute("contentEn", contentEn != null && !contentEn.equals(content.getContent()) ? contentEn : "");

        if (authentication != null && authentication.getPrincipal() instanceof User currentUser) {
            model.addAttribute("currentUserId", currentUser.getId());
        }

        return "admin/contents/form";
    }

    @PostMapping("/contents")
    public String saveContent(@ModelAttribute("contentForm") Content content,
                             @RequestParam(value = "philosopherId", required = false) Long philosopherId,
                             @RequestParam(value = "schoolId", required = false) Long schoolId,
                             @RequestParam(value = "contentEn", required = false) String contentEn,
                             @RequestParam(value = "lockAfterSave", required = false, defaultValue = "false") boolean lockAfterSave,
                             @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
                             Model model,
                             org.springframework.security.core.Authentication authentication) {
        
        try {
            User currentUser = (User) authentication.getPrincipal();

            // 如果content.getId()不为null，需要从数据库加载完整的Content对象以避免version字段问题
            Content contentToSave;
            if (content.getId() != null) {
                Content existingContent = contentService.getContentById(content.getId());
                if (existingContent == null) {
                    model.addAttribute("errorMessage", "内容不存在");
                    model.addAttribute("contentForm", content);
                    model.addAttribute("philosophers", philosopherService.getAllPhilosophers());
                    model.addAttribute("schools", schoolService.getAllSchools());
                    model.addAttribute("contentEn", contentEn != null ? contentEn : "");
                    return "admin/contents/form";
                }
                
                if (!contentService.canUserEditContent(currentUser, existingContent)) {
                    model.addAttribute("errorMessage", "您没有权限编辑这个内容");
                    model.addAttribute("contentForm", content);
                    model.addAttribute("philosophers", philosopherService.getAllPhilosophers());
                    model.addAttribute("schools", schoolService.getAllSchools());
                    model.addAttribute("contentEn", contentEn != null ? contentEn : ""); // 保持英文翻译内容
                    return "admin/contents/form";
                }
                
                // 使用从数据库加载的对象，更新需要修改的字段
                contentToSave = existingContent;
                contentToSave.setContent(content.getContent());
            } else {
                // 新建内容，直接使用表单提交的对象
                contentToSave = content;
            }

            // 设置关联对象
            if (philosopherId != null) {
                contentToSave.setPhilosopher(philosopherService.getPhilosopherById(philosopherId));
            }
            if (schoolId != null) {
                contentToSave.setSchool(schoolService.getSchoolById(schoolId));
            }

            Content savedContent = contentService.saveContentForAdmin(contentToSave, currentUser);

            // 处理英文翻译：如果为空则删除，如果有内容则保存
            if (savedContent.getId() != null) {
                logger.debug("Saving content translation - contentId: {}, contentEn: {}", savedContent.getId(), 
                    contentEn != null ? (contentEn.length() > 50 ? contentEn.substring(0, 50) + "..." : contentEn) : "null");
                
                if (contentEn != null && !contentEn.trim().isEmpty()) {
                    // 保存或更新英文翻译
                    try {
                        translationService.saveContentTranslation(
                            savedContent.getId(), 
                            "en", 
                            contentEn.trim()
                        );
                        logger.info("Successfully saved content translation for contentId: {}", savedContent.getId());
                    } catch (Exception e) {
                        logger.error("Failed to save content translation for contentId: " + savedContent.getId(), e);
                        throw e;
                    }
                } else {
                    // 如果翻译为空，删除已有的英文翻译
                    try {
                        translationService.deleteContentTranslation(savedContent.getId(), "en");
                        logger.debug("Deleted content translation for contentId: {}", savedContent.getId());
                    } catch (Exception e) {
                        logger.warn("Failed to delete content translation for contentId: " + savedContent.getId(), e);
                        // 不抛出异常，因为删除失败不是关键错误
                    }
                }
            }
            // 保存后锁定（仅当勾选且当前用户为创建者时）
            if (lockAfterSave && savedContent.getId() != null) {
                try {
                    contentService.lockContent(savedContent.getId(), currentUser);
                } catch (Exception ignore) {
                }
            }
            
            if (redirectUrl != null && !redirectUrl.isEmpty() && redirectUrl.startsWith("/")) {
                return "redirect:" + redirectUrl;
            }
            return "redirect:/admin/contents";
            
        } catch (Exception e) {
            logger.error("Error saving content", e);
            model.addAttribute("contentForm", content);
            model.addAttribute("philosophers", philosopherService.getAllPhilosophers());
            model.addAttribute("schools", schoolService.getAllSchools());
            model.addAttribute("contentEn", contentEn != null ? contentEn : ""); // 保持英文翻译内容
            model.addAttribute("error", "保存内容失败: " + e.getMessage());
            return "admin/contents/form";
        }
    }

    @PostMapping("/contents/toggle-history-pin/{id}")
    public String toggleContentHistoryPin(@PathVariable Long id) {
        Content content = contentService.getContentById(id);
        if (content != null) {
            content.setHistoryPinned(!content.isHistoryPinned());
            contentService.saveContent(content);
        }
        return "redirect:/admin/contents";
    }

    @PostMapping("/contents/delete/{id}")
    public String deleteContent(@PathVariable Long id, org.springframework.security.core.Authentication authentication) {
        Content content = contentService.getContentById(id);
        if (content == null) {
            return "redirect:/admin/contents?error=" + java.net.URLEncoder.encode("内容不存在", java.nio.charset.StandardCharsets.UTF_8);
        }

        // 管理员可以删除任何内容
        contentService.deleteContent(id);
        
        return "redirect:/admin/contents?success=" + java.net.URLEncoder.encode("内容删除成功", java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 处理内容锁定/解锁的通用逻辑
     */
    private String handleContentLockUnlock(Long id, boolean isLock,
                                           org.springframework.security.core.Authentication authentication,
                                           HttpServletRequest request) {
        String language = getLanguage(request);
        User currentUser = (User) authentication.getPrincipal();
        
        boolean success = isLock 
            ? contentService.lockContent(id, currentUser)
            : contentService.unlockContent(id, currentUser);

        if (!success) {
            String errorMessage = isLock
                ? ("en".equals(language) ? 
                    "Failed to lock content. You may not have permission or the content may already be locked." :
                    "锁定内容失败。您可能没有权限或内容已经被锁定。")
                : ("en".equals(language) ?
                    "Failed to unlock content. You may not have permission or the content may not be locked." :
                    "解锁内容失败。您可能没有权限或内容未被锁定。");
            return "redirect:/admin/contents?error=" + java.net.URLEncoder.encode(errorMessage, java.nio.charset.StandardCharsets.UTF_8);
        }

        String successMessage = isLock
            ? ("en".equals(language) ? "Content locked successfully." : "内容锁定成功。")
            : ("en".equals(language) ? "Content unlocked successfully." : "内容解锁成功。");
        return "redirect:/admin/contents?success=" + java.net.URLEncoder.encode(successMessage, java.nio.charset.StandardCharsets.UTF_8);
    }

    // 内容锁定管理
    @PostMapping("/contents/{id}/lock")
    public String lockContent(@PathVariable Long id,
                             org.springframework.security.core.Authentication authentication,
                             HttpServletRequest request) {
        return handleContentLockUnlock(id, true, authentication, request);
    }

    @PostMapping("/contents/{id}/unlock")
    public String unlockContent(@PathVariable Long id,
                               org.springframework.security.core.Authentication authentication,
                               HttpServletRequest request) {
        return handleContentLockUnlock(id, false, authentication, request);
    }

    // 上传图片
    @PostMapping("/upload")
    public String uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        String imageUrl = philosopherService.uploadImage(file);
        return "redirect:/admin/philosophers?imageUrl=" + imageUrl;
    }

    // 添加新用户表单
    @GetMapping("/users/new")
    public String newUser(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("allSchools", schoolService.getAllSchools());
        return "admin/users/form";
    }

    // 用户编辑表单
    @GetMapping("/users/edit/{id}")
    public String editUser(@PathVariable Long id, Model model) {
        User user = userService.getUserById(id);
        if (user == null) {
            model.addAttribute("errorMessage", "用户不存在");
            return "error";
        }
        model.addAttribute("user", user);
        model.addAttribute("allSchools", schoolService.getAllSchools());
        return "admin/users/form";
    }

    // 保存用户信息（管理员）
    @PostMapping("/users")
    public String saveUser(@ModelAttribute("user") User formUser,
                          @RequestParam(value = "password", required = false) String password,
                          @RequestParam(value = "confirmPassword", required = false) String confirmPassword,
                          @RequestParam(value = "assignedSchoolId", required = false) Long assignedSchoolId,
                          Model model,
                          org.springframework.security.core.Authentication authentication) {

        // 如果是新用户（没有ID）
        if (formUser.getId() == null) {
            // 验证密码
            if (password == null || password.trim().isEmpty()) {
                model.addAttribute("errorMessage", "密码不能为空");
                model.addAttribute("user", formUser);
                return "admin/users/form";
            }
            
            if (password.length() < 6) {
                model.addAttribute("errorMessage", "密码长度不能少于6位");
                model.addAttribute("user", formUser);
                return "admin/users/form";
            }
            
            if (!password.equals(confirmPassword)) {
                model.addAttribute("errorMessage", "两次输入的密码不一致");
                model.addAttribute("user", formUser);
                return "admin/users/form";
            }
            
            // 检查用户名和邮箱是否已存在
            if (userService.existsByUsername(formUser.getUsername())) {
                model.addAttribute("errorMessage", "用户名已存在");
                model.addAttribute("user", formUser);
                return "admin/users/form";
            }
            
            if (userService.existsByEmail(formUser.getEmail())) {
                model.addAttribute("errorMessage", "邮箱已存在");
                model.addAttribute("user", formUser);
                return "admin/users/form";
            }
            
            // 处理版主流派分配
            if ("MODERATOR".equals(formUser.getRole())) {
                formUser.setAssignedSchoolId(assignedSchoolId);
            }

            // 设置密码并保存新用户
            formUser.setPassword(password);
            User savedUser = userService.registerNewUser(formUser);
            return "redirect:/admin/users/view/" + savedUser.getId();
        } else {
            // 更新现有用户
            User existing = userService.getUserById(formUser.getId());
            if (existing == null) {
                model.addAttribute("errorMessage", "用户不存在");
                return "error";
            }

            // 检查用户名和邮箱是否被其他用户使用
            if (!existing.getUsername().equals(formUser.getUsername()) && 
                userService.existsByUsername(formUser.getUsername())) {
                model.addAttribute("errorMessage", "用户名已被其他用户使用");
                model.addAttribute("user", formUser);
                return "admin/users/form";
            }
            
            if (!existing.getEmail().equals(formUser.getEmail()) && 
                userService.existsByEmail(formUser.getEmail())) {
                model.addAttribute("errorMessage", "邮箱已被其他用户使用");
                model.addAttribute("user", formUser);
                return "admin/users/form";
            }

            StringBuilder changes = new StringBuilder();
            if (!existing.getUsername().equals(formUser.getUsername())) {
                changes.append("username: '").append(existing.getUsername()).append("' -> '").append(formUser.getUsername()).append("'; ");
            }
            if (!existing.getEmail().equals(formUser.getEmail())) {
                changes.append("email: '").append(existing.getEmail()).append("' -> '").append(formUser.getEmail()).append("'; ");
            }
            if (existing.isEnabled() != formUser.isEnabled()) {
                changes.append("enabled: ").append(existing.isEnabled()).append(" -> ").append(formUser.isEnabled()).append("; ");
            }
            if (!existing.getRole().equals(formUser.getRole())) {
                changes.append("role: '").append(existing.getRole()).append("' -> '").append(formUser.getRole()).append("'; ");
            }
            if (assignedSchoolId != null && !assignedSchoolId.equals(existing.getAssignedSchoolId())) {
                changes.append("assignedSchoolId: ").append(existing.getAssignedSchoolId()).append(" -> ").append(assignedSchoolId).append("; ");
            } else if (assignedSchoolId == null && existing.getAssignedSchoolId() != null) {
                changes.append("assignedSchoolId: ").append(existing.getAssignedSchoolId()).append(" -> null; ");
            }


            // 更新用户信息
            existing.setUsername(formUser.getUsername());
            existing.setEmail(formUser.getEmail());
            existing.setEnabled(formUser.isEnabled());
            existing.setRole(formUser.getRole());

            // 处理版主流派分配
            if ("MODERATOR".equals(formUser.getRole())) {
                existing.setAssignedSchoolId(assignedSchoolId);
            } else {
                existing.setAssignedSchoolId(null);
            }

            userService.saveUser(existing);

            return "redirect:/admin/users/view/" + existing.getId();
        }
    }

    // 修改用户密码（管理员）
    @PostMapping("/users/{id}/password")
    public String updateUserPassword(@PathVariable Long id,
                                     @RequestParam("newPassword") String newPassword,
                                     @RequestParam("confirmPassword") String confirmPassword,
                                     Model model,
                                     org.springframework.security.core.Authentication authentication) {
        User user = userService.getUserById(id);
        if (user == null) {
            model.addAttribute("errorMessage", "用户不存在");
            return "error";
        }

        if (newPassword == null || newPassword.trim().isEmpty()) {
            model.addAttribute("user", user);
            model.addAttribute("passwordError", "新密码不能为空");
            return "admin/users/form";
        }
        if (newPassword.length() < 6) {
            model.addAttribute("user", user);
            model.addAttribute("passwordError", "新密码长度至少为6位");
            return "admin/users/form";
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("user", user);
            model.addAttribute("passwordError", "两次输入的密码不一致");
            return "admin/users/form";
        }

        userService.updatePassword(id, newPassword);

        return "redirect:/admin/users/view/" + id;
    }

    // 为版主分配流派
    @PostMapping("/users/{userId}/assign-school")
    public String assignSchoolToModerator(@PathVariable Long userId, @RequestParam Long schoolId, org.springframework.security.core.Authentication authentication) {
        User user = userService.getUserById(userId);
        if (user != null && "MODERATOR".equals(user.getRole())) {
            userService.assignSchoolToModerator(userId, schoolId);
        }
        return "redirect:/admin/users/view/" + userId;
    }

    // 移除版主的流派分配
    @PostMapping("/users/{userId}/remove-school")
    public String removeSchoolFromModerator(@PathVariable Long userId, org.springframework.security.core.Authentication authentication) {
        User user = userService.getUserById(userId);
        if (user != null && "MODERATOR".equals(user.getRole())) {
            userService.removeSchoolFromModerator(userId);
        }
        return "redirect:/admin/users/view/" + userId;
    }

    // =============================================
    // 版主屏蔽用户管理
    // =============================================

    /**
     * 版主屏蔽用户在特定流派中
     * @param moderatorId 版主用户ID
     * @param blockedUserId 被屏蔽用户ID
     * @param schoolId 流派ID
     * @param reason 屏蔽原因
     * @return 操作结果
     */
    @PostMapping("/moderator/block-user")
    @ResponseBody
    public String blockUserInSchool(@RequestParam Long moderatorId, 
                                   @RequestParam Long blockedUserId, 
                                   @RequestParam Long schoolId, 
                                   @RequestParam(required = false) String reason) {
        try {
            boolean success = moderatorBlockService.blockUserInSchool(moderatorId, blockedUserId, schoolId, reason);
            if (success) {
                return "success";
            } else {
                return "error: 屏蔽失败，可能已经屏蔽过该用户";
            }
        } catch (SecurityException e) {
            return "error: " + e.getMessage();
        } catch (Exception e) {
            return "error: 系统错误：" + e.getMessage();
        }
    }

    /**
     * 取消版主屏蔽用户在特定流派中
     * @param moderatorId 版主用户ID
     * @param blockedUserId 被屏蔽用户ID
     * @param schoolId 流派ID
     * @return 操作结果
     */
    @PostMapping("/moderator/unblock-user")
    @ResponseBody
    public String unblockUserInSchool(@RequestParam Long moderatorId, 
                                     @RequestParam Long blockedUserId, 
                                     @RequestParam Long schoolId) {
        try {
            boolean success = moderatorBlockService.unblockUserInSchool(moderatorId, blockedUserId, schoolId);
            if (success) {
                return "success";
            } else {
                return "error: 取消屏蔽失败";
            }
        } catch (SecurityException e) {
            return "error: " + e.getMessage();
        } catch (Exception e) {
            return "error: 系统错误：" + e.getMessage();
        }
    }

    /**
     * 获取版主在特定流派中屏蔽的用户列表
     * @param moderatorId 版主用户ID
     * @param schoolId 流派ID
     * @param model 模型
     * @return 屏蔽用户列表页面
     */
    @GetMapping("/moderator/{moderatorId}/school/{schoolId}/blocked-users")
    public String getBlockedUsersInSchool(@PathVariable Long moderatorId, 
                                         @PathVariable Long schoolId, 
                                         Model model, 
                                         HttpServletRequest request) {
        // 获取版主信息
        User moderator = userService.getUserById(moderatorId);
        if (moderator == null || !"MODERATOR".equals(moderator.getRole())) {
            model.addAttribute("errorMessage", "版主不存在");
            return "error";
        }

        // 获取流派信息
        School school = schoolService.getSchoolById(schoolId);
        if (school == null) {
            model.addAttribute("errorMessage", "流派不存在");
            return "error";
        }

        // 获取屏蔽的用户列表
        List<com.philosophy.model.ModeratorBlock> blockedUsers = moderatorBlockService.getBlockedUsersInSchool(moderatorId, schoolId);

        model.addAttribute("moderator", moderator);
        model.addAttribute("school", school);
        model.addAttribute("blockedUsers", blockedUsers);

        return "admin/moderator/blocked-users";
    }

    /**
     * 获取用户被版主屏蔽的详情
     * @param userId 用户ID
     * @param model 模型
     * @return 屏蔽详情页面
     */
    @GetMapping("/users/{userId}/moderator-blocks")
    public String getUserModeratorBlocks(@PathVariable Long userId, Model model, HttpServletRequest request) {
        // 获取用户信息
        User user = userService.getUserById(userId);
        if (user == null) {
            model.addAttribute("errorMessage", "用户不存在");
            return "error";
        }

        // 获取用户被版主屏蔽的关系
        List<com.philosophy.model.ModeratorBlock> blockRelationships = moderatorBlockService.getBlockRelationshipsForUser(userId);

        model.addAttribute("user", user);
        model.addAttribute("blockRelationships", blockRelationships);

        return "admin/users/moderator-blocks";
    }

    // 退出后台管理，返回到前台名句页面
    @GetMapping("/exit")
    public String exitAdmin() {
        return "redirect:/quotes";
    }

    private String getLanguage(HttpServletRequest request) {
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        return (language == null) ? "zh" : language;
    }
}