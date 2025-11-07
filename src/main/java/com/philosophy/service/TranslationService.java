package com.philosophy.service;

import com.philosophy.model.School;
import com.philosophy.model.Content;
import com.philosophy.model.Philosopher;
import com.philosophy.model.SchoolTranslation;
import com.philosophy.model.ContentTranslation;
import com.philosophy.model.PhilosopherTranslation;
import com.philosophy.repository.SchoolTranslationRepository;
import com.philosophy.repository.ContentTranslationRepository;
import com.philosophy.repository.PhilosopherTranslationRepository;
import com.philosophy.repository.ContentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
public class TranslationService {

    private final SchoolTranslationRepository schoolTranslationRepository;
    private final ContentTranslationRepository contentTranslationRepository;
    private final PhilosopherTranslationRepository philosopherTranslationRepository;
    private final ContentRepository contentRepository;

    public TranslationService(SchoolTranslationRepository schoolTranslationRepository, 
                             ContentTranslationRepository contentTranslationRepository,
                             PhilosopherTranslationRepository philosopherTranslationRepository,
                             ContentRepository contentRepository) {
        this.schoolTranslationRepository = schoolTranslationRepository;
        this.contentTranslationRepository = contentTranslationRepository;
        this.philosopherTranslationRepository = philosopherTranslationRepository;
        this.contentRepository = contentRepository;
    }

    // ==================== 流派翻译相关方法 ====================

    /**
     * 获取流派的显示名称（优先英文，没有则显示中文）
     */
    public String getSchoolDisplayName(School school, String languageCode) {
        if (school == null) return "";
        
        if ("en".equals(languageCode)) {
            Optional<SchoolTranslation> translation = schoolTranslationRepository
                .findBySchoolIdAndLanguageCode(school.getId(), languageCode);
            return translation.map(SchoolTranslation::getNameEn).orElse(school.getName());
        }
        return school.getName();
    }

    /**
     * 获取流派的显示描述（优先英文，没有则显示中文）
     */
    public String getSchoolDisplayDescription(School school, String languageCode) {
        if (school == null) return "";
        
        if ("en".equals(languageCode)) {
            Optional<SchoolTranslation> translation = schoolTranslationRepository
                .findBySchoolIdAndLanguageCode(school.getId(), languageCode);
            return translation.map(SchoolTranslation::getDescriptionEn).orElse(school.getDescription());
        }
        return school.getDescription();
    }

    /**
     * 获取多个流派的显示名称映射
     */
    public Map<Long, String> getSchoolDisplayNames(List<School> schools, String languageCode) {
        Map<Long, String> result = new HashMap<>();
        
        if (schools == null || schools.isEmpty()) {
            return result;
        }

        if ("en".equals(languageCode)) {
            List<Long> schoolIds = schools.stream().map(School::getId).collect(Collectors.toList());
            List<SchoolTranslation> translations = schoolTranslationRepository
                .findBySchoolIdInAndLanguageCode(schoolIds, languageCode);
            
            Map<Long, String> translationMap = translations.stream()
                .collect(Collectors.toMap(
                    t -> t.getSchool().getId(),
                    SchoolTranslation::getNameEn
                ));
            
            for (School school : schools) {
                result.put(school.getId(), translationMap.getOrDefault(school.getId(), school.getName()));
            }
        } else {
            for (School school : schools) {
                result.put(school.getId(), school.getName());
            }
        }
        
        return result;
    }

    /**
     * 保存或更新流派翻译
     * @param schoolId 流派ID
     * @param languageCode 语言代码
     * @param nameEn 英文名称（如果为null，更新时保留原有值，新建时抛出异常）
     * @param descriptionEn 英文描述（可以为null）
     */
    @Transactional
    public SchoolTranslation saveSchoolTranslation(Long schoolId, String languageCode, String nameEn, String descriptionEn) {
        if (schoolId == null) {
            throw new IllegalArgumentException("School ID cannot be null");
        }
        if (languageCode == null || languageCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Language code cannot be null or empty");
        }
        
        Optional<SchoolTranslation> existing = schoolTranslationRepository
            .findBySchoolIdAndLanguageCode(schoolId, languageCode);
        
        SchoolTranslation translation;
        if (existing.isPresent()) {
            translation = existing.get();
            // 更新时：只更新非null的字段
            if (nameEn != null) {
                translation.setNameEn(nameEn);
            }
            if (descriptionEn != null) {
                translation.setDescriptionEn(descriptionEn);
            }
        } else {
            // 新建时：nameEn必须不为null
            if (nameEn == null) {
                throw new IllegalArgumentException("NameEn cannot be null when creating a new translation");
            }
            School school = new School();
            school.setId(schoolId);
            translation = new SchoolTranslation(school, languageCode, nameEn, descriptionEn);
        }
        
        return schoolTranslationRepository.save(translation);
    }

