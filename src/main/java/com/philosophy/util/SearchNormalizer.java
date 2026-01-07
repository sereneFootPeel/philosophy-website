package com.philosophy.util;

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

    // \p{P}：所有 Unicode 标点；\s：空白字符
    private static final Pattern STRIP_PUNCT_AND_SPACE = Pattern.compile("[\\p{P}\\s]+");

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
}


