package com.philosophy.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 日期工具类，用于处理哲学家出生死亡日期的格式转换
 */
public class DateUtils {

    /**
     * 日期分隔符兼容：支持半角点 "." 与全角点 "．"
     */
    private static final String DATE_SEP = "[\\.．]";

    /**
     * 范围分隔符兼容：支持 "-"、"–"、"—"、"－"
     */
    private static final String RANGE_SEP = "[-–—－]";
    
    /**
     * 将日期范围字符串解析为出生日期的整数格式（YYYYMMDD）
     * 如果解析失败，返回 null
     * 支持多种格式：
     * 1. "1914.11.18 - 1975.3.4" (完整日期范围，会解析为19141118)
     * 2. "1914.11.18" (仅出生日期，会解析为19141118)
     * 3. "c.460 - 490BC" (年份范围，公元前，会解析为-4600101)
     * 4. "c.460BC" (单个年份，公元前，会解析为-4600101)
     * 5. "460 - 490BC" (年份范围，公元前，会解析为-4600101)
     * 6. "460BC" (单个年份，公元前，会解析为-4600101)
     * 7. "460" (单个年份，公元，会解析为4600101)
     * 
     * 生成的YYYYMMDD格式数字用于数据库存储和排序，例如：
     * - "1914.11.18" -> 19141118
     * - "1975.3.4" -> 19750304
     * - "460BC" -> -4600000（仅年份，月日补 00 用于排序）
     * - "c.460BC" -> -4600001（约年，仅年份，day=01 标记 circa）
     * 
     * @param dateRange 日期范围字符串，支持多种格式
     * @return 出生日期的整数格式（YYYYMMDD），如果解析失败返回 null
     */
    public static Integer parseBirthDateFromRange(String dateRange) {
        if (dateRange == null || dateRange.trim().isEmpty()) {
            return null;
        }
        
        // 移除所有空格并转换为小写以便匹配
        String cleaned = dateRange.trim().replaceAll("\\s+", " ").toLowerCase();
        
        // 1. 先尝试匹配完整日期范围格式：Y.M.D - Y.M.D
        // 兼容：
        // - 年份 1-4 位（如 121.4.26）
        // - 分隔符 "."/ "．"
        // - 范围分隔符 "-" / "–" / "—" / "－"
        Pattern pattern = Pattern.compile("^(\\d{1,4})" + DATE_SEP + "(\\d{1,2})" + DATE_SEP + "(\\d{1,2})\\s*" + RANGE_SEP + "\\s*(\\d{1,4})" + DATE_SEP + "(\\d{1,2})" + DATE_SEP + "(\\d{1,2})$");
        Matcher matcher = pattern.matcher(cleaned);
        
        if (matcher.matches()) {
            try {
                int birthYear = Integer.parseInt(matcher.group(1));
                int birthMonth = Integer.parseInt(matcher.group(2));
                int birthDay = Integer.parseInt(matcher.group(3));
                
                // 验证日期有效性
                if (birthMonth < 1 || birthMonth > 12 || birthDay < 1 || birthDay > 31) {
                    return null;
                }
                
                // 转换为 YYYYMMDD 格式
                return birthYear * 10000 + birthMonth * 100 + birthDay;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        // 2. 尝试匹配单个完整日期：Y.M.D（兼容 1-4 位年份与 "."/"．"）
        // 使用 ^ 和 $ 确保完全匹配，避免部分匹配
        pattern = Pattern.compile("^(\\d{1,4})" + DATE_SEP + "(\\d{1,2})" + DATE_SEP + "(\\d{1,2})$");
        matcher = pattern.matcher(cleaned);
        
        if (matcher.matches()) {
            try {
                int birthYear = Integer.parseInt(matcher.group(1));
                int birthMonth = Integer.parseInt(matcher.group(2));
                int birthDay = Integer.parseInt(matcher.group(3));
                
                // 验证日期有效性
                if (birthMonth < 1 || birthMonth > 12 || birthDay < 1 || birthDay > 31) {
                    return null;
                }
                
                // 转换为 YYYYMMDD 格式
                return birthYear * 10000 + birthMonth * 100 + birthDay;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        // 3a. 尝试匹配年份范围且每段可带独立 BC：c.20BC - 50 或 20BC - 50AD 或 c.460 - 490BC
        // 捕获组1: 开始 c.  2: 开始年份  3: 开始BC  4: 结束 c.  5: 结束年份  6: 结束BC
        pattern = Pattern.compile("(c\\.)?\\s*(\\d+)\\s*(bc)?\\s*" + RANGE_SEP + "\\s*(c\\.)?\\s*(\\d+)\\s*(bc)?", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(cleaned);
        
        if (matcher.find()) {
            try {
                boolean startHasC = matcher.group(1) != null;
                int birthYear = Integer.parseInt(matcher.group(2));
                String birthBC = matcher.group(3);
                if (birthBC != null && !birthBC.isEmpty()) {
                    birthYear = -birthYear;
                }
                int dateSuffix = startHasC ? 1 : 0;
                return birthYear * 10000 + (birthYear < 0 ? -dateSuffix : dateSuffix);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        // 3b. 兼容旧格式：范围末尾单一 BC（c.460 - 490BC），BC 表示两段均为公元前
        pattern = Pattern.compile("(c\\.)?\\s*(\\d+)\\s*" + RANGE_SEP + "\\s*(c\\.)?\\s*(\\d+)\\s*(bc)?", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(cleaned);
        
        if (matcher.find()) {
            try {
                boolean startHasC = matcher.group(1) != null;
                int birthYear = Integer.parseInt(matcher.group(2));
                String bcMarker = matcher.group(5);
                if (bcMarker != null && !bcMarker.isEmpty()) {
                    birthYear = -birthYear;
                }
                int dateSuffix = startHasC ? 1 : 0;
                return birthYear * 10000 + (birthYear < 0 ? -dateSuffix : dateSuffix);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        // 4. 尝试匹配单个年份格式（支持BC和c.前缀）：c.460BC 或 460BC 或 460
        // 捕获组1: c. (可选)
        // 捕获组2: 年份
        // 捕获组3: BC标记 (可选)
        pattern = Pattern.compile("(c\\.)?\\s*(\\d+)\\s*(bc)?", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(cleaned);
        
        if (matcher.find()) {
            try {
                boolean hasC = matcher.group(1) != null;
                int birthYear = Integer.parseInt(matcher.group(2));
                String bcMarker = matcher.group(3);
                
                // 如果是BC（公元前），年份为负数
                if (bcMarker != null && !bcMarker.isEmpty()) {
                    birthYear = -birthYear;
                }
                
                // 转换为 YYYYMMDD 格式
                // 仅年份时：月日补 00（用于排序）
                // 约年（c.）用 day=01 做标记，方便显示为 "c. YYYY"
                int dateSuffix = hasC ? 1 : 0;
                
                return birthYear * 10000 + (birthYear < 0 ? -dateSuffix : dateSuffix);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * 将 birthYear（YYYYMMDD 格式）和 deathYear 转换为日期范围字符串
     * 支持多种格式：
     * - 完整日期：YYYY.M.D - YYYY.M.D
     * - 年份格式（公元前）：c. 460 - 490 BC（当月份和日期都是1时，或者月份为1日期为2表示"c."）
     * - 年份格式（公元）：460 - 490（当月份和日期都是1时）
     * 
     * @param birthYear 出生日期（YYYYMMDD 格式的整数，可为负数表示公元前）
     * @param deathYear 死亡年份（整数，可选，可为负数表示公元前）
     * @return 日期范围字符串，如果 birthYear 为 null 返回空字符串
     */
    public static String formatBirthYearToDateRange(Integer birthYear, Integer deathYear) {
        if (birthYear == null) {
            return "";
        }
        
        // 解析 YYYYMMDD 格式，支持负数年份（公元前）
        boolean isNegative = birthYear < 0;
        int absBirthYear = Math.abs(birthYear);
        int year = absBirthYear / 10000;
        int month = (absBirthYear % 10000) / 100;
        int day = absBirthYear % 100;
        
        // 如果是负数年份，添加负号
        if (isNegative) {
            year = -year;
        }
        
        // 判断是否为年份格式
        // - 用户输入了月份和日期：month/day 为真实值（>=1），显示完整日期（包括 1.1）
        // - 用户只输入年份：存储为 YYYY0000（月日补 00 用于排序），显示年份
        // - 约年（c.）：存储为 YYYY0001（day=01 标记 circa），显示 "c. YYYY"
        // - 公元前年份：强制使用年份格式显示，避免显示如 -490.0.0 这样的格式
        boolean isYearOnlyFormat = isNegative || (month == 0 && (day == 0 || day == 1));
        boolean hasApproxMarker = (month == 0 && day == 1);
        
        // 预先检查死亡年份是否为BC，以决定是否在出生年份显示BC
        boolean isDeathBC = false;
        if (deathYear != null && Math.abs(deathYear) >= 10000) {
            isDeathBC = deathYear < 0;
        } else if (deathYear != null) {
            isDeathBC = deathYear < 0;
        }
        
        // 如果出生和死亡都是BC，则出生年份不显示BC，只在最后显示
        boolean showBirthBC = isNegative && (!isDeathBC || deathYear == null);
        
        // 构建出生日期部分
        String birthDateStr;
        if (isYearOnlyFormat) {
            // 年份格式：显示时不补齐 0（补齐 0 仅用于排序存储）
            StringBuilder sb = new StringBuilder();
            if (hasApproxMarker) {
                sb.append("c. ");
            }
            sb.append(Math.abs(year));
            if (showBirthBC) {
                sb.append(" BC");
            }
            birthDateStr = sb.toString();
        } else {
            // 完整日期格式：只要写了月份和日期就显示完整日期（包括 1.1）
            birthDateStr = String.format("%d.%d.%d", year, month, day);
        }
        
        // 如果有死亡年份，构建死亡日期部分
        if (deathYear != null) {
            // 如果 deathYear 是 YYYYMMDD 格式（绝对值 >= 10000），解析它
            if (Math.abs(deathYear) >= 10000) {
                boolean isNegativeDeath = deathYear < 0;
                int absDeathYear = Math.abs(deathYear);
                int deathYearInt = absDeathYear / 10000;
                int deathMonth = (absDeathYear % 10000) / 100;
                int deathDay = absDeathYear % 100;
                
                // 如果是负数年份，添加负号
                if (isNegativeDeath) {
                    deathYearInt = -deathYearInt;
                }
                
                // 判断死亡日期是否为年份格式（使用与出生日期相同的逻辑）
                boolean isDeathYearOnlyFormat = isNegativeDeath || (deathMonth == 0 && (deathDay == 0 || deathDay == 1));
                boolean deathHasApproxMarker = (deathMonth == 0 && deathDay == 1);
                
                String deathDateStr;
                if (isDeathYearOnlyFormat) {
                    // 年份格式：显示时不补齐 0（补齐 0 仅用于排序存储）
                    StringBuilder sb = new StringBuilder();
                    if (deathHasApproxMarker) {
                        sb.append("c. ");
                    }
                    sb.append(Math.abs(deathYearInt));
                    if (isNegativeDeath) {
                        sb.append(" BC");
                    }
                    deathDateStr = sb.toString();
                } else {
                    // 完整日期格式：只要写了月份和日期就显示完整日期（包括 1.1）
                    deathDateStr = String.format("%d.%d.%d", deathYearInt, deathMonth, deathDay);
                }
                
                return birthDateStr + " - " + deathDateStr;
            } else {
                // 如果只是年份（绝对值 < 10000），使用年份格式（旧数据兼容）
                String deathDateStr;
                if (deathYear < 0) {
                    deathDateStr = Math.abs(deathYear) + " BC";
                } else {
                    deathDateStr = String.valueOf(deathYear);
                }
                return birthDateStr + " - " + deathDateStr;
            }
        }
        
        // 如果没有死亡日期，只返回出生日期
        return birthDateStr;
    }
    
    /**
     * 从日期范围字符串中提取死亡日期
     * 支持多种格式：
     * 1. "1914.11.18 - 1975.3.4" (完整日期范围，会解析为19750304)
     * 2. "c. 460 - 490 BC" (年份范围，公元前，会解析为-4900101)
     * 3. "460 - 490 BC" (年份范围，公元前，会解析为-4900101)
     * 
     * @param dateRange 日期范围字符串，支持多种格式
     * @return 死亡日期的整数格式（YYYYMMDD），如果解析失败或没有死亡日期返回 null
     */
    public static Integer parseDeathYearFromRange(String dateRange) {
        if (dateRange == null || dateRange.trim().isEmpty()) {
            return null;
        }
        
        // 移除所有空格并转换为小写以便匹配
        String cleaned = dateRange.trim().replaceAll("\\s+", " ").toLowerCase();
        
        // 1. 先尝试匹配完整日期范围格式：Y.M.D - Y.M.D（同 parseBirthDateFromRange 的分隔符兼容）
        Pattern pattern = Pattern.compile("^(\\d{1,4})" + DATE_SEP + "(\\d{1,2})" + DATE_SEP + "(\\d{1,2})\\s*" + RANGE_SEP + "\\s*(\\d{1,4})" + DATE_SEP + "(\\d{1,2})" + DATE_SEP + "(\\d{1,2})$");
        Matcher matcher = pattern.matcher(cleaned);
        
        if (matcher.matches()) {
            try {
                int deathYear = Integer.parseInt(matcher.group(4));
                int deathMonth = Integer.parseInt(matcher.group(5));
                int deathDay = Integer.parseInt(matcher.group(6));
                
                // 验证日期有效性
                if (deathMonth < 1 || deathMonth > 12 || deathDay < 1 || deathDay > 31) {
                    return null;
                }
                
                // 转换为 YYYYMMDD 格式
                return deathYear * 10000 + deathMonth * 100 + deathDay;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        // 2a. 尝试匹配年份范围且每段可带独立 BC：c.20BC - 50 或 20BC - 50AD
        // 捕获组1: 开始 c.  2: 开始年份  3: 开始BC  4: 结束 c.  5: 结束年份  6: 结束BC
        pattern = Pattern.compile("(c\\.)?\\s*(\\d+)\\s*(bc)?\\s*" + RANGE_SEP + "\\s*(c\\.)?\\s*(\\d+)\\s*(bc)?", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(cleaned);
        
        if (matcher.find()) {
            try {
                boolean endHasC = matcher.group(4) != null;
                int deathYear = Integer.parseInt(matcher.group(5));
                String deathBC = matcher.group(6);
                if (deathBC != null && !deathBC.isEmpty()) {
                    deathYear = -deathYear;
                }
                int dateSuffix = endHasC ? 1 : 0;
                return deathYear * 10000 + (deathYear < 0 ? -dateSuffix : dateSuffix);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        // 2b. 兼容旧格式：范围末尾单一 BC（c.460 - 490BC），BC 表示两段均为公元前
        pattern = Pattern.compile("(c\\.)?\\s*(\\d+)\\s*" + RANGE_SEP + "\\s*(c\\.)?\\s*(\\d+)\\s*(bc)?", Pattern.CASE_INSENSITIVE);
        matcher = pattern.matcher(cleaned);
        
        if (matcher.find()) {
            try {
                boolean endHasC = matcher.group(3) != null;
                int deathYear = Integer.parseInt(matcher.group(4));
                String bcMarker = matcher.group(5);
                if (bcMarker != null && !bcMarker.isEmpty()) {
                    deathYear = -deathYear;
                }
                int dateSuffix = endHasC ? 1 : 0;
                return deathYear * 10000 + (deathYear < 0 ? -dateSuffix : dateSuffix);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        // 如果没有匹配到日期范围格式，说明只有出生日期，返回 null
        return null;
    }
    
    /**
     * 将旧格式的年份（如1999或-551）转换为YYYYMMDD格式（如19990101或-5510101）
     * 用于统一数据格式，确保所有日期都是YYYYMMDD格式，便于排序
     * 
     * @param year 年份（可能是旧格式，如1999或-551）
     * @return 转换后的YYYYMMDD格式（如19990101或-5510101），如果输入为null返回null
     */
    public static Integer convertYearToDateFormat(Integer year) {
        if (year == null) {
            return null;
        }
        
        // 如果已经是YYYYMMDD格式（绝对值 >= 10000），直接返回
        if (Math.abs(year) >= 10000) {
            return year;
        }
        
        // 如果是旧格式（年份），转换为YYYYMMDD格式（仅年份）
        // 规则：YYYY -> YYYY0000（用于排序；显示时仍为 YYYY）
        return year * 10000;
    }
}

