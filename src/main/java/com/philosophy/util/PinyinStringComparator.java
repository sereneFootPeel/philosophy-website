package com.philosophy.util;

import com.github.promeg.pinyinhelper.Pinyin;

import java.util.Comparator;

public class PinyinStringComparator implements Comparator<String> {

    @Override
    public int compare(String a, String b) {
        String sa = toComparableKey(a);
        String sb = toComparableKey(b);
        return sa.compareTo(sb);
    }

    public String toComparableKey(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        StringBuilder key = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (isChinese(c)) {
                key.append(Pinyin.toPinyin(c).toLowerCase().charAt(0));
            } else {
                key.append(Character.toLowerCase(c));
            }
        }
        return key.toString();
    }

    private boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_C
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_D
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_E
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_F
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_FORMS
                || block == Character.UnicodeBlock.CJK_RADICALS_SUPPLEMENT
                || block == Character.UnicodeBlock.KANGXI_RADICALS
                || block == Character.UnicodeBlock.IDEOGRAPHIC_DESCRIPTION_CHARACTERS;
    }
}