    /**
     * 删除流派翻译
     */
    @Transactional
    public void deleteSchoolTranslation(Long schoolId, String languageCode) {
        schoolTranslationRepository.deleteBySchoolIdAndLanguageCode(schoolId, languageCode);
    }

    // ==================== 内容翻译相关方法 ====================

    /**
     * 获取内容的显示文本（优先英文，没有则显示中文）
     */
    public String getContentDisplayText(Content content, String languageCode) {
        if (content == null) return "";
        
        if ("en".equals(languageCode)) {
            Optional<ContentTranslation> translation = contentTranslationRepository
                .findByContentIdAndLanguageCode(content.getId(), languageCode);
            return translation.map(ContentTranslation::getContentEn).orElse(content.getContent());
        }
        return content.getContent();
    }

    /**
     * 获取多个内容的显示文本映射
     */
    public Map<Long, String> getContentDisplayTexts(List<Content> contents, String languageCode) {
        Map<Long, String> result = new HashMap<>();
        
        if (contents == null || contents.isEmpty()) {
            return result;
        }

        if ("en".equals(languageCode)) {
            List<Long> contentIds = contents.stream().map(Content::getId).collect(Collectors.toList());
            List<ContentTranslation> translations = contentTranslationRepository
                .findByContentIdInAndLanguageCode(contentIds, languageCode);
            
            Map<Long, String> translationMap = translations.stream()
                .collect(Collectors.toMap(
                    t -> t.getContent().getId(),
                    ContentTranslation::getContentEn
                ));
            
            for (Content content : contents) {
                result.put(content.getId(), translationMap.getOrDefault(content.getId(), content.getContent()));
            }
        } else {
            for (Content content : contents) {
                result.put(content.getId(), content.getContent());
            }
        }
        
        return result;
    }

    /**
     * 保存或更新内容翻译
     * @param contentId 内容ID
     * @param languageCode 语言代码
     * @param contentEn 英文内容（可以为null）
     */
    @Transactional
    public ContentTranslation saveContentTranslation(Long contentId, String languageCode, String contentEn) {
        if (contentId == null) {
            throw new IllegalArgumentException("Content ID cannot be null");
        }
        if (languageCode == null || languageCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Language code cannot be null or empty");
        }
        
        // 验证Content是否存在
        Content content = contentRepository.findById(contentId)
            .orElseThrow(() -> new IllegalArgumentException("Content with ID " + contentId + " does not exist"));
        
        Optional<ContentTranslation> existing = contentTranslationRepository
            .findByContentIdAndLanguageCode(contentId, languageCode);
        
        ContentTranslation translation;
        if (existing.isPresent()) {
            translation = existing.get();
            translation.setContentEn(contentEn);
        } else {
            translation = new ContentTranslation(content, languageCode, contentEn);
        }
        
        return contentTranslationRepository.save(translation);
    }

    /**
     * 删除内容翻译
     */
    @Transactional
    public void deleteContentTranslation(Long contentId, String languageCode) {
        contentTranslationRepository.deleteByContentIdAndLanguageCode(contentId, languageCode);
    }

    // ==================== 批量操作方法 ====================

    /**
     * 批量获取流派翻译数据
     */
    public List<Object[]> getSchoolsWithTranslation(String languageCode) {
        return schoolTranslationRepository.findSchoolsWithTranslation(languageCode);
    }

    /**
     * 批量获取内容翻译数据
     */
    public List<Object[]> getContentsWithTranslation(String languageCode) {
        return contentTranslationRepository.findContentsWithTranslation(languageCode);
    }

    /**
     * 根据流派ID获取内容翻译数据
     */
    public List<Object[]> getContentsBySchoolIdWithTranslation(Long schoolId, String languageCode) {
        return contentTranslationRepository.findContentsBySchoolIdWithTranslation(schoolId, languageCode);
    }

    /**
     * 根据流派ID列表获取内容翻译数据
     */
    public List<Object[]> getContentsBySchoolIdsWithTranslation(List<Long> schoolIds, String languageCode) {
        return contentTranslationRepository.findContentsBySchoolIdsWithTranslation(schoolIds, languageCode);
    }

    // ==================== 哲学家翻译相关方法 ====================

