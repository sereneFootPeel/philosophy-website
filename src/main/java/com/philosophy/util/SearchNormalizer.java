package com.philosophy.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 搜索关键词规范化：
 * - 去掉首尾空白
 * - 移除所有标点符号与空白（例如：? ？ · 、 ， 。 等）
 * - 统一为小写（方便英文搜索）
 *
 * 目的：让“亚当斯密？”能匹配到“亚当·斯密”等带中点/标点的条目。
 */
public final class SearchNormalizer {
    private SearchNormalizer() {}

    /** 多关键词时，两个关键词之间允许的最大间隔字符数（超过则不算匹配，用于收紧内容搜索）。若用户用空格分隔关键词则不应用此限制。 */
    public static final int MAX_KEYWORD_GAP_CHARS = 30;
    /** 仅当规范化查询词长度达到该阈值时，才启用子序列模糊匹配，避免短词匹配过宽。 */
    public static final int MIN_SUBSEQUENCE_QUERY_LENGTH = 5;

    // \p{P}：所有 Unicode 标点；\s：空白字符
    private static final Pattern STRIP_PUNCT_AND_SPACE = Pattern.compile("[\\p{P}\\s]+");
    private static final Pattern SPLIT_WORDS = Pattern.compile("\\s+");

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String stripped = STRIP_PUNCT_AND_SPACE.matcher(trimmed).replaceAll("");
        return stripped.toLowerCase(Locale.ROOT);
    }

    /**
     * 将查询按空白拆成多个“词”，并对每个词做规范化（去标点、小写），跳过空串。
     * 用于多关键词搜索时做“词间距”限制。
     */
    public static List<String> normalizedWords(String query) {
        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }
        String[] parts = SPLIT_WORDS.split(query.trim());
        List<String> words = new ArrayList<>();
        for (String p : parts) {
            String n = normalize(p);
            if (!n.isEmpty()) {
                words.add(n);
            }
        }
        return words;
    }

    /**
     * 判断规范化后的文本中，是否按顺序出现所有规范化词，且相邻两词之间间隔不超过 maxGap 个字符。
     * “间隔”指从上一词结束位置到下一词开始位置之间的字符数（不包含两词本身）。
     *
     * @param normalizedText 已规范化的文本（小写、已去标点/空格）
     * @param normalizedWords 已规范化的关键词列表（顺序）
     * @param maxGap 允许的最大间隔字符数
     * @return 若所有词按序出现且间隔均不超过 maxGap 则 true
     */
    public static boolean matchesWithMaxGap(String normalizedText, List<String> normalizedWords, int maxGap) {
        if (normalizedText == null || normalizedWords == null || normalizedWords.isEmpty()) {
            return normalizedWords != null && normalizedWords.isEmpty();
        }
        int fromIndex = 0;
        for (String word : normalizedWords) {
            if (word.isEmpty()) continue;
            int start = normalizedText.indexOf(word, fromIndex);
            if (start == -1) return false;
            if (fromIndex > 0 && start - fromIndex > maxGap) return false;
            fromIndex = start + word.length();
        }
        return true;
    }

    /**
     * 根据规范化后的关键词生成“子序列”LIKE 模式：
     * 在关键词的每个字符之间插入通配符，使「哈利波特」能匹配「哈利.詹姆.波特」等中间带其他字的情况。
     * 返回模式已对 LIKE 中的 % _ \ 做转义，使用时需配合 ESCAPE '\\'。
     */
    public static String buildSubsequenceLikePattern(String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            return "%";
        }
        StringBuilder sb = new StringBuilder("%");
        for (int i = 0; i < normalizedQuery.length(); i++) {
            char c = normalizedQuery.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                sb.append('\\');
            }
            sb.append(c);
            if (i < normalizedQuery.length() - 1) {
                sb.append('%');
            }
        }
        sb.append('%');
        return sb.toString();
    }

    /**
     * 是否启用“子序列”模糊匹配。
     * 规则：
     * 1) 查询词过短（< MIN_SUBSEQUENCE_QUERY_LENGTH）时禁用；
     * 2) 用户显式用空格分词时禁用（此时更适合按词匹配）。
     */
    public static boolean shouldEnableSubsequence(String rawQuery, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isEmpty()) {
            return false;
        }
        if (normalizedQuery.length() < MIN_SUBSEQUENCE_QUERY_LENGTH) {
            return false;
        }
        return rawQuery == null || !rawQuery.trim().contains(" ");
    }

    /**
     * 判断查询是否为纯 ASCII 字母数字词（无空格、无符号）。
     * 对这类查询建议采用更严格的字面匹配，避免“去空格归一化”带来的过度命中。
     */
    public static boolean isAsciiAlnumToken(String rawQuery) {
        if (rawQuery == null) {
            return false;
        }
        String trimmed = rawQuery.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return trimmed.matches("^[A-Za-z0-9]+$");
    }

    public static boolean containsIgnoreCase(String text, String query) {
        if (text == null || query == null) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
}


