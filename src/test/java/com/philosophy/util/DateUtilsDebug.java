package com.philosophy.util;

public class DateUtilsTest {
    public static void main(String[] args) {
        testFormat(-4900101); // 490 BC
        testFormat(-4900102); // c. 490 BC
        testFormat(-4900505); // Should be 490 BC with new logic (was -490.5.5)
    }

    private static void testFormat(int birthYear) {
        String result = DateUtils.formatBirthYearToDateRange(birthYear, null);
        System.out.println("Input: " + birthYear + " -> " + result);
    }
}