    /**
     * 获取哲学家的显示名称（优先英文，没有则显示中文）
     */
    public String getPhilosopherDisplayName(Philosopher philosopher, String languageCode) {
        if (philosopher == null) return "";
        
        if ("en".equals(languageCode)) {
            Optional<PhilosopherTranslation> translation = philosopherTranslationRepository
                .findByPhilosopherIdAndLanguageCode(philosopher.getId(), languageCode);
            return translation.map(PhilosopherTranslation::getNameEn).orElse(philosopher.getName());
        }
        return philosopher.getName();
    }

    /**
     * 获取哲学家的显示传记（优先英文，没有则显示中文）
     */
    public String getPhilosopherDisplayBiography(Philosopher philosopher, String languageCode) {
        if (philosopher == null) return "";
        
        if ("en".equals(languageCode)) {
            Optional<PhilosopherTranslation> translation = philosopherTranslationRepository
                .findByPhilosopherIdAndLanguageCode(philosopher.getId(), languageCode);
            return translation.map(PhilosopherTranslation::getBiographyEn).orElse(philosopher.getBio());
        }
        return philosopher.getBio();
    }

    /**
     * 获取多个哲学家的显示名称映射
     */
    public Map<Long, String> getPhilosopherDisplayNames(List<Philosopher> philosophers, String languageCode) {
        Map<Long, String> result = new HashMap<>();
        
        if (philosophers == null || philosophers.isEmpty()) {
            return result;
        }

        if ("en".equals(languageCode)) {
            List<Long> philosopherIds = philosophers.stream().map(Philosopher::getId).collect(Collectors.toList());
            List<PhilosopherTranslation> translations = philosopherTranslationRepository
                .findByPhilosopherIdInAndLanguageCode(philosopherIds, languageCode);
            
            Map<Long, String> translationMap = translations.stream()
                .collect(Collectors.toMap(
                    t -> t.getPhilosopher().getId(),
                    PhilosopherTranslation::getNameEn
                ));
            
            for (Philosopher philosopher : philosophers) {
                result.put(philosopher.getId(), translationMap.getOrDefault(philosopher.getId(), philosopher.getName()));
            }
        } else {
            for (Philosopher philosopher : philosophers) {
                result.put(philosopher.getId(), philosopher.getName());
            }
        }
        
        return result;
    }

    /**
     * 保存或更新哲学家翻译
     * @param philosopherId 哲学家ID
     * @param languageCode 语言代码
     * @param nameEn 英文姓名（如果为null，更新时保留原有值，新建时抛出异常）
     * @param biographyEn 英文简介（可以为null）
     */
    @Transactional
    public PhilosopherTranslation savePhilosopherTranslation(Long philosopherId, String languageCode, String nameEn, String biographyEn) {
        if (philosopherId == null) {
            throw new IllegalArgumentException("Philosopher ID cannot be null");
        }
        if (languageCode == null || languageCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Language code cannot be null or empty");
        }
        
        Optional<PhilosopherTranslation> existing = philosopherTranslationRepository
            .findByPhilosopherIdAndLanguageCode(philosopherId, languageCode);
        
        PhilosopherTranslation translation;
        if (existing.isPresent()) {
            translation = existing.get();
            // 更新时：只更新非null的字段
            if (nameEn != null) {
                translation.setNameEn(nameEn);
            }
            if (biographyEn != null) {
                translation.setBiographyEn(biographyEn);
            }
        } else {
            // 新建时：nameEn必须不为null
            if (nameEn == null) {
                throw new IllegalArgumentException("NameEn cannot be null when creating a new translation");
            }
            Philosopher philosopher = new Philosopher();
            philosopher.setId(philosopherId);
            translation = new PhilosopherTranslation(philosopher, languageCode, nameEn, biographyEn);
        }
        
        return philosopherTranslationRepository.save(translation);
    }

    /**
     * 删除哲学家翻译
     */
    @Transactional
    public void deletePhilosopherTranslation(Long philosopherId, String languageCode) {
        philosopherTranslationRepository.deleteByPhilosopherIdAndLanguageCode(philosopherId, languageCode);
    }

    /**
     * 检查哲学家翻译是否存在
     */
    @Transactional(readOnly = true)
    public boolean existsPhilosopherTranslation(Long philosopherId, String languageCode) {
        return philosopherTranslationRepository.existsByPhilosopherIdAndLanguageCode(philosopherId, languageCode);
    }

    // ==================== 批量操作方法 ====================

