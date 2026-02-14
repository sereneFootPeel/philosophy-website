package com.philosophy.controller;

import com.philosophy.model.TestResult;
import com.philosophy.model.User;
import com.philosophy.service.TestResultService;
import com.philosophy.service.UserService;
import com.philosophy.util.LanguageUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
public class TestResultController {

    private final TestResultService testResultService;
    private final UserService userService;
    private final LanguageUtil languageUtil;
    private final ObjectMapper objectMapper;

    public TestResultController(TestResultService testResultService, UserService userService, LanguageUtil languageUtil, ObjectMapper objectMapper) {
        this.testResultService = testResultService;
        this.userService = userService;
        this.languageUtil = languageUtil;
        this.objectMapper = objectMapper;
    }

    /** 保存测试结果（需登录） */
    @PostMapping("/api/test-results")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> save(
            @RequestParam String testType,
            @RequestParam String resultSummary,
            @RequestParam(required = false) String resultJson,
            @RequestParam(defaultValue = "false") boolean isPublic,
            Authentication authentication) {
        Map<String, Object> body = new HashMap<>();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            body.put("success", false);
            body.put("message", "请先登录后再保存");
            return ResponseEntity.ok(body);
        }
        User user = userService.findByUsername(authentication.getName());
        if (user == null) {
            body.put("success", false);
            body.put("message", "用户不存在");
            return ResponseEntity.ok(body);
        }
        if (testType == null || testType.isBlank() || resultSummary == null || resultSummary.isBlank()) {
            body.put("success", false);
            body.put("message", "测试类型和结果摘要不能为空");
            return ResponseEntity.ok(body);
        }
        String type = testType.trim().toLowerCase();
        if (!type.matches("^(enneagram|mbti|bigfive|mmpi|values8)$")) {
            body.put("success", false);
            body.put("message", "不支持的测试类型");
            return ResponseEntity.ok(body);
        }
        TestResult saved = testResultService.save(user, type, resultSummary.trim(), resultJson, isPublic);
        body.put("success", true);
        body.put("message", "已保存到我的主页");
        body.put("id", saved.getId());
        return ResponseEntity.ok(body);
    }

    /** 更新可见性 */
    @PatchMapping("/api/test-results/{id}/visibility")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateVisibility(
            @PathVariable Long id,
            @RequestParam boolean isPublic,
            Authentication authentication) {
        Map<String, Object> body = new HashMap<>();
        if (authentication == null || !authentication.isAuthenticated()) {
            body.put("success", false);
            body.put("message", "请先登录");
            return ResponseEntity.ok(body);
        }
        User user = userService.findByUsername(authentication.getName());
        if (user == null) {
            body.put("success", false);
            body.put("message", "用户不存在");
            return ResponseEntity.ok(body);
        }
        boolean ok = testResultService.updateVisibility(id, user.getId(), isPublic);
        body.put("success", ok);
        body.put("message", ok ? (isPublic ? "已设为公开" : "已设为仅自己可见") : "无权限或记录不存在");
        if (ok) body.put("isPublic", isPublic);
        return ResponseEntity.ok(body);
    }

    /** 删除记录 */
    @DeleteMapping("/api/test-results/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, Authentication authentication) {
        Map<String, Object> body = new HashMap<>();
        if (authentication == null || !authentication.isAuthenticated()) {
            body.put("success", false);
            body.put("message", "请先登录");
            return ResponseEntity.ok(body);
        }
        User user = userService.findByUsername(authentication.getName());
        if (user == null) {
            body.put("success", false);
            body.put("message", "用户不存在");
            return ResponseEntity.ok(body);
        }
        boolean ok = testResultService.deleteByIdAndUser(id, user.getId());
        body.put("success", ok);
        body.put("message", ok ? "已删除" : "无权限或记录不存在");
        return ResponseEntity.ok(body);
    }

    /** 查看单条测试记录详情 */
    @GetMapping("/user/test-results/{id}")
    public String viewResult(@PathVariable Long id, Model model, Authentication authentication, HttpServletRequest request) {
        User viewer = null;
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            viewer = userService.findByUsername(authentication.getName());
        }
        Optional<TestResult> opt = testResultService.findByIdForView(id, viewer);
        if (opt.isEmpty()) {
            model.addAttribute("errorMessage", "记录不存在或无权查看");
            return "error";
        }
        TestResult r = opt.get();
        String language = languageUtil.getLanguage(request);
        boolean isOwner = viewer != null && viewer.getId().equals(r.getUser().getId());

        model.addAttribute("record", r);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("language", language);
        return "user/test-result-detail";
    }

    /** 主页卡片的分数预览（避免在 HTML 中内嵌完整 resultJson） */
    @GetMapping("/api/test-results/{id}/scores")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getScorePreview(@PathVariable Long id, Authentication authentication) {
        Map<String, Object> body = new HashMap<>();
        User viewer = null;
        if (authentication != null && authentication.isAuthenticated() && !(authentication instanceof AnonymousAuthenticationToken)) {
            viewer = userService.findByUsername(authentication.getName());
        }

        Optional<TestResult> opt = testResultService.findByIdForView(id, viewer);
        if (opt.isEmpty()) {
            body.put("success", false);
            body.put("message", "记录不存在或无权查看");
            return ResponseEntity.ok(body);
        }

        TestResult record = opt.get();
        String resultJson = record.getResultJson();
        if (resultJson == null || resultJson.isBlank()) {
            body.put("success", true);
            body.put("lines", java.util.Collections.emptyList());
            return ResponseEntity.ok(body);
        }

        try {
            JsonNode root = objectMapper.readTree(resultJson);
            java.util.List<String> lines = new java.util.ArrayList<>();
            String testType = record.getTestType() == null ? "" : record.getTestType();

            if ("enneagram".equals(testType) && root.has("scores") && root.get("scores").isObject()) {
                JsonNode scores = root.get("scores");
                JsonNode typeNames = root.has("typeNames") ? root.get("typeNames") : null;
                for (int t = 1; t <= 9; t++) {
                    String key = String.valueOf(t);
                    String name = (typeNames != null && typeNames.has(key)) ? typeNames.get(key).asText("") : "";
                    int score = scores.has(key) ? scores.get(key).asInt(0) : 0;
                    lines.add(name.isEmpty() ? ("型" + t + ": " + score) : ("型" + t + " " + name + ": " + score));
                }
            } else if ("mbti".equals(testType) && root.has("scores") && root.get("scores").isObject()) {
                JsonNode scores = root.get("scores");
                String[][] dims = {{"E", "I"}, {"S", "N"}, {"T", "F"}, {"J", "P"}};
                for (String[] d : dims) {
                    int a = scores.has(d[0]) ? scores.get(d[0]).asInt(0) : 0;
                    int b = scores.has(d[1]) ? scores.get(d[1]).asInt(0) : 0;
                    lines.add(d[0] + "-" + d[1] + ": " + d[0] + " " + a + " / " + d[1] + " " + b);
                }
            } else if ("values8".equals(testType) && root.has("scores") && root.get("scores").isObject()) {
                JsonNode scores = root.get("scores");
                JsonNode labels = root.has("labels") ? root.get("labels") : null;
                String[] axes = {"econ", "dipl", "govt", "scty"};
                for (String axis : axes) {
                    double raw = scores.has(axis) ? scores.get(axis).asDouble(0) : 0;
                    double left = ("econ".equals(axis) || "govt".equals(axis)) ? raw : round1(100 - raw);
                    double right = round1(100 - left);
                    String leftName = axis;
                    String rightName = "";
                    if (labels != null && labels.has(axis) && labels.get(axis).isArray() && labels.get(axis).size() >= 2) {
                        leftName = labels.get(axis).get(0).asText(axis);
                        rightName = labels.get(axis).get(1).asText("");
                    }
                    lines.add(leftName + " " + left + "% / " + right + "% " + rightName);
                }
            } else if (root.has("scores") && root.get("scores").isObject()) {
                JsonNode scores = root.get("scores");
                java.util.Iterator<String> keys = scores.fieldNames();
                while (keys.hasNext()) {
                    String k = keys.next();
                    lines.add(k + ": " + scores.get(k).asText(""));
                }
            }

            body.put("success", true);
            body.put("lines", lines);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            body.put("success", false);
            body.put("message", "结果解析失败");
            return ResponseEntity.ok(body);
        }
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
