package com.philosophy.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 日期工具类，用于处理哲学家出生死亡日期的格式转换
 */
public class DateUtils {
    
    /**
     * 将日期范围字符串解析为出生日期的整数格式（YYYYMMDD）
     * 如果解析失败，返回 null
     * 支持两种格式：
     * 1. "1914.11.18 - 1975.3.4" (完整日期范围，会解析为19141118)
     * 2. "1914.11.18" (仅出生日期，会解析为19141118)
     * 
     * 生成的YYYYMMDD格式数字用于数据库存储和排序，例如：
     * - "1914.11.18" -> 19141118
     * - "1975.3.4" -> 19750304
     * 
     * @param dateRange 日期范围字符串，格式：YYYY.M.D - YYYY.M.D 或 YYYY.M.D
     * @return 出生日期的整数格式（YYYYMMDD），如果解析失败返回 null
     */
    public static Integer parseBirthDateFromRange(String dateRange) {
        if (dateRange == null || dateRange.trim().isEmpty()) {
            return null;
        }
        
        // 移除所有空格
        String cleaned = dateRange.trim().replaceAll("\\s+", " ");
        
        // 先尝试匹配完整日期范围格式：YYYY.M.D - YYYY.M.D
        Pattern pattern = Pattern.compile("(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})\\s*-\\s*(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})");
        Matcher matcher = pattern.matcher(cleaned);
        
        if (matcher.find()) {
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
        
        // 如果没有匹配到日期范围，尝试匹配单个日期：YYYY.M.D
        pattern = Pattern.compile("(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})");
        matcher = pattern.matcher(cleaned);
        
        if (matcher.find()) {
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
        
        return null;
    }
    
    /**
     * 将 birthYear（YYYYMMDD 格式）和 deathYear 转换为日期范围字符串
     * 格式：YYYY.M.D - YYYY.M.D 或 YYYY.M.D（如果没有死亡日期）
     * 支持负数年份（公元前），如：-5510101 -> -551.1.1
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
        
        // 构建出生日期部分
        String birthDateStr = String.format("%d.%d.%d", year, month, day);
        
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
                
                String deathDateStr = String.format("%d.%d.%d", deathYearInt, deathMonth, deathDay);
                return birthDateStr + " - " + deathDateStr;
            } else {
                // 如果只是年份（绝对值 < 10000），使用默认的 12.31
                return birthDateStr + " - " + deathYear + ".12.31";
            }
        }
        
        // 如果没有死亡日期，只返回出生日期
        return birthDateStr;
    }
    
    /**
     * 从日期范围字符串中提取死亡日期
     * 
     * @param dateRange 日期范围字符串，格式：YYYY.M.D - YYYY.M.D
     * @return 死亡日期的整数格式（YYYYMMDD），如果解析失败或没有死亡日期返回 null
     */
    public static Integer parseDeathYearFromRange(String dateRange) {
        if (dateRange == null || dateRange.trim().isEmpty()) {
            return null;
        }
        
        // 移除所有空格
        String cleaned = dateRange.trim().replaceAll("\\s+", " ");
        
        // 匹配格式：YYYY.M.D - YYYY.M.D（必须包含死亡日期）
        Pattern pattern = Pattern.compile("(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})\\s*-\\s*(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})");
        Matcher matcher = pattern.matcher(cleaned);
        
        if (matcher.find()) {
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
        
        // 如果是旧格式（年份），转换为YYYYMMDD格式
        // 对于正数年份：1999 -> 19990101（1月1日）
        // 对于负数年份（公元前）：-551 -> -5510101（1月1日）
        if (year < 0) {
            // 公元前年份：-551 -> -5510101
            return year * 10000 - 101;
        } else {
            // 公元年份：1999 -> 19990101
            return year * 10000 + 101;
        }
    }
}