    /**
     * 批量获取哲学家翻译数据
     */
    public List<Object[]> getPhilosophersWithTranslation(String languageCode) {
        return philosopherTranslationRepository.findPhilosophersWithTranslation(languageCode);
    }

    // ==================== 静态文本国际化方法 ====================

    /**
     * 获取静态文本的国际化版本
     */
    public String getStaticText(String key, String languageCode) {
        if ("en".equals(languageCode)) {
            return getEnglishText(key);
        }
        return getChineseText(key);
    }

    /**
     * 获取中文文本
     */
    private String getChineseText(String key) {
        switch (key) {
            case "philosophy": return "哲学";
            case "my_learning_notes": return "我的学习笔记";
            case "my_comments": return "我的评论";
            case "total_comments": return "共";
            case "start_commenting": return "去评论区分享您的想法吧！";
            case "switch_language": return "切换语言";
            case "switch_to_chinese": return "切换到中文";
            case "switch_to_english": return "Switch to English";
            case "switched_to_chinese": return "已切换到中文";
            case "switched_to_english": return "Switched to English";
            case "welcome_tour": return "欢迎游览";
            case "philosophers": return "哲学家";
            case "schools": return "流派";
            case "login": return "登录";
            case "logout": return "退出";
            case "admin": return "管理";
            case "comments": return "评论区";
            case "no_comments": return "暂无评论，来发表第一条评论吧！";
            case "write_comment": return "写下你的想法...";
            case "post_comment": return "发表评论";
            case "please_login": return "请先登录后再发表评论";
            case "delete_confirm": return "确定要删除这条评论吗？";
            case "philosophy_schools": return "哲学流派";
            case "please_select_school": return "请从上方选择一个哲学流派查看相关思想内容";
            case "no_content": return "该流派暂无相关思想内容";
            case "unknown_philosopher": return "未知哲学家";
            case "no_philosopher": return "未关联哲学家";
            case "biography": return "生平简介";
            case "main_thoughts": return "主要思想";
            case "no_thoughts": return "暂无相关思想内容";
            case "please_select_philosopher": return "请从左侧选择一位哲学家";
            case "content_loading": return "内容加载中...";
            case "parent_school": return "父流派";
            case "school_name": return "流派名称";
            case "sub_school_name": return "子流派名称";
            case "school_description": return "流派描述...";
            case "school_thought_content": return "流派思想内容...";
            case "philosopher_name": return "哲学家名称";
            case "era": return "时代";
            case "content_text": return "内容正文...";
            case "back_to_top": return "回到顶部";
            case "comments_count": return "条评论";
            case "please_login_to_comment": return "请先登录后再发表评论";
            case "search": return "搜索";
            case "search_results": return "搜索结果";
            case "enter_keywords_to_search": return "输入关键词搜索...";
            case "view_details": return "查看详情";
            case "no_results_found": return "未找到相关结果";
            case "search_tips": return "搜索建议：";
            case "check_spelling": return "检查拼写是否正确";
            case "try_different_keywords": return "尝试使用不同的关键词";
            case "use_general_terms": return "使用更通用的术语";
            case "contents": return "内容";
            case "all_contents": return "全部内容";
            case "all_rights_reserved": return "保留所有权利";
            case "philosophy_website": return "哲学网站";
            case "home": return "首页";
            case "user_profile": return "用户主页";
            case "back_to_profile": return "返回个人主页";
            case "username": return "用户名";
            case "user_email": return "用户邮箱";
            case "likes": return "点赞";
            case "my_likes": return "我的点赞";
            case "liked_users": return "点赞的用户";
            case "no_liked_users": return "您还没有点赞任何用户";
            case "liked_contents": return "点赞的内容";
            case "no_liked_contents": return "您还没有点赞任何内容";
            case "edit": return "编辑";
            case "comment_privacy_settings": return "评论隐私设置";
            case "comment_privacy_description": return "开启后，您的所有评论将仅您可见";
            case "privacy_settings_updated": return "隐私设置已更新";
            case "update_comment_privacy_failed": return "更新评论隐私设置失败，请稍后再试";
            case "deleted": return "已删除";
            case "username_placeholder": return "用户名";
            case "comment_content_placeholder": return "评论内容...";
            case "reply_content_placeholder": return "回复内容...";
            case "view_all": return "查看全部";
            case "users": return "用户";
            case "content_edit_list": return "内容编辑列表";
            case "back": return "返回";
            case "original_content": return "原始内容";
            case "content_label": return "内容：";
            case "philosopher_label": return "哲学家：";
            case "school_label": return "流派：";
            case "creation_time": return "创建时间：";
            case "user_edits": return "用户编辑";
            case "create_new_edit": return "创建新编辑";
            case "no_user_edits": return "暂无用户编辑";
            case "no_edits_yet": return "还没有用户对此内容进行编辑";
            case "create_first_edit": return "创建第一个编辑";
            case "view": return "查看";
            case "editor_label": return "编辑者：";
            case "edit_content_label": return "编辑内容：";
            case "close": return "关闭";
            case "unknown_time": return "未知时间";
            case "edit_title": return "编辑标题";
            case "create_content": return "创建内容";
            case "creating_new_philosophy_content": return "创建新的哲学内容";
            case "back_to_edit_list": return "返回编辑列表";
            case "select_philosopher": return "请选择哲学家";
            case "select_school": return "请选择流派";
            case "title": return "标题";
            case "enter_title": return "请输入标题";
            case "content": return "内容";
            case "enter_content": return "请输入内容";
            case "english_translation_optional": return "英文翻译（可选）";
            case "english_content": return "英文内容";
            case "submit": return "提交";
            case "cancel": return "取消";
            case "save": return "保存";
            case "moderator_backend": return "版主后台";
            case "admin_backend": return "管理员后台";
            case "settings": return "设置";
            case "language_settings": return "语言设置";
            case "chinese": return "中文";
            case "english": return "English";
            case "theme_color": return "主题颜色";
            case "light": return "浅色";
            case "dark": return "深色";
            case "auto": return "自动";
            case "profile_privacy_status": return "主页隐私状态";
            case "privacy_lock_description": return "锁定后，您的所有内容和评论将仅自己可见。";
            case "locked": return "已锁定";
            case "unlocked": return "已解锁";
            case "confirm_lock_profile": return "确定要锁定您的主页吗？所有内容和评论将变为私密。";
            case "confirm_unlock_profile": return "确定要解锁您的主页吗？所有内容和评论将变为公开。";
            case "update_failed": return "更新失败";
            case "theme_switched": return "主题已切换";
            case "my_content_edits": return "我的内容编辑";
            case "manage_your_content_edits": return "管理您的内容编辑";
            case "edit_content": return "编辑内容";
            case "delete_edit": return "删除";
            case "delete_edit_confirm": return "确定要删除这个编辑吗?";
            case "no_content_data": return "暂无内容数据，请添加新的内容编辑。";
            case "id": return "ID";
            case "actions": return "操作";
            case "none": return "无";
            default: return key;
        }
    }

