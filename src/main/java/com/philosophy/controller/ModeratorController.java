package com.philosophy.controller;

import com.philosophy.model.Content;
import com.philosophy.model.Philosopher;
import com.philosophy.model.School;
import com.philosophy.model.SchoolTranslation;
import com.philosophy.model.User;
import com.philosophy.model.Comment;
import com.philosophy.service.ContentService;
import com.philosophy.service.PhilosopherService;
import com.philosophy.service.SchoolService;
import com.philosophy.service.TranslationService;
import com.philosophy.service.UserService;
import com.philosophy.service.CommentService;
import com.philosophy.repository.SchoolTranslationRepository;
import com.philosophy.util.UserInfoCollector;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/moderator")
public class ModeratorController {

    private final PhilosopherService philosopherService;
    private final SchoolService schoolService;
    private final ContentService contentService;
    private final UserService userService;
    private final TranslationService translationService;
    private final SchoolTranslationRepository schoolTranslationRepository;
    private final UserInfoCollector userInfoCollector;
    private final CommentService commentService;

    public ModeratorController(PhilosopherService philosopherService, SchoolService schoolService,
                              ContentService contentService, UserService userService,
                              TranslationService translationService, SchoolTranslationRepository schoolTranslationRepository,
                              UserInfoCollector userInfoCollector, CommentService commentService) {
        this.philosopherService = philosopherService;
        this.schoolService = schoolService;
        this.contentService = contentService;
        this.userService = userService;
        this.translationService = translationService;
        this.schoolTranslationRepository = schoolTranslationRepository;
        this.userInfoCollector = userInfoCollector;
        this.commentService = commentService;
    }

