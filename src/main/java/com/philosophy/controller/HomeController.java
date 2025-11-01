package com.philosophy.controller;


import com.philosophy.model.Philosopher;
import com.philosophy.model.School;
import com.philosophy.model.Content;
import com.philosophy.model.User;
import com.philosophy.service.PhilosopherService;
import com.philosophy.service.SchoolService;
import com.philosophy.service.CommentService;
import com.philosophy.service.TranslationService;
import com.philosophy.service.ContentService;
import com.philosophy.service.LikeService;
import com.philosophy.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import com.philosophy.util.PinyinStringComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    private final PhilosopherService philosopherService;
    private final SchoolService schoolService;
    private final CommentService commentService;
    private final TranslationService translationService;
    private final ContentService contentService;
    private final LikeService likeService;
    private final UserService userService;
    
    // 构造函数注入
    public HomeController(PhilosopherService philosopherService, SchoolService schoolService, CommentService commentService, TranslationService translationService, ContentService contentService, LikeService likeService, UserService userService) {
        this.philosopherService = philosopherService;
        this.schoolService = schoolService;
        this.commentService = commentService;
        this.translationService = translationService;
        this.contentService = contentService;
        this.likeService = likeService;
        this.userService = userService;
    }
    
    // 递归排序所有层级的流派
    private void sortSchoolsRecursively(List<School> schools, PinyinStringComparator nameComparator) {
        if (schools == null) return;
        
        // 排序当前层级的流派
        schools.sort(Comparator.comparing(s -> nameComparator.toComparableKey(s.getName())));
        
        // 递归排序每个流派的子流派
        for (School school : schools) {
            if (school.getChildren() != null && !school.getChildren().isEmpty()) {
                sortSchoolsRecursively(school.getChildren(), nameComparator);
            }
        }
    }
    
    @GetMapping("/")
    public String home(HttpServletRequest request, Model model, Authentication authentication) {
        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }
        
        // 添加身份验证相关变量
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        boolean isAdmin = isAuthenticated && authentication.getAuthorities() != null && 
                         authentication.getAuthorities().stream()
                         .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        
        // 如果用户已登录，获取用户点赞过的内容
        if (isAuthenticated) {
            com.philosophy.model.User user = (com.philosophy.model.User) authentication.getPrincipal();
            List<Content> likedContents = likeService.getUserLikedContents(user.getId());
            List<Philosopher> likedPhilosophers = likeService.getUserLikedPhilosophers(user.getId());
            List<School> likedSchools = likeService.getUserLikedSchools(user.getId());
            
            model.addAttribute("likedContents", likedContents);
            model.addAttribute("likedPhilosophers", likedPhilosophers);
            model.addAttribute("likedSchools", likedSchools);
        }
        
        // 获取最受欢迎的内容
        List<Content> popularContents = likeService.getMostLikedContents(5);
        List<Philosopher> popularPhilosophers = likeService.getMostLikedPhilosophers(5);
        List<School> popularSchools = likeService.getMostLikedSchools(5);
        
        model.addAttribute("popularContents", popularContents);
        model.addAttribute("popularPhilosophers", popularPhilosophers);
        model.addAttribute("popularSchools", popularSchools);
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        
        // 返回home页面作为首页
        return "home";
    }
    
    
    @GetMapping("/philosophers/{id}")
    public String philosopherDetail(@PathVariable Long id, Model model, Authentication authentication) {
        // 直接重定向到新的URL格式
        return "redirect:/philosophers?philosopherId=" + id;
    }
    
    @GetMapping("/schools/{id}")
    public String schoolDetail(@PathVariable Long id, Model model, Authentication authentication) {
        School school = schoolService.getSchoolById(id);
        if (school == null) {
            return "error/404";
        }
        
        // 使用新的向上计算方法：从子流派开始计算包含所有父流派
        List<Philosopher> schoolPhilosophers = schoolService.getPhilosophersBySchoolIdWithAncestors(id);

        // 获取只包含管理员和版主的内容（用于流派页面显示，包含父流派）
        List<Content> schoolContents = schoolService.getContentsBySchoolIdWithAncestorsAdminModeratorOnly(id);
        
        // 添加身份验证相关变量
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        boolean isAdmin = isAuthenticated && authentication.getAuthorities() != null && 
                         authentication.getAuthorities().stream()
                         .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        
        List<School> allSchools = schoolService.findTopLevelSchools();
        // 使用拼音/忽略大小写排序顶级流派
        PinyinStringComparator nameComparator = new PinyinStringComparator();
        allSchools.sort(Comparator.comparing(s -> nameComparator.toComparableKey(s.getName())));

        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("school", school);
        model.addAttribute("selectedSchool", school);
        model.addAttribute("topLevelSchools", allSchools);
        model.addAttribute("philosophers", schoolPhilosophers);
        model.addAttribute("contents", schoolContents);
        model.addAttribute("activePage", "schools");

        return "schools";
    }
    
    @GetMapping("/about")
    public String about() {
        return "about";
    }
    
    @GetMapping("/search")
    public String searchForm(HttpServletRequest request, Model model) {
        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }
        
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        
        return "search";
    }
    
    @GetMapping("/search/results")
    public String searchResults(String query, Model model, HttpServletRequest request, Authentication authentication) {
        if (query == null || query.trim().isEmpty()) {
            return "redirect:/search";
        }
        
        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }
        
        List<Philosopher> philosophers = philosopherService.searchPhilosophers(query);
        List<School> schools = schoolService.searchSchools(query);
        List<Content> contents = contentService.searchContents(query);
        List<User> users = userService.searchUsers(query);
        
        logger.info("搜索结果统计 - 查询词: {}, 哲学家: {}, 学派: {}, 内容(过滤前): {}, 用户: {}", 
                    query, philosophers.size(), schools.size(), contents.size(), users.size());
        
        // 获取当前用户信息用于隐私过滤
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        User currentUser = null;
        if (isAuthenticated) {
            currentUser = (User) authentication.getPrincipal();
            logger.info("当前用户: {}, 角色: {}", currentUser.getUsername(), currentUser.getRole());
        } else {
            logger.info("未登录用户访问搜索");
        }
        
        // 应用隐私过滤
        contents = contentService.filterContentsByPrivacy(contents, currentUser);
        logger.info("内容过滤后数量: {}", contents.size());
        
        model.addAttribute("query", query);
        model.addAttribute("philosophers", philosophers);
        model.addAttribute("schools", schools);
        model.addAttribute("contents", contents);
        model.addAttribute("users", users);
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        model.addAttribute("isAuthenticated", isAuthenticated);
        
        return "search/results";
    }
    
    @GetMapping("/test-likes")
    public String testLikes(Model model, Authentication authentication) {
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "test-likes";
    }
    
    @GetMapping("/test-like-component")
    public String testLikeComponent(Model model, Authentication authentication) {
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "test-like-component";
    }
    
    @GetMapping("/test-likes-debug")
    public String testLikesDebug(Model model, Authentication authentication) {
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "test-likes-debug";
    }

    @GetMapping("/test-likes-final")
    public String testLikesFinal(Model model, Authentication authentication) {
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "test-likes-final";
    }

    @GetMapping("/diagnose-likes")
    public String diagnoseLikes(Model model, Authentication authentication) {
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "diagnose-likes";
    }

    @GetMapping("/test-api")
    public String testApi(Model model, Authentication authentication) {
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "test-api";
    }

    @GetMapping("/quick-test")
    public String quickTest(Model model, Authentication authentication) {
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "quick-test";
    }

    @GetMapping("/simple-test")
    public String simpleTest(Model model, Authentication authentication) {
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "simple-test";
    }

    @GetMapping("/debug-db")
    public String debugDb(Model model, Authentication authentication) {
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "debug-db";
    }

    @GetMapping("/final-test")
    public String finalTest(Model model, Authentication authentication) {
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "final-test";
    }


    
    @GetMapping("/test-follow")
    public String testFollow(Model model, Authentication authentication) {
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("isAuthenticated", isAuthenticated);
        
        if (isAuthenticated) {
            com.philosophy.model.User currentUser = (com.philosophy.model.User) authentication.getPrincipal();
            model.addAttribute("currentUser", currentUser);
            
            // 获取所有用户用于测试
            List<com.philosophy.model.User> users = userService.getAllUsers();
            model.addAttribute("users", users);
        }
        
        return "test-follow";
    }
    
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            model.addAttribute("username", authentication.getName());
            model.addAttribute("roles", authentication.getAuthorities());
        }
        
        return "dashboard";
    }
    
    @GetMapping("/schools")
    public String schools(Model model, Authentication authentication, HttpServletRequest request) {
        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }
        
        List<School> allSchools = schoolService.findTopLevelSchools();
        // 使用拼音/忽略大小写排序顶级流派
        PinyinStringComparator nameComparator = new PinyinStringComparator();
        allSchools.sort(Comparator.comparing(s -> nameComparator.toComparableKey(s.getName())));
        List<Philosopher> allPhilosophers = philosopherService.getAllPhilosophers();

        // 添加身份验证相关变量
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        boolean isAdmin = isAuthenticated && authentication.getAuthorities() != null &&
                         authentication.getAuthorities().stream()
                         .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("topLevelSchools", allSchools);
        model.addAttribute("philosophers", allPhilosophers);
        model.addAttribute("selectedSchool", null);
        // 初始不加载内容，由用户点击流派后再加载
        model.addAttribute("contents", new ArrayList<Content>());
        model.addAttribute("translationService", translationService);
        model.addAttribute("activePage", "schools");
        model.addAttribute("language", language);
        
        return "schools";
    }
    
    @GetMapping("/schools/filter/{id}")
    public String filterBySchool(@PathVariable Long id, Model model, Authentication authentication, HttpServletRequest request) {
        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }
        
        List<School> allSchools = schoolService.findTopLevelSchools();
        // 使用拼音/忽略大小写排序顶级流派
        PinyinStringComparator nameComparator = new PinyinStringComparator();
        allSchools.sort(Comparator.comparing(s -> nameComparator.toComparableKey(s.getName())));
        School selectedSchool = schoolService.getSchoolById(id);
        
        // 添加身份验证相关变量
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        boolean isAdmin = isAuthenticated && authentication.getAuthorities() != null && 
                         authentication.getAuthorities().stream()
                         .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        
        // 获取当前用户ID
        Long currentUserId = null;
        if (isAuthenticated && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails) {
            String username = ((org.springframework.security.core.userdetails.UserDetails) authentication.getPrincipal()).getUsername();
            try {
                com.philosophy.model.User user = userService.findByUsername(username);
                if (user != null) {
                    currentUserId = user.getId();
                }
            } catch (Exception e) {
                // 如果获取用户失败，继续使用null
            }
        }
        
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("topLevelSchools", allSchools);
        model.addAttribute("selectedSchool", selectedSchool);
        
        if (selectedSchool != null) {
            try {
                // 判断是否为顶级流派（没有父流派的流派）
                boolean isTopLevelSchool = selectedSchool.getParent() == null;
                
                List<Philosopher> philosophers;
                List<Content> contents;
                
                if (isTopLevelSchool) {
                    // 顶级流派：显示该流派及其所有子流派的内容，只显示管理员、版主和用户关注的作者的内容
                    philosophers = schoolService.getPhilosophersBySchoolIdWithDescendants(selectedSchool.getId());
                    contents = schoolService.getContentsBySchoolIdFiltered(selectedSchool.getId(), currentUserId);
                } else {
                    // 子流派：只显示该子流派的内容，只显示管理员、版主和用户关注的作者的内容
                    philosophers = schoolService.getPhilosophersBySchoolIdOnly(selectedSchool.getId());
                    contents = schoolService.getContentsBySchoolIdFiltered(selectedSchool.getId(), currentUserId);
                }
                
                // 内容已经通过getContentsBySchoolIdFiltered方法按优先级排序，无需额外排序
                
                model.addAttribute("philosophers", philosophers);
                model.addAttribute("contents", contents);
                
            } catch (Exception e) {
                // 如果出现异常，使用空列表
                model.addAttribute("philosophers", new ArrayList<>());
                model.addAttribute("contents", new ArrayList<>());
            }
        } else {
            model.addAttribute("philosophers", new ArrayList<>());
            model.addAttribute("contents", new ArrayList<>());
        }
        
        model.addAttribute("activePage", "schools");
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        return "schools";
    }
    
    @GetMapping("/philosophers")
    public String philosophers(Model model, 
                              @RequestParam(required = false) Long philosopherId, 
                              @RequestParam(required = false) Long schoolId, 
                              @RequestParam(required = false) Long ideaId,
                              Authentication authentication,
                              HttpServletRequest request) {
        
        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }
        
        // 添加身份验证相关变量
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        boolean isAdmin = isAuthenticated && authentication.getAuthorities() != null && 
                         authentication.getAuthorities().stream()
                         .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        
        User currentUser = null;
        if (isAuthenticated) {
            currentUser = (User) authentication.getPrincipal();
        }
        
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("language", language);
        List<Philosopher> allPhilosophers = philosopherService.getAllPhilosophers();
        List<School> allSchools = schoolService.getAllSchools();
        
        // 按哲学家出生年份排序，处理可能为null的情况
        allPhilosophers.sort(Comparator.comparing((Philosopher p) -> {
            if (p.getBirthYear() == null) {
                return Integer.MAX_VALUE; // 没有出生年份的排到最后
            }
            return p.getBirthYear();
        }));
        
        // 如果有指定哲学家ID，则使用该哲学家作为当前哲学家
        Philosopher currentPhilosopher = null;
        if (philosopherId != null) {
            currentPhilosopher = philosopherService.getPhilosopherById(philosopherId);
        }
        // 如果没有指定或者指定的哲学家不存在，并且有哲学家列表，则使用第一个哲学家
        if (currentPhilosopher == null && !allPhilosophers.isEmpty()) {
            currentPhilosopher = allPhilosophers.get(0);
        }
        
        // 预加载当前哲学家的内容数据（包括流派关系），按优先级排序（首次只加载12条）
        if (currentPhilosopher != null) {
            Map<String, Object> result = philosopherService.getContentsByPhilosopherIdWithPriorityPaged(currentPhilosopher.getId(), 0, 12);
            @SuppressWarnings("unchecked")
            List<Content> philosopherContents = (List<Content>) result.get("contents");
            // 应用隐私和屏蔽过滤
            philosopherContents = contentService.filterContentsByPrivacy(philosopherContents, currentUser);
            currentPhilosopher.setContents(philosopherContents);
            model.addAttribute("hasMoreContents", result.get("hasMore"));
        }
        
        model.addAttribute("philosophers", allPhilosophers);
        model.addAttribute("topLevelSchools", allSchools);
        model.addAttribute("currentPhilosopher", currentPhilosopher);
        model.addAttribute("commentService", commentService);
        model.addAttribute("translationService", translationService);
        model.addAttribute("activePage", "philosophers");
        
        // 传递URL参数，以便前端JavaScript使用
        if (schoolId != null) {
            model.addAttribute("schoolId", schoolId);
        }
        
        // 传递思想ID参数，以便前端JavaScript使用
        if (ideaId != null) {
            model.addAttribute("ideaId", ideaId);
        }
        
        return "philosophers";
    }
    
    @GetMapping("/test/likes-fixed")
    public String testLikesFixed(HttpServletRequest request, Model model, Authentication authentication) {
        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }
        
        // 添加身份验证相关变量
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated();
        
        model.addAttribute("language", language);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("translationService", translationService);
        
        return "test-likes-fixed";
    }
    
    // AJAX搜索API端点
    @GetMapping("/api/search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchApi(@RequestParam String query) {
        Map<String, Object> response = new HashMap<>();
        
        if (query == null || query.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "搜索关键词不能为空");
            return ResponseEntity.badRequest().body(response);
        }
        
        if (query.length() > 100) {
            response.put("success", false);
            response.put("message", "搜索关键词长度不能超过100个字符");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            List<Philosopher> philosophers = philosopherService.searchPhilosophers(query);
            List<School> schools = schoolService.searchSchools(query);
            List<Content> contents = contentService.searchContents(query);
            
            response.put("success", true);
            response.put("query", query);
            response.put("philosophers", philosophers);
            response.put("schools", schools);
            response.put("contents", contents);
            response.put("totalResults", philosophers.size() + schools.size() + contents.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "搜索时发生错误: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    // 分页搜索API端点 - 按类别分页
    @GetMapping("/api/search/paged")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchPagedByCategory(
            @RequestParam String query,
            @RequestParam String category,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Authentication authentication) {
        
        logger.info("收到搜索分页请求 - query: {}, category: {}, page: {}, size: {}", query, category, page, size);
        
        Map<String, Object> response = new HashMap<>();
        
        if (query == null || query.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "搜索关键词不能为空");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            List<?> results = new ArrayList<>();
            int totalCount = 0;
            
            // 根据类别搜索
            switch (category.toLowerCase()) {
                case "philosophers":
                    List<Philosopher> allPhilosophers = philosopherService.searchPhilosophers(query);
                    totalCount = allPhilosophers.size();
                    int startP = page * size;
                    int endP = Math.min(startP + size, totalCount);
                    if (startP < totalCount) {
                        List<Philosopher> pagedPhilosophers = allPhilosophers.subList(startP, endP);
                        // 清除循环引用：移除schools中的philosophers引用
                        for (Philosopher p : pagedPhilosophers) {
                            if (p.getSchools() != null) {
                                for (School s : p.getSchools()) {
                                    s.setPhilosophers(null);
                                    s.setContents(null);
                                    if (s.getParent() != null) {
                                        s.getParent().setChildren(null);
                                        s.getParent().setPhilosophers(null);
                                        s.getParent().setContents(null);
                                    }
                                    s.setChildren(null);
                                }
                            }
                            // 清除内容引用
                            p.setContents(null);
                        }
                        results = pagedPhilosophers;
                    }
                    logger.info("哲学家搜索结果: 总数={}, 返回={}", totalCount, ((List<?>)results).size());
                    break;
                    
                case "schools":
                    List<School> allSchools = schoolService.searchSchools(query);
                    totalCount = allSchools.size();
                    int startS = page * size;
                    int endS = Math.min(startS + size, totalCount);
                    if (startS < totalCount) {
                        List<School> pagedSchools = allSchools.subList(startS, endS);
                        // 清除循环引用
                        for (School s : pagedSchools) {
                            s.setPhilosophers(null);
                            s.setContents(null);
                            if (s.getParent() != null) {
                                s.getParent().setChildren(null);
                                s.getParent().setPhilosophers(null);
                                s.getParent().setContents(null);
                            }
                            if (s.getChildren() != null) {
                                for (School child : s.getChildren()) {
                                    child.setParent(null);
                                    child.setChildren(null);
                                    child.setPhilosophers(null);
                                    child.setContents(null);
                                }
                            }
                        }
                        results = pagedSchools;
                    }
                    logger.info("学派搜索结果: 总数={}, 返回={}", totalCount, ((List<?>)results).size());
                    break;
                    
                case "contents":
                    List<Content> allContents = contentService.searchContents(query);
                    logger.info("内容搜索结果（过滤前）: {}", allContents.size());
                    
                    // 获取当前用户信息用于隐私过滤
                    boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
                    User currentUser = null;
                    if (isAuthenticated) {
                        currentUser = (User) authentication.getPrincipal();
                    }
                    // 应用隐私过滤
                    allContents = contentService.filterContentsByPrivacy(allContents, currentUser);
                    totalCount = allContents.size();
                    logger.info("内容搜索结果（过滤后）: {}", totalCount);
                    
                    int startC = page * size;
                    int endC = Math.min(startC + size, totalCount);
                    if (startC < totalCount) {
                        List<Content> pagedContents = allContents.subList(startC, endC);
                        logger.info("开始清除循环引用，内容数量: {}", pagedContents.size());
                        
                        // 清除循环引用
                        for (Content c : pagedContents) {
                            logger.debug("处理content id: {}", c.getId());
                            
                            // 清理philosopher的循环引用
                            if (c.getPhilosopher() != null) {
                                Philosopher p = c.getPhilosopher();
                                logger.debug("清理philosopher: {}", p.getName());
                                p.setSchools(null);
                                p.setContents(null);
                            }
                            
                            // 清理school的循环引用（需要递归清理parent链）
                            if (c.getSchool() != null) {
                                School school = c.getSchool();
                                logger.debug("清理school: {}", school.getName());
                                school.setPhilosophers(null);
                                school.setContents(null);
                                school.setChildren(null);
                                
                                // 递归清理所有parent
                                School parent = school.getParent();
                                while (parent != null) {
                                    logger.debug("清理parent school: {}", parent.getName());
                                    parent.setPhilosophers(null);
                                    parent.setContents(null);
                                    parent.setChildren(null);
                                    School nextParent = parent.getParent();
                                    parent.setParent(null);
                                    parent = nextParent;
                                }
                            }
                            
                            // 简化User引用，只保留基本信息
                            c.setLockedByUser(null);
                            c.setPrivacySetBy(null);
                            c.setBlockedBy(null);
                        }
                        
                        logger.info("循环引用清除完成");
                        results = pagedContents;
                    }
                    logger.info("内容分页结果: 返回={}", ((List<?>)results).size());
                    break;
                    
                case "users":
                    List<User> allUsers = userService.searchUsers(query);
                    totalCount = allUsers.size();
                    int startU = page * size;
                    int endU = Math.min(startU + size, totalCount);
                    if (startU < totalCount) {
                        // User对象比较简单，不包含循环引用，直接返回
                        results = allUsers.subList(startU, endU);
                    }
                    logger.info("用户搜索结果: 总数={}, 返回={}", totalCount, ((List<?>)results).size());
                    break;
                    
                default:
                    response.put("success", false);
                    response.put("message", "无效的类别: " + category);
                    return ResponseEntity.badRequest().body(response);
            }
            
            boolean hasMore = (page + 1) * size < totalCount;
            
            response.put("success", true);
            response.put("query", query);
            response.put("category", category);
            response.put("results", results);
            response.put("totalCount", totalCount);
            response.put("currentPage", page);
            response.put("hasMore", hasMore);
            
            logger.info("返回搜索结果: success=true, totalCount={}, resultsSize={}, hasMore={}", totalCount, ((List<?>)results).size(), hasMore);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("搜索时发生错误: category={}, query={}, error={}", category, query, e.getMessage(), e);
            response.put("success", false);
            response.put("message", "搜索时发生错误: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    // 测试搜索功能页面
    @GetMapping("/test-search")
    public String testSearch() {
        return "test-search";
    }
    
    // 简单搜索测试页面
    @GetMapping("/search-test")
    public String searchTest() {
        return "search-test";
    }

    // 测试内容点击功能页面
    @GetMapping("/test-content-click")
    public String testContentClick(Model model, Authentication authentication) {
        model.addAttribute("isAuthenticated", authentication != null && authentication.isAuthenticated());
        return "test-content-click";
    }

    // 内容优先级测试页面
    @GetMapping("/test-priority")
    public String testPriority(Model model, Authentication authentication, HttpServletRequest request) {
        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 获取所有流派
        List<School> allSchools = schoolService.getAllSchools();
        PinyinStringComparator nameComparator = new PinyinStringComparator();
        allSchools.sort(Comparator.comparing(s -> nameComparator.toComparableKey(s.getName())));

        // 为每个流派获取按优先级排序的内容（已经在SchoolService中实现）
        Map<Long, List<Content>> schoolContents = new HashMap<>();
        for (School school : allSchools) {
            List<Content> contents = schoolService.getContentsBySchoolIdWithPriority(school.getId());
            schoolContents.put(school.getId(), contents);
        }

        model.addAttribute("topLevelSchools", allSchools);
        model.addAttribute("schoolContents", schoolContents);
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);

        return "test-priority";
    }

    // 点赞功能调试页面
    @GetMapping("/test-like-debug")
    public String testLikeDebug(Model model, Authentication authentication, HttpServletRequest request) {
        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 添加身份验证相关变量
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);

        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);

        return "test-like-debug";
    }

    // 全部contents界面
    @GetMapping("/contents")
    public String allContents(@RequestParam(required = false) Long schoolId, 
                             @RequestParam(required = false) Long philosopherId, 
                             Model model, Authentication authentication, HttpServletRequest request) {
        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }

        // 添加身份验证相关变量
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        boolean isAdmin = isAuthenticated && authentication.getAuthorities() != null &&
                         authentication.getAuthorities().stream()
                         .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        School selectedSchool = null;
        Philosopher selectedPhilosopher = null;
        List<Content> contents = new ArrayList<>();
        List<School> relatedSchools = new ArrayList<>(); // 相关流派（当前流派及其父流派和子流派）

        // 获取当前用户信息用于隐私过滤
        User currentUser = null;
        if (isAuthenticated) {
            currentUser = (User) authentication.getPrincipal();
        }

        if (schoolId != null && philosopherId != null) {
            // 获取选中的流派和哲学家
            selectedSchool = schoolService.getSchoolById(schoolId);
            selectedPhilosopher = philosopherService.getPhilosopherById(philosopherId);

            logger.debug("=== Contents页面调试信息 ===");
            logger.debug("请求的schoolId: {}, philosopherId: {}", schoolId, philosopherId);
            logger.debug("找到的流派: {}, 哲学家: {}", 
                (selectedSchool != null ? selectedSchool.getName() : "null"),
                (selectedPhilosopher != null ? selectedPhilosopher.getName() : "null"));

            if (selectedSchool != null && selectedPhilosopher != null) {
                // 获取指定哲学家和流派的原始内容
                List<Content> originalContents = contentService.findOriginalContentsByPhilosopherAndSchool(philosopherId, schoolId);
                logger.debug("原始内容数量: {}", (originalContents != null ? originalContents.size() : 0));

                // 获取指定哲学家和流派的用户编辑内容
                List<com.philosophy.model.UserContentEdit> userEdits = contentService.findUserEditsByPhilosopherAndSchool(philosopherId, schoolId);
                logger.debug("用户编辑内容数量: {}", (userEdits != null ? userEdits.size() : 0));

                // 合并两种内容，将UserContentEdit转换为Content实体以便统一处理
                contents.addAll(originalContents);
                for (com.philosophy.model.UserContentEdit edit : userEdits) {
                    // 只转换状态为 "APPROVED" 的用户编辑内容
                    if (edit.getStatus() == com.philosophy.model.UserContentEdit.EditStatus.APPROVED) {
                        Content contentFromEdit = new Content();
                        contentFromEdit.setId(edit.getId());
                        contentFromEdit.setContent(edit.getContent());
                        contentFromEdit.setTitle(edit.getTitle());
                        contentFromEdit.setPhilosopher(edit.getPhilosopher());
                        contentFromEdit.setSchool(edit.getSchool());
                        contentFromEdit.setUser(edit.getUser());
                        // UserContentEdit 没有隐私字段，可以设置为默认值 false
                        contentFromEdit.setPrivate(false); 
                        contents.add(contentFromEdit);
                    }
                }

                // 对合并后的内容进行优先级排序
                contents = contentService.sortContentsByPriority(contents);

                // 应用隐私过滤
                contents = contentService.filterContentsByPrivacy(contents, currentUser);

                // 获取相关流派：当前流派、父流派和子流派
                relatedSchools = getRelatedSchools(selectedSchool);

                // 将用户编辑内容传递到模板 (现在已合并，无需单独传递)
                model.addAttribute("userEdits", new ArrayList<>());
            } else {
                // 流派或哲学家不存在，返回空内容
                contents = new ArrayList<>();
                relatedSchools = new ArrayList<>();
                model.addAttribute("userEdits", new ArrayList<>());
            }
        } else if (schoolId != null) {
            // 只选择了流派，显示该流派下所有哲学家的内容
            selectedSchool = schoolService.getSchoolById(schoolId);
            if (selectedSchool != null) {
                // 获取选中流派及其父流派的所有内容，按优先级排序（版主/管理员优先，其余按点赞数排序）
                contents = schoolService.getContentsBySchoolIdWithAncestorsPriority(schoolId);
                // 应用隐私和屏蔽过滤
                contents = contentService.filterContentsByPrivacy(contents, currentUser);

                // 获取相关流派：当前流派、父流派和子流派
                relatedSchools = getRelatedSchools(selectedSchool);
            } else {
                contents = new ArrayList<>();
                relatedSchools = new ArrayList<>();
            }
            model.addAttribute("userEdits", new ArrayList<>());
        } else {
            // 没有选择流派，显示所有内容，按优先级排序（首次只加载15条）
            Map<String, Object> result = contentService.findAllWithPrioritySortPaged(currentUser, 0, 15);
            @SuppressWarnings("unchecked")
            List<Content> pagedContents = (List<Content>) result.get("contents");
            contents = pagedContents;
            relatedSchools = schoolService.getAllSchools();
            model.addAttribute("userEdits", new ArrayList<>());
            model.addAttribute("hasMore", result.get("hasMore"));
        }

        // 获取所有哲学家用于筛选
        List<Philosopher> allPhilosophers = philosopherService.getAllPhilosophers();

        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("relatedSchools", relatedSchools);
        model.addAttribute("selectedSchool", selectedSchool);
        model.addAttribute("selectedPhilosopher", selectedPhilosopher);
        model.addAttribute("allPhilosophers", allPhilosophers);
        model.addAttribute("contents", contents);
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        model.addAttribute("activePage", "contents");

        return "contents";
    }

    /**
     * 获取相关流派：当前流派、父流派和子流派
     */
    private List<School> getRelatedSchools(School selectedSchool) {
        List<School> relatedSchools = new ArrayList<>();
        
        // 添加当前流派
        relatedSchools.add(selectedSchool);
        
        // 添加父流派（递归向上）
        School parent = selectedSchool.getParent();
        while (parent != null) {
            relatedSchools.add(0, parent); // 添加到开头，保持层级顺序
            parent = parent.getParent();
        }
        
        // 添加子流派（递归向下）
        addChildrenSchools(selectedSchool, relatedSchools);
        
        return relatedSchools;
    }
    
    /**
     * 递归添加子流派
     */
    private void addChildrenSchools(School school, List<School> relatedSchools) {
        if (school.getChildren() != null && !school.getChildren().isEmpty()) {
            for (School child : school.getChildren()) {
                relatedSchools.add(child);
                addChildrenSchools(child, relatedSchools); // 递归添加子流派的子流派
            }
        }
    }

    // API 端点：加载更多内容（用于内容总览页面的无限滚动）
    @GetMapping("/api/contents/more")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMoreContents(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "15") int size,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 获取当前用户信息用于隐私过滤
            boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
            User currentUser = null;
            if (isAuthenticated) {
                currentUser = (User) authentication.getPrincipal();
            }

            // 获取分页数据
            Map<String, Object> result = contentService.findAllWithPrioritySortPaged(currentUser, page, size);
            
            response.put("success", true);
            response.put("contents", result.get("contents"));
            response.put("hasMore", result.get("hasMore"));
            response.put("totalElements", result.get("totalElements"));
            response.put("currentPage", page);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error loading more contents: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // API 端点：加载更多哲学家的内容（用于哲学家页面的无限滚动）
    @GetMapping("/api/philosophers/contents/more")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getMorePhilosopherContents(
            @RequestParam("philosopherId") Long philosopherId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size,
            Authentication authentication) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            Philosopher philosopher = philosopherService.getPhilosopherById(philosopherId);
            if (philosopher == null) {
                response.put("success", false);
                response.put("message", "Philosopher not found");
                return ResponseEntity.notFound().build();
            }

            // 获取当前用户信息用于隐私过滤
            boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
            User currentUser = null;
            if (isAuthenticated) {
                currentUser = (User) authentication.getPrincipal();
            }

            // 获取分页数据
            Map<String, Object> result = philosopherService.getContentsByPhilosopherIdWithPriorityPaged(philosopherId, page, size);
            
            @SuppressWarnings("unchecked")
            List<Content> contents = (List<Content>) result.get("contents");
            
            // 应用隐私和屏蔽过滤
            contents = contentService.filterContentsByPrivacy(contents, currentUser);
            
            response.put("success", true);
            response.put("contents", contents);
            response.put("hasMore", result.get("hasMore"));
            response.put("totalElements", result.get("totalElements"));
            response.put("currentPage", page);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error loading more contents: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    // 名句推荐页面
    @GetMapping("/quotes")
    public String quotes(HttpServletRequest request, Model model, Authentication authentication) {
        // 获取当前语言设置
        HttpSession session = request.getSession();
        String language = (String) session.getAttribute("language");
        if (language == null) {
            language = "zh"; // 默认中文
        }
        
        // 添加身份验证相关变量
        boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
        boolean isAdmin = isAuthenticated && authentication.getAuthorities() != null && 
                         authentication.getAuthorities().stream()
                         .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
        
        // 获取当前用户
        User currentUser = null;
        if (isAuthenticated) {
            currentUser = (User) authentication.getPrincipal();
        }
        
        // 获取一个随机内容作为初始展示
        List<Content> randomContents = contentService.getRandomContents(1, currentUser);
        Content initialContent = randomContents.isEmpty() ? null : randomContents.get(0);
        
        model.addAttribute("content", initialContent);
        model.addAttribute("language", language);
        model.addAttribute("translationService", translationService);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        
        return "quotes";
    }
    
    // API 端点：获取随机名句
    @GetMapping("/api/quotes/random")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getRandomQuotes(
            @RequestParam(value = "count", defaultValue = "12") int count,
            @RequestParam(value = "excludeIds", required = false) String excludeIds,
            HttpServletRequest request,
            Authentication authentication) {
        
        try {
            // 获取当前语言设置
            HttpSession session = request.getSession();
            String language = (String) session.getAttribute("language");
            if (language == null) {
                language = "zh"; // 默认中文
            }
            
            // 获取当前用户（用于隐私过滤）
            boolean isAuthenticated = authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken);
            User currentUser = null;
            if (isAuthenticated) {
                currentUser = (User) authentication.getPrincipal();
            }
            
            // 解析排除的ID列表
            List<Long> excludeIdList = new ArrayList<>();
            if (excludeIds != null && !excludeIds.trim().isEmpty()) {
                String[] ids = excludeIds.split(",");
                for (String id : ids) {
                    try {
                        excludeIdList.add(Long.parseLong(id.trim()));
                    } catch (NumberFormatException e) {
                        // 忽略无效的ID
                    }
                }
            }
            
            // 获取随机内容
            List<Content> contents;
            if (excludeIdList.isEmpty()) {
                contents = contentService.getRandomContents(count, currentUser);
            } else {
                contents = contentService.getRandomContentsExcluding(count, excludeIdList, currentUser);
            }
            
            // 转换为简化的JSON格式
            List<Map<String, Object>> result = new ArrayList<>();
            for (Content content : contents) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", content.getId());
                
                // 使用 TranslationService 获取显示文本
                String displayText = translationService.getContentDisplayText(content, language);
                item.put("contentText", displayText);
                
                // 获取哲学家名称
                if (content.getPhilosopher() != null) {
                    String philosopherName = translationService.getPhilosopherDisplayName(content.getPhilosopher(), language);
                    item.put("philosopherName", philosopherName);
                } else {
                    item.put("philosopherName", language.equals("en") ? "Unknown Philosopher" : "未知哲学家");
                }
                
                result.add(item);
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("获取随机名句失败: " + e.getMessage(), e);
            return ResponseEntity.internalServerError().body(new ArrayList<>());
        }
    }

}
    
    