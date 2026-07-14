package com.njydsz.pmis.common.util.string;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * StringFormatterUtils - 字符串格式化工具类 (SLF4J 风格)
 * 
 * <p>提供全面的字符串格式化方法，功能对标 SLF4J MessageFormatter 和 Hutool StrFormatter，
 * 并进行了增强和优化。
 * 
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>SLF4J 风格格式化：format（使用 {} 占位符）</li>
 *   <li>数字格式化：formatNumber、formatMoney、formatPercent</li>
 *   <li>日期格式化：formatDate、formatDateTime</li>
 *   <li>JSON 风格格式化：formatJson</li>
 *   <li>XML 风格格式化：formatXml</li>
 *   <li>字符串对齐：alignLeft、alignRight、alignCenter</li>
 *   <li>缩进格式化：indent</li>
 *   <li>换行格式化：formatLines</li>
 * </ul>
 * 
 * <p><b>相比 SLF4J/Hutool 的增强：</b>
 * <ul>
 *   <li>支持数字格式化</li>
 *   <li>支持日期格式化</li>
 *   <li>支持 JSON/XML 美化</li>
 *   <li>所有方法 null 安全处理</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public class StringFormatterUtils {
    public static final String EMPTY_JSON = "{}";
    public static final char C_BACKSLASH = '\\';
    public static final char C_DELIM_START = '{';

    /**
     * 格式化字符串 ({} 占位符替换)
     *
     * @see StringUtils#format(String, Object...)
     */
    public static String format(final String strPattern, final Object... argArray) {
        return StringUtils.format(strPattern, argArray);
    }

    /**
     * 格式化数字
     */
    public static String formatNumber(Number number, String pattern) {
        if (number == null) {
            return null;
        }
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(number);
    }

    /**
     * 格式化数字（默认格式）
     */
    public static String formatNumber(Number number) {
        if (number == null) {
            return null;
        }
        return number.toString();
    }

    /**
     * 格式化金额
     */
    public static String formatMoney(Number amount) {
        if (amount == null) {
            return null;
        }
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(amount);
    }

    /**
     * 格式化百分比
     */
    public static String formatPercent(Number number) {
        if (number == null) {
            return null;
        }
        DecimalFormat df = new DecimalFormat("#0.00%");
        return df.format(number.doubleValue() / 100.0);
    }

    /**
     * 格式化日期
     */
    public static String formatDate(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return formatter.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
    }

    public static String formatDate(Date date) {
        return formatDate(date, "yyyy-MM-dd");
    }

    /**
     * 格式化日期时间
     */
    public static String formatDateTime(Date date, String pattern) {
        if (date == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return formatter.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    public static String formatDateTime(Date date) {
        return formatDateTime(date, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 格式化 LocalDate
     */
    public static String formatDate(LocalDate date, String pattern) {
        if (date == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return formatter.format(date);
    }

    public static String formatDate(LocalDate date) {
        return formatDate(date, "yyyy-MM-dd");
    }

    /**
     * 格式化 LocalDateTime
     */
    public static String formatDateTime(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return null;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return formatter.format(dateTime);
    }

    public static String formatDateTime(LocalDateTime dateTime) {
        return formatDateTime(dateTime, "yyyy-MM-dd HH:mm:ss");
    }

    /**
     * 格式化 JSON（美化）
     */
    public static String formatJson(String json) {
        if (StringUtils.isEmpty(json)) {
            return json;
        }
        
        StringBuilder sb = new StringBuilder();
        int level = 0;
        boolean inString = false;
        
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            
            if (!inString) {
                if (c == '{' || c == '[') {
                    sb.append(c).append('\n');
                    level++;
                    sb.append(repeat("  ", level));
                } else if (c == '}' || c == ']') {
                    sb.append('\n');
                    level--;
                    sb.append(repeat("  ", level));
                    sb.append(c);
                } else if (c == ',') {
                    sb.append(c).append('\n');
                    sb.append(repeat("  ", level));
                } else if (c == ':') {
                    sb.append(c).append(' ');
                } else if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    continue;
                } else {
                    sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        
        return sb.toString();
    }

    /**
     * 格式化 XML（美化）
     */
    public static String formatXml(String xml) {
        if (StringUtils.isEmpty(xml)) {
            return xml;
        }
        
        StringBuilder sb = new StringBuilder();
        int level = 0;
        
        for (int i = 0; i < xml.length(); i++) {
            char c = xml.charAt(i);
            
            if (c == '<' && i + 1 < xml.length() && xml.charAt(i + 1) == '/') {
                level--;
                sb.append('\n').append(repeat("  ", level));
            } else if (c == '<' && i + 1 < xml.length() && xml.charAt(i + 1) != '?') {
                if (i > 0 && level > 0) {
                    sb.append('\n').append(repeat("  ", level));
                }
                level++;
            } else if (c == '>' && i + 1 < xml.length() && xml.charAt(i + 1) == '<') {
                // 自闭合标签或相邻标签
            } else if (c == '>') {
                if (i + 1 < xml.length() && xml.charAt(i + 1) != '/' && xml.charAt(i + 1) != '<') {
                    level--;
                }
            }
            
            sb.append(c);
        }
        
        return sb.toString();
    }

    /**
     * 左对齐
     */
    public static String alignLeft(String str, int length) {
        return alignLeft(str, length, ' ');
    }

    /**
     * 左对齐（指定填充字符）
     */
    public static String alignLeft(String str, int length, char padChar) {
        if (str == null) {
            return null;
        }
        if (str.length() >= length) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str);
        while (sb.length() < length) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    /**
     * 右对齐
     */
    public static String alignRight(String str, int length) {
        return alignRight(str, length, ' ');
    }

    /**
     * 右对齐（指定填充字符）
     */
    public static String alignRight(String str, int length, char padChar) {
        if (str == null) {
            return null;
        }
        if (str.length() >= length) {
            return str;
        }
        StringBuilder sb = new StringBuilder();
        while (sb.length() + str.length() < length) {
            sb.append(padChar);
        }
        sb.append(str);
        return sb.toString();
    }

    /**
     * 居中对齐
     */
    public static String alignCenter(String str, int length) {
        return alignCenter(str, length, ' ');
    }

    /**
     * 居中对齐（指定填充字符）
     */
    public static String alignCenter(String str, int length, char padChar) {
        if (str == null) {
            return null;
        }
        if (str.length() >= length) {
            return str;
        }
        int totalPadding = length - str.length();
        int leftPadding = totalPadding / 2;
        int rightPadding = totalPadding - leftPadding;
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < leftPadding; i++) {
            sb.append(padChar);
        }
        sb.append(str);
        for (int i = 0; i < rightPadding; i++) {
            sb.append(padChar);
        }
        return sb.toString();
    }

    /**
     * 缩进字符串
     */
    public static String indent(String str, int indent) {
        return indent(str, indent, ' ');
    }

    /**
     * 缩进字符串（指定缩进字符）
     */
    public static String indent(String str, int indent, char indentChar) {
        if (str == null) {
            return null;
        }
        String indentStr = repeat(String.valueOf(indentChar), indent);
        return indentStr + str.replace("\n", "\n" + indentStr);
    }

    /**
     * 格式化多行字符串
     */
    public static String formatLines(String str, String lineSeparator) {
        if (str == null) {
            return null;
        }
        return str.replace("\n", lineSeparator).replace("\r", "");
    }

    /**
     * 重复字符串
     */
    private static String repeat(String str, int repeat) {
        if (str == null || repeat <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < repeat; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * 格式化对象数组
     */
    public static String formatArray(Object[] array) {
        if (array == null || array.length == 0) {
            return "[]";
        }
        return "[" + StringUtils.join(array, ", ") + "]";
    }

    /**
     * 格式化集合
     */
    public static String formatCollection(Collection<?> collection) {
        if (collection == null || collection.isEmpty()) {
            return "[]";
        }
        return "[" + StringUtils.join(collection, ", ") + "]";
    }

    /**
     * 格式化 Map
     */
    public static String formatMap(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        List<String> entries = new ArrayList<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            entries.add(entry.getKey() + "=" + entry.getValue());
        }
        return "{" + StringUtils.join(entries, ", ") + "}";
    }

    /**
     * 格式化字节大小
     */
    public static String formatByteSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
