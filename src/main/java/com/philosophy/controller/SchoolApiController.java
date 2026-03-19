package com.philosophy.controller;

import com.philosophy.model.School;
import com.philosophy.service.SchoolService;
import com.philosophy.service.TranslationService;
import com.philosophy.util.PinyinStringComparator;
import com.philosophy.util.LanguageUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class SchoolApiController {

    private final SchoolService schoolService;
    private final TranslationService translationService;
    private final LanguageUtil languageUtil;

    public SchoolApiController(SchoolService schoolService, TranslationService translationService, LanguageUtil languageUtil) {
        this.schoolService = schoolService;
        this.translationService = translationService;
        this.languageUtil = languageUtil;
    }

    @GetMapping("/api/schools/children")
    @Transactional(readOnly = true)
    public ResponseEntity<List<SchoolNodeDTO>> getChildSchools(@RequestParam("parentId") Long parentId,
                                                               HttpServletRequest request) {
        School parent = schoolService.getSchoolById(parentId);
        if (parent == null) {
            return ResponseEntity.ok(new ArrayList<>());
        }

        List<School> children = schoolService.findByParentId(parentId);

        // 使用拼音/忽略大小写排序当前层级的流派
        PinyinStringComparator nameComparator = new PinyinStringComparator();
        children.sort(Comparator.comparing(s -> nameComparator.toComparableKey(s.getName())));

        // 获取当前语言设置（根据IP自动判断默认语言）
        String language = languageUtil.getLanguage(request);

        // 批量判断这些子流派是否还有子流派（避免 N+1）
        List<Long> childIds = children.stream().map(School::getId).toList();
        Set<Long> childHasChildrenSet = schoolService.findParentIdsHavingChildren(childIds);

        List<SchoolNodeDTO> result = new ArrayList<>();
        for (School child : children) {
            boolean hasChildren = childHasChildrenSet.contains(child.getId());
            String displayName = translationService.getSchoolDisplayName(child, language);
            result.add(new SchoolNodeDTO(
                    child.getId(),
                    child.getName(),
                    child.getNameEn(),
                    parentId,
                    displayName,
                    hasChildren
            ));
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 一次性返回所有流派的“名字节点”（不包含描述/内容），供前端构建可展开的树。
     * 前端首屏只渲染顶级节点；点击时再在前端展开子节点（无需再请求 children）。
     */
    @GetMapping("/api/schools/nodes")
    @Transactional(readOnly = true)
    public ResponseEntity<List<SchoolNameNodeDTO>> getAllSchoolNameNodes(HttpServletRequest request) {
        // 获取当前语言设置（根据IP自动判断默认语言）
        String language = languageUtil.getLanguage(request);

        List<School> all = schoolService.findAllSchools();
        if (all == null || all.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        // 计算哪些节点拥有子节点（避免 N+1）
        List<Long> allIds = all.stream().map(School::getId).toList();
        Set<Long> hasChildrenSet = schoolService.findParentIdsHavingChildren(allIds);

        PinyinStringComparator nameComparator = new PinyinStringComparator();

        // 给前端排序用的 key：与后端 children 接口一致，按中文名拼音/忽略大小写
        Map<Long, String> sortKeyById = new HashMap<>();
        for (School s : all) {
            sortKeyById.put(s.getId(), nameComparator.toComparableKey(s.getName()));
        }

        List<SchoolNameNodeDTO> result = new ArrayList<>(all.size());
        for (School s : all) {
            Long parentId = (s.getParent() != null) ? s.getParent().getId() : null;
            String displayName = translationService.getSchoolDisplayName(s, language);
            boolean hasChildren = hasChildrenSet.contains(s.getId());
            result.add(new SchoolNameNodeDTO(
                    s.getId(),
                    parentId,
                    displayName,
                    s.getName(),
                    s.getNameEn(),
                    hasChildren,
                    sortKeyById.get(s.getId())
            ));
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 返回指定流派的祖先路径（从顶级到当前，包含自身）。
     * 用于前端在 /schools/filter/{id} 时只沿路径懒加载展开，避免递归扫描整棵树。
     */
    @GetMapping("/api/schools/ancestry")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Long>> getSchoolAncestry(@RequestParam("id") Long id) {
        School school = schoolService.getSchoolById(id);
        if (school == null) {
            return ResponseEntity.ok(List.of());
        }

        List<Long> path = new ArrayList<>();
        School current = school;
        while (current != null) {
            path.add(0, current.getId());
            current = current.getParent();
        }
        return ResponseEntity.ok(path);
    }

    @GetMapping("/api/schools/detail")
    @Transactional(readOnly = true)
    public ResponseEntity<SchoolDetailDTO> getSchoolDetail(@RequestParam("id") Long id,
                                                           HttpServletRequest request) {
        School school = schoolService.getSchoolById(id);
        if (school == null) {
            return ResponseEntity.ok(new SchoolDetailDTO());
        }

        // 获取当前语言设置（根据IP自动判断默认语言）
        String language = languageUtil.getLanguage(request);

        String displayName = translationService.getSchoolDisplayName(school, language);
        String displayDesc = translationService.getSchoolDisplayDescription(school, language);

        SchoolDetailDTO dto = new SchoolDetailDTO();
        dto.setId(school.getId());
        dto.setName(school.getName());
        dto.setNameEn(school.getNameEn());
        dto.setDisplayName(displayName);
        dto.setDescription(displayDesc);
        dto.setParentId(school.getParent() != null ? school.getParent().getId() : null);
        return ResponseEntity.ok(dto);
    }

    public static class SchoolNodeDTO {
        private Long id;
        private String name;
        private String nameEn;
        private Long parentId;
        private String displayName;
        private boolean hasChildren;

        public SchoolNodeDTO() {}

        public SchoolNodeDTO(Long id, String name, String nameEn, Long parentId, String displayName, boolean hasChildren) {
            this.id = id;
            this.name = name;
            this.nameEn = nameEn;
            this.parentId = parentId;
            this.displayName = displayName;
            this.hasChildren = hasChildren;
        }

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNameEn() {
            return nameEn;
        }

        public void setNameEn(String nameEn) {
            this.nameEn = nameEn;
        }

        public Long getParentId() {
            return parentId;
        }

        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public boolean isHasChildren() {
            return hasChildren;
        }

        public void setHasChildren(boolean hasChildren) {
            this.hasChildren = hasChildren;
        }
    }

    public static class SchoolDetailDTO {
        private Long id;
        private String name;
        private String nameEn;
        private String displayName;
        private String description;
        private Long parentId;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNameEn() {
            return nameEn;
        }

        public void setNameEn(String nameEn) {
            this.nameEn = nameEn;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Long getParentId() {
            return parentId;
        }

        public void setParentId(Long parentId) {
            this.parentId = parentId;
        }
    }

    public static class SchoolNameNodeDTO {
        private Long id;
        private Long parentId;
        private String displayName;
        private String name;
        private String nameEn;
        private boolean hasChildren;
        private String sortKey;

        public SchoolNameNodeDTO() {}

        public SchoolNameNodeDTO(Long id, Long parentId, String displayName, String name, String nameEn, boolean hasChildren, String sortKey) {
            this.id = id;
            this.parentId = parentId;
            this.displayName = displayName;
            this.name = name;
            this.nameEn = nameEn;
            this.hasChildren = hasChildren;
            this.sortKey = sortKey;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getNameEn() { return nameEn; }
        public void setNameEn(String nameEn) { this.nameEn = nameEn; }

        public boolean isHasChildren() { return hasChildren; }
        public void setHasChildren(boolean hasChildren) { this.hasChildren = hasChildren; }

        public String getSortKey() { return sortKey; }
        public void setSortKey(String sortKey) { this.sortKey = sortKey; }
    }
}