    /**
     * 获取英文文本
     */
    private String getEnglishText(String key) {
        switch (key) {
            case "philosophy": return "Philosophy";
            case "my_learning_notes": return "My Learning Notes";
            case "my_comments": return "My Comments";
            case "total_comments": return "Total";
            case "start_commenting": return "Start commenting and share your thoughts!";
            case "switch_language": return "Switch Language";
            case "switch_to_chinese": return "切换到中文";
            case "switch_to_english": return "Switch to English";
            case "switched_to_chinese": return "已切换到中文";
            case "switched_to_english": return "Switched to English";
            case "welcome_tour": return "Welcome Tour";
            case "philosophers": return "Philosophers";
            case "schools": return "Schools";
            case "login": return "Login";
            case "logout": return "Logout";
            case "admin": return "Admin";
            case "comments": return "Comments";
            case "no_comments": return "No comments yet. Be the first to share your thoughts!";
            case "write_comment": return "Share your thoughts...";
            case "post_comment": return "Post Comment";
            case "please_login": return "Please log in to comment";
            case "delete_confirm": return "Are you sure you want to delete this comment?";
            case "philosophy_schools": return "Philosophy Schools";
            case "please_select_school": return "Please select a philosophy school from above to view related content";
            case "no_content": return "This school has no related content yet";
            case "unknown_philosopher": return "Unknown Philosopher";
            case "no_philosopher": return "No Associated Philosopher";
            case "biography": return "Biography";
            case "main_thoughts": return "Main Thoughts";
            case "no_thoughts": return "No related thoughts available";
            case "please_select_philosopher": return "Please select a philosopher from the left";
            case "content_loading": return "Content loading...";
            case "parent_school": return "Parent School";
            case "school_name": return "School Name";
            case "sub_school_name": return "Sub School Name";
            case "school_description": return "School description...";
            case "school_thought_content": return "School thought content...";
            case "philosopher_name": return "Philosopher Name";
            case "era": return "Era";
            case "content_text": return "Content text...";
            case "back_to_top": return "Back to Top";
            case "comments_count": return " comments";
            case "please_login_to_comment": return "Please log in to comment";
            case "search": return "Search";
            case "search_results": return "Search Results";
            case "enter_keywords_to_search": return "Enter keywords to search...";
            case "view_details": return "View Details";
            case "no_results_found": return "No results found";
            case "search_tips": return "Search tips:";
            case "check_spelling": return "Check spelling";
            case "try_different_keywords": return "Try different keywords";
            case "use_general_terms": return "Use more general terms";
            case "contents": return "Contents";
            case "all_contents": return "All Contents";
            case "all_rights_reserved": return "All rights reserved";
            case "philosophy_website": return "Philosophy Website";
            case "home": return "Home";
            case "user_profile": return "User Profile";
            case "back_to_profile": return "Back to Profile";
            case "username": return "Username";
            case "user_email": return "User Email";
            case "likes": return "Likes";
            case "my_likes": return "My Likes";
            case "liked_users": return "Liked Users";
            case "no_liked_users": return "You haven't liked any users yet";
            case "liked_contents": return "Liked Contents";
            case "no_liked_contents": return "You haven't liked any content yet";
            case "edit": return "Edit";
            case "comment_privacy_settings": return "Comment Privacy Settings";
            case "comment_privacy_description": return "When enabled, all your comments will only be visible to you";
            case "privacy_settings_updated": return "Privacy settings updated";
            case "update_comment_privacy_failed": return "Failed to update comment privacy settings, please try again later";
            case "deleted": return "Deleted";
            case "username_placeholder": return "Username";
            case "comment_content_placeholder": return "Comment content...";
            case "reply_content_placeholder": return "Reply content...";
            case "view_all": return "View All";
            case "users": return "Users";
            case "content_edit_list": return "Content Edit List";
            case "back": return "Back";
            case "original_content": return "Original Content";
            case "content_label": return "Content:";
            case "philosopher_label": return "Philosopher:";
            case "school_label": return "School:";
            case "creation_time": return "Creation Time:";
            case "user_edits": return "User Edits";
            case "create_new_edit": return "Create New Edit";
            case "no_user_edits": return "No user edits";
            case "no_edits_yet": return "No users have edited this content yet";
            case "create_first_edit": return "Create first edit";
            case "view": return "View";
            case "editor_label": return "Editor:";
            case "edit_content_label": return "Edit Content:";
            case "close": return "Close";
            case "unknown_time": return "Unknown time";
            case "edit_title": return "Edit Title";
            case "create_content": return "Create Content";
            case "creating_new_philosophy_content": return "Creating new philosophy content";
            case "back_to_edit_list": return "Back to Edit List";
            case "select_philosopher": return "Please select a philosopher";
            case "select_school": return "Please select a school";
            case "title": return "Title";
            case "enter_title": return "Please enter title";
            case "content": return "Content";
            case "enter_content": return "Please enter content";
            case "english_translation_optional": return "English Translation (Optional)";
            case "english_content": return "English Content";
            case "submit": return "Submit";
            case "cancel": return "Cancel";
            case "save": return "Save";
            case "moderator_backend": return "Moderator Backend";
            case "admin_backend": return "Admin Backend";
            case "settings": return "Settings";
            case "language_settings": return "Language Settings";
            case "chinese": return "Chinese";
            case "english": return "English";
            case "theme_color": return "Theme Color";
            case "light": return "Light";
            case "dark": return "Dark";
            case "auto": return "Auto";
            case "profile_privacy_status": return "Profile Privacy Status";
            case "privacy_lock_description": return "When locked, all your content and comments will only be visible to you.";
            case "locked": return "Locked";
            case "unlocked": return "Unlocked";
            case "confirm_lock_profile": return "Are you sure you want to lock your profile? All content and comments will become private.";
            case "confirm_unlock_profile": return "Are you sure you want to unlock your profile? All content and comments will become public.";
            case "update_failed": return "Update failed";
            case "theme_switched": return "Theme switched";
            case "my_content_edits": return "My Content Edits";
            case "manage_your_content_edits": return "Manage your content edits";
            case "edit_content": return "Edit Content";
            case "delete_edit": return "Delete";
            case "delete_edit_confirm": return "Are you sure you want to delete this edit?";
            case "no_content_data": return "No content data, please add new content edits.";
            case "id": return "ID";
            case "actions": return "Actions";
            case "none": return "None";
            default: return key;
        }
    }
}
