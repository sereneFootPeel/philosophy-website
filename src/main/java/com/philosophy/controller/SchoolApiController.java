package com.philosophy.controller;

import com.philosophy.model.School;
import com.philosophy.service.SchoolService;
import com.philosophy.service.TranslationService;
import com.philosophy.util.PinyinStringComparator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@RestController
public class SchoolApiController {

    private final SchoolService schoolService;
    private final TranslationService translationService;

    public SchoolApiController(SchoolService schoolService, TranslationService translationService) {
        this.schoolService = schoolService;
        this.translationService = translationService;
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

        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh";
        }

        List<SchoolNodeDTO> result = new ArrayList<>();
        for (School child : children) {
            boolean hasChildren = !schoolService.findByParentId(child.getId()).isEmpty();
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

    @GetMapping("/api/schools/detail")
    @Transactional(readOnly = true)
    public ResponseEntity<SchoolDetailDTO> getSchoolDetail(@RequestParam("id") Long id,
                                                           HttpServletRequest request) {
        School school = schoolService.getSchoolById(id);
        if (school == null) {
            return ResponseEntity.ok(new SchoolDetailDTO());
        }

        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh";
        }

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
}