    // 获取当前登录的版主用户
    private User getCurrentModerator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.getCurrentUser(authentication);
        if (user != null && "MODERATOR".equals(user.getRole())) {
            return user;
        }
        return null;
    }

    // 检查版主是否有权限访问该流派
    private boolean hasAccessToSchool(User moderator, Long schoolId) {
        if (moderator == null || moderator.getAssignedSchoolId() == null || schoolId == null) {
            return false;
        }
        // 获取版主负责的流派及其所有子流派的ID
        List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
        return accessibleSchoolIds.contains(schoolId);
    }

    @GetMapping
    public String moderatorDashboard(Model model, HttpServletRequest request) {
        User moderator = getCurrentModerator();
        if (moderator == null) {
            return "redirect:/login";
        }

        // 检查版主是否已被分配流派
        if (moderator.getAssignedSchoolId() == null) {
            String language = getLanguage(request);
            model.addAttribute("errorMessage", "en".equals(language) ?
                "You have not been assigned a school yet. Please contact the administrator to assign you a school." :
                "您尚未被分配负责的流派，请联系管理员为您分配流派。");
            return "moderator/dashboard";
        }

        // 获取版主负责的流派及其子流派
        List<Long> schoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());

        // 统计版主负责的流派相关数据
        model.addAttribute("philosophersCount", philosopherService.countPhilosophersBySchoolIds(schoolIds));
        model.addAttribute("schoolsCount", schoolIds.size());
        model.addAttribute("contentsCount", contentService.countContentsBySchoolIds(schoolIds));
        return "moderator/dashboard";
    }

    // 流派管理 - 只显示版主负责的流派及其子流派
    @GetMapping("/schools")
    public String listSchools(Model model, HttpServletRequest request) {
        User moderator = getCurrentModerator();
        if (moderator == null) {
            return "redirect:/login";
        }

        // 检查版主是否已被分配流派
        if (moderator.getAssignedSchoolId() == null) {
            String language = getLanguage(request);
            model.addAttribute("errorMessage", "en".equals(language) ?
                "You have not been assigned a school yet. Please contact the administrator to assign you a school." :
                "您尚未被分配负责的流派，请联系管理员为您分配流派。");
            return "moderator/schools/list";
        }

        // 获取版主负责的流派及其子流派，但过滤掉管理员创建的流派
        School assignedSchool = schoolService.getSchoolById(moderator.getAssignedSchoolId());
        List<School> schools = new ArrayList<>();
        if (assignedSchool != null) {
            schools.add(assignedSchool);
            // 递归获取所有子流派
            addChildSchools(schools, assignedSchool);
        }

        // 过滤掉管理员创建的流派
        schools = schools.stream()
                .filter(school -> school.getUser() == null ||
                                 !"ADMIN".equals(school.getUser().getRole()))
                .collect(java.util.stream.Collectors.toList());
        model.addAttribute("schools", schools);
        return "moderator/schools/list";
    }

    private void addChildSchools(List<School> schools, School parent) {
        List<School> children = parent.getChildren();
        if (children != null && !children.isEmpty()) {
            schools.addAll(children);
            for (School child : children) {
                addChildSchools(schools, child);
            }
        }
    }

    // 哲学家管理 - 只显示版主负责流派下的哲学家
    @GetMapping("/philosophers")
    public String listPhilosophers(Model model) {
        User moderator = getCurrentModerator();
        if (moderator == null) {
            return "redirect:/login";
        }

        // 检查版主是否已被分配流派
        if (moderator.getAssignedSchoolId() == null) {
            model.addAttribute("errorMessage", "您尚未被分配负责的流派，请联系管理员为您分配流派。");
            model.addAttribute("philosophers", new ArrayList<>());
            return "moderator/philosophers/list";
        }

        // 获取版主负责的流派及其子流派的哲学家，但过滤掉管理员创建的哲学家
        List<Long> schoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
        List<Philosopher> philosophers = philosopherService.getPhilosophersBySchoolIds(schoolIds);

        // 过滤掉管理员创建的哲学家
        philosophers = philosophers.stream()
                .filter(philosopher -> philosopher.getUser() == null ||
                                     !"ADMIN".equals(philosopher.getUser().getRole()))
                .collect(java.util.stream.Collectors.toList());

        model.addAttribute("philosophers", philosophers);
        return "moderator/philosophers/list";
    }

    // 内容管理 - 只显示版主负责流派下管理员和版主创建的内容
    @GetMapping("/contents")
    public String listContents(Model model) {
        User moderator = getCurrentModerator();
        if (moderator == null) {
            return "redirect:/login";
        }

        // 检查版主是否已被分配流派
        if (moderator.getAssignedSchoolId() == null) {
            model.addAttribute("errorMessage", "您尚未被分配负责的流派，请联系管理员为您分配流派。");
            model.addAttribute("contents", new ArrayList<>());
            return "moderator/contents/list";
        }

        // 获取版主负责的流派及其子流派的内容，只显示管理员和版主创建的内容
        List<Long> schoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
        List<Content> contents = contentService.findBySchoolIdsAdminModeratorOnly(schoolIds);

        model.addAttribute("contents", contents);
        return "moderator/contents/list";
    }

    // 创建流派 - 只能在自己负责的流派下创建子流派
    @GetMapping("/schools/create")
    public String showCreateSchoolForm(Model model) {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        model.addAttribute("school", new School());
        // 只能选择自己负责的流派及其子流派作为父流派
        List<School> parentSchools = schoolService.getModeratorManageableSchools(moderator.getAssignedSchoolId());
        model.addAttribute("parentSchools", parentSchools);
        return "moderator/schools/form";
    }

    // 编辑流派 - 只能编辑自己负责的流派及其子流派
    @GetMapping("/schools/edit/{id}")
    public String showEditSchoolForm(@PathVariable Long id, Model model) {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        School school = schoolService.getSchoolById(id);
        if (school == null || !schoolService.canModeratorManageSchool(moderator.getAssignedSchoolId(), id)) {
            return "redirect:/error?message=Access denied";
        }

        model.addAttribute("school", school);

        // 获取英文翻译
        Optional<SchoolTranslation> translation = schoolTranslationRepository
            .findBySchoolIdAndLanguageCode(id, "en");
        if (translation.isPresent()) {
            model.addAttribute("nameEn", translation.get().getNameEn());
            model.addAttribute("descriptionEn", translation.get().getDescriptionEn());
        }

        // 只能选择自己负责的流派及其子流派作为父流派
        List<School> parentSchools = schoolService.getModeratorManageableSchools(moderator.getAssignedSchoolId());
        // 移除自己及自己的子流派作为父流派选项，防止循环引用
        parentSchools.removeIf(s -> s.getId().equals(id) || isDescendant(s, id));
        model.addAttribute("parentSchools", parentSchools);
        return "moderator/schools/form";
    }

    private boolean isDescendant(School school, Long ancestorId) {
        if (school.getParent() == null) {
            return false;
        }
        if (school.getParent().getId().equals(ancestorId)) {
            return true;
        }
        return isDescendant(school.getParent(), ancestorId);
    }

    @PostMapping("/schools/save")
    public String saveSchool(@ModelAttribute School school,
                           @RequestParam(required = false) String nameEn,
                           @RequestParam(required = false) String descriptionEn) {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        // 检查是否有权限保存该流派
        if (school.getId() != null && !schoolService.canModeratorManageSchool(moderator.getAssignedSchoolId(), school.getId())) {
            return "redirect:/error?message=Access denied";
        }
        // 检查父流派是否在权限范围内
        if (school.getParent() != null && !schoolService.canModeratorManageSchool(moderator.getAssignedSchoolId(), school.getParent().getId())) {
            return "redirect:/error?message=Access denied for parent school";
        }

        // 设置流派的创建者为当前版主
        school.setUser(moderator);

        schoolService.saveSchool(school);

        // 保存或删除英文翻译
        if (school.getId() != null) {
            String nameEnTrimmed = (nameEn != null) ? nameEn.trim() : "";
            String descriptionEnTrimmed = (descriptionEn != null) ? descriptionEn.trim() : "";
            boolean hasNameEn = !nameEnTrimmed.isEmpty();
            boolean hasDescriptionEn = !descriptionEnTrimmed.isEmpty();
            if (hasNameEn || hasDescriptionEn) {
                translationService.saveSchoolTranslation(
                    school.getId(),
                    "en",
                    hasNameEn ? nameEnTrimmed : null,
                    hasDescriptionEn ? descriptionEnTrimmed : null
                );
            } else {
                // 两个字段都为空时，删除已有的英文翻译
                translationService.deleteSchoolTranslation(school.getId(), "en");
            }
        }

        return "redirect:/moderator/schools";
    }

    @PostMapping("/schools/delete/{id}")
    public String deleteSchool(@PathVariable Long id) {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        if (!schoolService.canModeratorManageSchool(moderator.getAssignedSchoolId(), id)) {
            return "redirect:/error?message=Access denied";
        }

        schoolService.deleteSchool(id);
        return "redirect:/moderator/schools";
    }

    // 创建哲学家 - 自动关联到版主负责的流派
    @GetMapping("/philosophers/create")
    public String showCreatePhilosopherForm(Model model) {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        model.addAttribute("philosopher", new Philosopher());
        // 初始化英文翻译字段
        model.addAttribute("nameEn", "");
        model.addAttribute("biographyEn", "");
        return "moderator/philosophers/form";
    }

    // 编辑哲学家 - 只能编辑自己负责流派下的哲学家
    @GetMapping("/philosophers/edit/{id}")
    public String showEditPhilosopherForm(@PathVariable Long id, Model model) {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        Philosopher philosopher = philosopherService.getPhilosopherById(id);
        if (philosopher == null) {
            return "redirect:/error?message=Philosopher not found";
        }

        // 检查该哲学家是否属于版主负责的流派
        boolean hasAccess = false;
        List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
        for (School school : philosopher.getSchools()) {
            if (accessibleSchoolIds.contains(school.getId())) {
                hasAccess = true;
                break;
            }
        }
        if (!hasAccess) {
            return "redirect:/error?message=Access denied";
        }

        model.addAttribute("philosopher", philosopher);
        // 加载英文翻译数据（仅当与中文不同才显示）
        String nameEn = translationService.getPhilosopherDisplayName(philosopher, "en");
        String biographyEn = translationService.getPhilosopherDisplayBiography(philosopher, "en");
        model.addAttribute("nameEn", nameEn != null && !nameEn.equals(philosopher.getName()) ? nameEn : "");
        model.addAttribute("biographyEn", biographyEn != null && !biographyEn.equals(philosopher.getBio()) ? biographyEn : "");
        return "moderator/philosophers/form";
    }

    @PostMapping("/philosophers/save")
    public String savePhilosopher(@ModelAttribute Philosopher philosopher,
                                 @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
                                 @RequestParam(value = "nameEn", required = false) String nameEn,
                                 @RequestParam(value = "biographyEn", required = false) String biographyEn) throws IOException {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        // 检查是否有权限保存该哲学家
        if (philosopher.getId() != null) {
            Philosopher existingPhilosopher = philosopherService.getPhilosopherById(philosopher.getId());
            if (existingPhilosopher != null) {
                boolean hasAccess = false;
                List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
                for (School school : existingPhilosopher.getSchools()) {
                    if (accessibleSchoolIds.contains(school.getId())) {
                        hasAccess = true;
                        break;
                    }
                }
                if (!hasAccess) {
                    return "redirect:/error?message=Access denied";
                }
            }
        }

        // 如果是编辑现有哲学家，保留原有的流派关联
        if (philosopher.getId() != null) {
            Philosopher existingPhilosopher = philosopherService.getPhilosopherById(philosopher.getId());
            if (existingPhilosopher != null) {
                // 保留原有的流派关联
                philosopher.setSchools(existingPhilosopher.getSchools());
            }
        } else {
            // 新哲学家默认关联到版主负责的流派
            School assignedSchool = schoolService.getSchoolById(moderator.getAssignedSchoolId());
            if (assignedSchool != null) {
                List<School> schools = new ArrayList<>();
                schools.add(assignedSchool);
                philosopher.setSchools(schools);
            }
        }

        // 设置哲学家的创建者为当前版主
        philosopher.setUser(moderator);

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
            savedPhilosopher = philosopherService.savePhilosopherWithImage(philosopher, imageFile);
        } else {
            // 如果没有上传新图片，保留原有的 imageUrl
            if (philosopher.getId() != null) {
                Philosopher existingPhilosopher = philosopherService.getPhilosopherById(philosopher.getId());
                if (existingPhilosopher != null && existingPhilosopher.getImageUrl() != null) {
                    // 设置原有的 imageUrl，确保不会被清除
                    philosopher.setImageUrl(existingPhilosopher.getImageUrl());
                }
            }
            savedPhilosopher = philosopherService.savePhilosopher(philosopher);
        }

        // 保存或删除英文翻译
        if (savedPhilosopher != null && savedPhilosopher.getId() != null) {
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

        return "redirect:/moderator/philosophers";
    }

    @PostMapping("/philosophers/delete/{id}")
    public String deletePhilosopher(@PathVariable Long id) {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        Philosopher philosopher = philosopherService.getPhilosopherById(id);
        if (philosopher == null) {
            return "redirect:/error?message=Philosopher not found";
        }

        // 检查该哲学家是否属于版主负责的流派
        boolean hasAccess = false;
        List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
        for (School school : philosopher.getSchools()) {
            if (accessibleSchoolIds.contains(school.getId())) {
                hasAccess = true;
                break;
            }
        }
        if (!hasAccess) {
            return "redirect:/error?message=Access denied";
        }

        philosopherService.deletePhilosopher(id);
        return "redirect:/moderator/philosophers";
    }

    // 创建内容 - 只能关联到自己负责的流派
    @GetMapping("/contents/create")
    public String showCreateContentForm(Model model) {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        model.addAttribute("contentForm", new Content());
        // 只能选择自己负责的流派及其子流派
        List<School> schools = schoolService.getModeratorManageableSchools(moderator.getAssignedSchoolId());
        model.addAttribute("schools", schools);
        // 获取所有哲学家，但只能关联到自己负责流派下的哲学家
        List<Long> schoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
        List<Philosopher> philosophers = philosopherService.getPhilosophersBySchoolIds(schoolIds);
        model.addAttribute("philosophers", philosophers);
        model.addAttribute("currentUserId", moderator.getId());
        model.addAttribute("contentEn", ""); // 初始化英文翻译为空
        return "moderator/contents/form";
    }

    // 编辑内容 - 只能编辑自己负责流派下的内容
    @GetMapping("/contents/edit/{id}")
    public String showEditContentForm(@PathVariable Long id, Model model) {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        Content content = contentService.getContentById(id);
        if (content == null) {
            return "redirect:/error?message=Content not found";
        }

        // 检查该内容是否属于版主负责的流派
        boolean hasAccess = false;
        if (content.getSchool() != null) {
            hasAccess = schoolService.canModeratorManageSchool(moderator.getAssignedSchoolId(), content.getSchool().getId());
        } else if (content.getPhilosopher() != null) {
            List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
            for (School school : content.getPhilosopher().getSchools()) {
                if (accessibleSchoolIds.contains(school.getId())) {
                    hasAccess = true;
                    break;
                }
            }
        }
        if (!hasAccess) {
            return "redirect:/error?message=Access denied";
        }

        // 检查用户是否有权限编辑这个内容（包括锁定检查）
        if (!contentService.canUserEditContent(moderator, content)) {
            String errorMsg = URLEncoder.encode("您没有权限编辑这个内容", StandardCharsets.UTF_8);
            return "redirect:/moderator/contents?error=" + errorMsg;
        }

        model.addAttribute("contentForm", content);
        // 只能选择自己负责的流派及其子流派
        List<School> schools = schoolService.getModeratorManageableSchools(moderator.getAssignedSchoolId());
        model.addAttribute("schools", schools);
        // 获取所有哲学家，但只能关联到自己负责流派下的哲学家
        List<Long> schoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
        List<Philosopher> philosophers = philosopherService.getPhilosophersBySchoolIds(schoolIds);
        model.addAttribute("philosophers", philosophers);
        model.addAttribute("currentUserId", moderator.getId());
        
        // 可选：英文翻译占位（如有）- 保持与AdminController的一致性
        String contentEn = translationService.getContentDisplayText(content, "en");
        model.addAttribute("contentEn", contentEn != null && !contentEn.equals(content.getContent()) ? contentEn : "");
        
        return "moderator/contents/form";
    }

    @PostMapping("/contents/save")
    public String saveContent(@ModelAttribute("contentForm") Content content,
                             @RequestParam(value = "philosopherId", required = false) Long philosopherId,
                             @RequestParam(value = "schoolId", required = false) Long schoolId,
                             @RequestParam(value = "contentEn", required = false) String contentEn,
                             @RequestParam(value = "lockAfterSave", required = false, defaultValue = "false") boolean lockAfterSave) {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        // 编辑时先加载持久化实体以保留version等字段
        Content contentToSave;
        if (content.getId() != null) {
            Content existingContent = contentService.getContentById(content.getId());
            if (existingContent == null) {
                return "redirect:/error?message=Content not found";
            }

            boolean hasAccess = false;
            if (existingContent.getSchool() != null) {
                List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
                hasAccess = accessibleSchoolIds.contains(existingContent.getSchool().getId());
            } else if (existingContent.getPhilosopher() != null) {
                List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
                for (School school : existingContent.getPhilosopher().getSchools()) {
                    if (accessibleSchoolIds.contains(school.getId())) {
                        hasAccess = true;
                        break;
                    }
                }
            }
            if (!hasAccess) {
                return "redirect:/error?message=Access denied";
            }

            // 检查用户是否有权限编辑这个内容（包括锁定检查）
            if (!contentService.canUserEditContent(moderator, existingContent)) {
                String errorMsg = URLEncoder.encode("您没有权限编辑这个内容", StandardCharsets.UTF_8);
                return "redirect:/moderator/contents?error=" + errorMsg;
            }

            // 使用持久化实体，更新需要修改的字段
            contentToSave = existingContent;
            contentToSave.setContent(content.getContent());
        } else {
            // 新建内容直接使用表单对象
            contentToSave = content;
        }

        // 设置关联（基于请求参数）
        if (philosopherId != null) {
            contentToSave.setPhilosopher(philosopherService.getPhilosopherById(philosopherId));
        }
        if (schoolId != null) {
            contentToSave.setSchool(schoolService.getSchoolById(schoolId));
        }

        // 检查关联的流派是否在权限范围内
        if (contentToSave.getSchool() != null && !schoolService.canModeratorManageSchool(moderator.getAssignedSchoolId(), contentToSave.getSchool().getId())) {
            return "redirect:/error?message=Access denied for associated school";
        }

        // 检查关联的哲学家是否在权限范围内
        if (contentToSave.getPhilosopher() != null) {
            boolean hasAccess = false;
            List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
            for (School school : contentToSave.getPhilosopher().getSchools()) {
                if (accessibleSchoolIds.contains(school.getId())) {
                    hasAccess = true;
                    break;
                }
            }
            if (!hasAccess) {
                return "redirect:/error?message=Access denied for associated philosopher";
            }
        }

        Content savedContent = contentService.saveContentWithUser(contentToSave, moderator);

        // 保存英文翻译
        if (savedContent.getId() != null) {
            if (contentEn != null && !contentEn.trim().isEmpty()) {
                translationService.saveContentTranslation(
                    savedContent.getId(),
                    "en",
                    contentEn.trim()
                );
            } else {
                // 如果翻译为空，则删除已有的英文翻译
                translationService.deleteContentTranslation(savedContent.getId(), "en");
            }
        }

        // 保存后锁定（仅当勾选且当前用户为创建者时）
        if (lockAfterSave && savedContent.getId() != null) {
            try {
                contentService.lockContent(savedContent.getId(), moderator);
            } catch (Exception ignore) {
            }
        }

        return "redirect:/moderator/contents";
    }

    @PostMapping("/contents/delete/{id}")
    public String deleteContent(@PathVariable Long id) {
        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        Content content = contentService.getContentById(id);
        if (content == null) {
            return "redirect:/error?message=Content not found";
        }

        // 检查该内容是否属于版主负责的流派
        boolean hasAccess = false;
        if (content.getSchool() != null) {
            hasAccess = schoolService.canModeratorManageSchool(moderator.getAssignedSchoolId(), content.getSchool().getId());
        } else if (content.getPhilosopher() != null) {
            List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
            for (School school : content.getPhilosopher().getSchools()) {
                if (accessibleSchoolIds.contains(school.getId())) {
                    hasAccess = true;
                    break;
                }
            }
        }
        if (!hasAccess) {
            return "redirect:/error?message=Access denied";
        }

        contentService.deleteContent(id);
        return "redirect:/moderator/contents";
    }

    // 内容锁定管理
    @PostMapping("/contents/{id}/lock")
    public String lockContent(@PathVariable Long id, HttpServletRequest request) {
        String language = getLanguage(request);

        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        Content content = contentService.getContentById(id);
        if (content == null) {
            return "redirect:/moderator/contents?error=Content not found";
        }

        // 检查该内容是否属于版主负责的流派
        boolean hasAccess = false;
        if (content.getSchool() != null) {
            hasAccess = schoolService.canModeratorManageSchool(moderator.getAssignedSchoolId(), content.getSchool().getId());
        } else if (content.getPhilosopher() != null) {
            List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
            for (School school : content.getPhilosopher().getSchools()) {
                if (accessibleSchoolIds.contains(school.getId())) {
                    hasAccess = true;
                    break;
                }
            }
        }
        if (!hasAccess) {
            return "redirect:/moderator/contents?error=Access denied";
        }

        boolean success = contentService.lockContent(id, moderator);

        if (!success) {
            String errorMessage = "en".equals(language) ?
                "Failed to lock content. You may not have permission or the content may already be locked." :
                "锁定内容失败。您可能没有权限或内容已经被锁定。";
            return "redirect:/moderator/contents?error=" + java.net.URLEncoder.encode(errorMessage, java.nio.charset.StandardCharsets.UTF_8);
        }

        String successMessage = "en".equals(language) ?
            "Content locked successfully." :
            "内容锁定成功。";
        return "redirect:/moderator/contents?success=" + java.net.URLEncoder.encode(successMessage, java.nio.charset.StandardCharsets.UTF_8);
    }

    @PostMapping("/contents/{id}/unlock")
    public String unlockContent(@PathVariable Long id, HttpServletRequest request) {
        String language = getLanguage(request);

        User moderator = getCurrentModerator();
        if (moderator == null || moderator.getAssignedSchoolId() == null) {
            return "redirect:/error?message=No assigned school";
        }

        Content content = contentService.getContentById(id);
        if (content == null) {
            return "redirect:/moderator/contents?error=Content not found";
        }

        // 检查该内容是否属于版主负责的流派
        boolean hasAccess = false;
        if (content.getSchool() != null) {
            hasAccess = schoolService.canModeratorManageSchool(moderator.getAssignedSchoolId(), content.getSchool().getId());
        } else if (content.getPhilosopher() != null) {
            List<Long> accessibleSchoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
            for (School school : content.getPhilosopher().getSchools()) {
                if (accessibleSchoolIds.contains(school.getId())) {
                    hasAccess = true;
                    break;
                }
            }
        }
        if (!hasAccess) {
            return "redirect:/moderator/contents?error=Access denied";
        }

        boolean success = contentService.unlockContent(id, moderator);

        if (!success) {
            String errorMessage = "en".equals(language) ?
                "Failed to unlock content. You may not have permission or the content may not be locked." :
                "解锁内容失败。您可能没有权限或内容未被锁定。";
            return "redirect:/moderator/contents?error=" + java.net.URLEncoder.encode(errorMessage, java.nio.charset.StandardCharsets.UTF_8);
        }

        String successMessage = "en".equals(language) ?
            "Content unlocked successfully." :
            "内容解锁成功。";
        return "redirect:/moderator/contents?success=" + java.net.URLEncoder.encode(successMessage, java.nio.charset.StandardCharsets.UTF_8);
    }





    // 评论管理 - 显示版主负责流派下的所有评论
    @GetMapping("/comments")
    public String listComments(Model model, HttpServletRequest request) {
        User moderator = getCurrentModerator();
        if (moderator == null) {
            return "redirect:/login";
        }

        // 检查版主是否已被分配流派
        if (moderator.getAssignedSchoolId() == null) {
            String language = getLanguage(request);
            model.addAttribute("errorMessage", "en".equals(language) ?
                "You have not been assigned a school yet. Please contact the administrator to assign you a school." :
                "您尚未被分配负责的流派，请联系管理员为您分配流派。");
            model.addAttribute("comments", java.util.Collections.emptyList());
            model.addAttribute("todayCommentsCount", 0L);
            model.addAttribute("translationService", translationService);
            return "moderator/comments/list";
        }

        // 获取版主负责的流派及其子流派的ID
        List<Long> schoolIds = schoolService.getSchoolIdWithDescendants(moderator.getAssignedSchoolId());
        
        // 获取这些流派下的所有评论（按时间排序，最新的在前）
        List<Comment> comments = commentService.findBySchoolIdsWithPrivacyFilter(schoolIds, moderator);

        // 计算今日评论数量
        long todayCommentsCount = comments.stream()
            .filter(comment -> comment.getCreatedAt() != null && 
                              comment.getCreatedAt().toLocalDate().equals(java.time.LocalDate.now()))
            .count();

        model.addAttribute("comments", comments);
        model.addAttribute("todayCommentsCount", todayCommentsCount);
        model.addAttribute("translationService", translationService);
        return "moderator/comments/list";
    }

    @GetMapping("/exit")
    public String exitModeratorPanel() {
        return "redirect:/";
    }

    private String getLanguage(HttpServletRequest request) {
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        return (language == null) ? "zh" : language;
    }
}
