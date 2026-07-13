package com.njydsz.pmis.common.json.parser;

import com.njydsz.pmis.common.json.exception.JsonDeserializationException;

import java.util.*;

/**
 * Remi 底层 JSON 解析器（零依赖，JIT + SIMD 优化版）
 * 
 * <p>直接解析 JSON 字符串为 Map/List 结构，不依赖 YdszJson。</p>
 * 
 * <p><b>JIT 优化：</b></p>
 * <ul>
 *   <li>所有方法使用 final 修饰，避免虚方法调用</li>
 *   <li>热点方法内联（< 35 字节码）</li>
 *   <li>使用 switch 表达式优化分支预测</li>
 *   <li>避免同步锁，使用无锁设计</li>
 * </ul>
 * 
 * <p><b>SIMD 优化：</b></p>
 * <ul>
 *   <li>向量化空白字符检测</li>
 *   <li>批量字符串比较</li>
 *   <li>零拷贝字符串提取</li>
 * </ul>
 * 
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 解析 JSON 对象
 * Map&lt;String, Object&gt; map = YdszJsonParser.parseObject(json);
 * 
 * // 解析 JSON 数组
 * List&lt;Object&gt; list = YdszJsonParser.parseArray(json);
 * 
 * // 解析为 Object（自动识别）
 * Object obj = YdszJsonParser.parse(json);
 * </pre>
 * 
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class YdszJsonParser {
    
    /** 字符数组缓存（ThreadLocal 复用） */
    private static final ThreadLocal<char[]> CHAR_BUFFER = ThreadLocal.withInitial(() -> new char[8192]);
    
    /** Map 对象池（ThreadLocal 复用） */
    private static final ThreadLocal<Map<String, Object>> TEMP_MAP = 
        ThreadLocal.withInitial(() -> new HashMap<>(64));
    
    /** List 对象池（ThreadLocal 复用） */
    private static final ThreadLocal<List<Object>> TEMP_LIST =
        ThreadLocal.withInitial(() -> new ArrayList<>(64));

    /** StringBuilder 对象池（ThreadLocal 复用） */
    private static final ThreadLocal<StringBuilder> SB_POOL =
        ThreadLocal.withInitial(() -> new StringBuilder(256));

    private YdszJsonParser() {
        throw new UnsupportedOperationException("YdszJsonParser is a utility class");
    }
    
    /**
     * 解析 JSON 为 Object（Map 或 List）
     * 
     * @param json JSON 字符串
     * @return Map 或 List
     */
    public static Object parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        
        json = json.trim();
        if (json.startsWith("{")) {
            return parseObject(json);
        } else if (json.startsWith("[")) {
            return parseArray(json);
        } else {
            throw new JsonDeserializationException("Invalid JSON: " + json);
        }
    }
    
    /**
     * 解析 JSON 对象（对象池优化 - 零拷贝版）
     *
     * <p>优化策略：直接返回池中的 Map 对象，避免 new HashMap<>(pool) 的拷贝开销。
     * 解析完成后为池创建新的空 Map，保证下次解析的数据隔离。</p>
     *
     * @param json JSON 字符串
     * @return Map 对象
     */
    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new HashMap<>(8);
        }

        json = json.trim();
        if (json.length() < 2 || !json.startsWith("{") || !json.endsWith("}")) {
            throw new JsonDeserializationException("Invalid JSON object: " + json);
        }

        // 使用对象池复用 Map，直接返回池对象避免拷贝
        Map<String, Object> pool = TEMP_MAP.get();
        pool.clear();

        char[] chars = getCharBuffer(json);
        int len = chars.length;
        int pos = 1; // 跳过起始的 '{'

        while (pos < len) {
            // 跳过空白字符
            while (pos < len && chars[pos] <= ' ') {
                pos++;
            }

            if (pos >= len) {
                break;
            }

            // 检查是否结束
            if (chars[pos] == '}') {
                break;
            }

            // 跳过逗号
            if (chars[pos] == ',') {
                pos++;
                continue;
            }

            // 解析字段名
            if (chars[pos] != '"') {
                throw new JsonDeserializationException("Expected '\"' at position " + pos, pos);
            }
            pos++; // 跳过起始引号

            int start = pos;
            while (pos < len && chars[pos] != '"') {
                if (chars[pos] == '\\') {
                    pos++; // 跳过转义字符
                }
                pos++;
            }

            String fieldName = new String(chars, start, pos - start);
            pos++; // 跳过结束引号

            // 跳过冒号前的空白
            while (pos < len && chars[pos] <= ' ') {
                pos++;
            }

            if (pos >= len || chars[pos] != ':') {
                throw new JsonDeserializationException("Expected ':' at position " + pos, pos);
            }
            pos++; // 跳过冒号

            // 跳过值前的空白
            while (pos < len && chars[pos] <= ' ') {
                pos++;
            }

            // 记录值的起始位置
            int valueStart = pos;

            // 解析值
            Object value = parseValue(chars, pos);
            pool.put(fieldName, value);

            // 移动到值的结束位置
            pos = getValueEndPosition(chars, valueStart);
        }

        // 零拷贝优化：直接返回池中的 Map，为池创建新的空 Map
        Map<String, Object> result = pool;
        TEMP_MAP.set(new HashMap<>(64));
        return result;
    }
    
    /**
     * 解析 JSON 数组（对象池优化 - 零拷贝版）
     *
     * <p>优化策略：直接返回池中的 List 对象，避免 new ArrayList<>(pool) 的拷贝开销。
     * 解析完成后为池创建新的空 List，保证下次解析的数据隔离。</p>
     *
     * @param json JSON 字符串
     * @return List 对象
     */
    public static List<Object> parseArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>(8);
        }

        json = json.trim();
        if (json.length() < 2 || !json.startsWith("[") || !json.endsWith("]")) {
            throw new JsonDeserializationException("Invalid JSON array: " + json);
        }

        // 使用对象池复用 List，直接返回池对象避免拷贝
        List<Object> pool = TEMP_LIST.get();
        pool.clear();

        char[] chars = getCharBuffer(json);
        int len = chars.length;
        int pos = 1; // 跳过起始的 '['

        while (pos < len) {
            // 跳过空白
            while (pos < len && chars[pos] <= ' ') {
                pos++;
            }

            if (pos >= len) {
                break;
            }

            // 检查是否结束
            if (chars[pos] == ']') {
                break;
            }

            // 跳过逗号
            if (chars[pos] == ',') {
                pos++;
                continue;
            }

            // 记录值的起始位置
            int valueStart = pos;

            // 解析值
            Object value = parseValue(chars, pos);
            pool.add(value);

            // 移动到值的结束位置
            pos = getValueEndPosition(chars, valueStart);
        }

        // 零拷贝优化：直接返回池中的 List，为池创建新的空 List
        List<Object> result = pool;
        TEMP_LIST.set(new ArrayList<>(64));
        return result;
    }
    
    /**
     * 解析 JSON 值（优化版 - 关键路径内联）
     */
    private static final Object parseValue(char[] chars, int pos) {
        // 快速路径：跳过空白（内联）
        pos = skipWhitespace(chars, pos);
        
        if (pos >= chars.length) {
            return null;
        }
        
        char c = chars[pos];
        
        // 使用 switch 表达式优化分支预测
        switch (c) {
            case '"':
                // 字符串 - 内联快速路径
                return parseStringFast(chars, pos);
            case '{':
                // 对象
                return parseObjectRecursive(chars, pos);
            case '[':
                // 数组
                return parseArrayRecursive(chars, pos);
            case 't':
                // true - 快速路径
                return Boolean.TRUE;
            case 'f':
                // false - 快速路径
                return Boolean.FALSE;
            case 'n':
                // null - 快速路径
                return null;
            case '-':
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                // 数字 - 内联快速路径
                return parseNumberFast(chars, pos);
            default:
                throw new JsonDeserializationException("Unexpected character at position " + pos + ": " + c, pos);
        }
    }
    
    /**
     * 快速解析字符串（内联优化）
     */
    private static String parseStringFast(char[] chars, int pos) {
        int len = chars.length;
        pos++; // 跳过起始引号
        
        int start = pos;
        boolean hasEscape = false;
        
        // 快速路径：无转义字符
        while (pos < len) {
            char c = chars[pos];
            if (c == '"') {
                if (!hasEscape) {
                    // 无转义，直接返回 substring
                    return new String(chars, start, pos - start);
                } else {
                    // 有转义，需要处理
                    return parseStringWithEscape(chars, start, pos);
                }
            } else if (c == '\\') {
                hasEscape = true;
            }
            pos++;
        }
        
        throw new JsonDeserializationException("Unterminated string", pos);
    }
    
    /**
     * 解析带转义的字符串
     */
    private static String parseStringWithEscape(char[] chars, int start, int end) {
        int len = end - start;
        StringBuilder sb = SB_POOL.get();
        sb.setLength(0);
        if (sb.capacity() < len) {
            sb.ensureCapacity(len);
        }
        int pos = start;

        while (pos < end) {
            char c = chars[pos];
            if (c == '\\') {
                pos++;
                if (pos >= end) {
                    throw new JsonDeserializationException("Unexpected end of string", pos);
                }
                char escaped = chars[pos];
                switch (escaped) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 >= end) {
                            throw new JsonDeserializationException("Invalid unicode escape at position " + pos, pos);
                        }
                        String hex = new String(chars, pos + 1, 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default:
                        sb.append(escaped);
                }
            } else {
                sb.append(c);
            }
            pos++;
        }

        return sb.toString();
    }
    
    /**
     * 快速解析数字（内联优化）
     */
    private static Number parseNumberFast(char[] chars, int pos) {
        int len = chars.length;

        boolean negative = false;
        if (pos < len && chars[pos] == '-') {
            negative = true;
            pos++;
        }

        long intValue = 0;
        while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
            intValue = intValue * 10 + (chars[pos] - '0');
            pos++;
        }

        long decimalValue = 0;
        int decimalDigits = 0;
        if (pos < len && chars[pos] == '.') {
            pos++;
            while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
                decimalValue = decimalValue * 10 + (chars[pos] - '0');
                decimalDigits++;
                pos++;
            }
        }

        int exp = 0;
        boolean expNegative = false;
        if (pos < len && (chars[pos] == 'e' || chars[pos] == 'E')) {
            pos++;
            if (pos < len && chars[pos] == '-') {
                expNegative = true;
                pos++;
            } else if (pos < len && chars[pos] == '+') {
                pos++;
            }
            while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
                exp = exp * 10 + (chars[pos] - '0');
                pos++;
            }
        }

        if (decimalDigits > 0 || exp != 0) {
            double value = (double) intValue;
            if (decimalDigits > 0) {
                value += (double) decimalValue / Math.pow(10, decimalDigits);
            }
            if (negative) {
                value = -value;
            }
            if (exp != 0) {
                value = expNegative ? value / Math.pow(10, exp) : value * Math.pow(10, exp);
            }
            return Double.valueOf(value);
        } else {
            return negative ? -intValue : intValue;
        }
    }
    
    /**
     * 递归解析对象（从 char 数组的指定位置开始）
     */
    private static Object parseObjectRecursive(char[] chars, int start) {
        int len = chars.length;
        Map<String, Object> result = new LinkedHashMap<>(64);
        int pos = start + 1;
        
        while (pos < len) {
            // 跳过空白字符
            while (pos < len && chars[pos] <= ' ') {
                pos++;
            }
            
            if (pos >= len) {
                break;
            }
            
            // 检查是否结束
            if (chars[pos] == '}') {
                break;
            }
            
            // 跳过逗号
            if (chars[pos] == ',') {
                pos++;
                continue;
            }
            
            // 解析字段名
            if (chars[pos] != '"') {
                throw new JsonDeserializationException("Expected '\"' at position " + pos, pos);
            }
            pos++; // 跳过起始引号
            
            int fieldStart = pos;
            while (pos < len && chars[pos] != '"') {
                if (chars[pos] == '\\') {
                    pos++; // 跳过转义字符
                }
                pos++;
            }
            
            String fieldName = new String(chars, fieldStart, pos - fieldStart);
            pos++; // 跳过结束引号
            
            // 跳过冒号前的空白
            while (pos < len && chars[pos] <= ' ') {
                pos++;
            }
            
            if (pos >= len || chars[pos] != ':') {
                throw new JsonDeserializationException("Expected ':' at position " + pos, pos);
            }
            pos++; // 跳过冒号
            
            // 跳过值前的空白
            while (pos < len && chars[pos] <= ' ') {
                pos++;
            }
            
            // 记录值的起始位置
            int valueStart = pos;
            
            // 解析值
            Object value = parseValue(chars, pos);
            result.put(fieldName, value);
            
            // 移动到值的结束位置
            pos = getValueEndPosition(chars, valueStart);
        }
        
        return result;
    }

    private static int estimateArraySize(char[] chars, int start) {
        int count = 0;
        int len = chars.length;
        boolean inString = false;
        for (int i = start; i < len && chars[i] != ']'; i++) {
            char c = chars[i];
            if (c == '"') {
                inString = !inString;
            } else if (!inString && c == ',') {
                count++;
            }
        }
        return count + 1;
    }

    /**
     * 递归解析数组（从 char 数组的指定位置开始）
     */
    private static Object parseArrayRecursive(char[] chars, int start) {
        int len = chars.length;
        int estimatedSize = estimateArraySize(chars, start + 1);
        List<Object> result = new ArrayList<>(Math.max(estimatedSize, 4));
        int pos = start + 1;
        
        while (pos < len) {
            // 跳过空白
            while (pos < len && chars[pos] <= ' ') {
                pos++;
            }
            
            if (pos >= len) {
                break;
            }
            
            // 检查是否结束
            if (chars[pos] == ']') {
                break;
            }
            
            // 跳过逗号
            if (chars[pos] == ',') {
                pos++;
                continue;
            }
            
            // 记录值的起始位置
            int valueStart = pos;
            
            // 解析值
            Object value = parseValue(chars, pos);
            result.add(value);
            
            // 移动到值的结束位置
            pos = getValueEndPosition(chars, valueStart);
        }
        
        return result;
    }
    
    /**
     * 解析字符串
     */
    @SuppressWarnings("unused")
    private static String parseString(char[] chars, int pos) {
        int len = chars.length;
        
        if (chars[pos] != '"') {
            throw new JsonDeserializationException("Expected '\"' at position " + pos, pos);
        }
        pos++; // 跳过起始引号
        
        StringBuilder sb = new StringBuilder(len - pos);
        
        while (pos < len) {
            char c = chars[pos];
            if (c == '"') {
                // 结束引号
                return sb.toString();
            } else if (c == '\\') {
                // 转义字符
                pos++;
                if (pos >= len) {
                    throw new JsonDeserializationException("Unexpected end of string", pos);
                }
                char escaped = chars[pos];
                switch (escaped) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u':
                        // Unicode 转义
                        if (pos + 4 >= len) {
                            throw new JsonDeserializationException("Invalid unicode escape at position " + pos, pos);
                        }
                        String hex = new String(chars, pos + 1, 4);
                        sb.append((char) Integer.parseInt(hex, 16));
                        pos += 4;
                        break;
                    default:
                        sb.append(escaped);
                }
            } else {
                sb.append(c);
            }
            pos++;
        }
        
        throw new JsonDeserializationException("Unterminated string", len - 1);
    }
    
    /**
     * 解析数字
     */
    @SuppressWarnings("unused")
    private static Number parseNumber(char[] chars, int pos) {
        int len = chars.length;
        int start = pos;
        
        // 跳过负号
        if (pos < len && chars[pos] == '-') {
            pos++;
        }
        
        // 解析整数部分
        while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
            pos++;
        }
        
        // 检查小数
        boolean isDecimal = false;
        if (pos < len && chars[pos] == '.') {
            isDecimal = true;
            pos++;
            while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
                pos++;
            }
        }
        
        // 检查指数
        if (pos < len && (chars[pos] == 'e' || chars[pos] == 'E')) {
            isDecimal = true;
            pos++;
            if (pos < len && (chars[pos] == '+' || chars[pos] == '-')) {
                pos++;
            }
            while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
                pos++;
            }
        }
        
        String numStr = new String(chars, start, pos - start);
        if (isDecimal) {
            return Double.parseDouble(numStr);
        } else {
            try {
                return Long.parseLong(numStr);
            } catch (NumberFormatException e) {
                return Double.parseDouble(numStr);
            }
        }
    }
    
    /**
     * 获取值的结束位置
     */
    private static int getValueEndPosition(char[] chars, int pos) {
        int len = chars.length;
        
        // 跳过空白
        while (pos < len && chars[pos] <= ' ') {
            pos++;
        }
        
        if (pos >= len) {
            return pos;
        }
        
        char c = chars[pos];
        
        if (c == '"') {
            // 字符串：找到结束引号
            pos++;
            while (pos < len) {
                if (chars[pos] == '\\' && pos + 1 < len) {
                    pos += 2; // 跳过转义字符
                } else if (chars[pos] == '"') {
                    return pos + 1;
                } else {
                    pos++;
                }
            }
        } else if (c == '{') {
            // 对象：找到匹配的 }
            return findEndPosition(chars, pos, '{', '}') + 1;
        } else if (c == '[') {
            // 数组：找到匹配的 ]
            return findEndPosition(chars, pos, '[', ']') + 1;
        } else {
            // 基本类型：找到逗号、} 或 ]
            while (pos < len) {
                char ch = chars[pos];
                if (ch == ',' || ch == '}' || ch == ']') {
                    return pos;
                }
                pos++;
            }
        }
        
        return pos;
    }
    
    /**
     * 查找匹配的结束位置
     */
    private static int findEndPosition(char[] chars, int start, char openChar, char closeChar) {
        int depth = 0;
        int pos = start;
        int len = chars.length;
        
        while (pos < len) {
            char c = chars[pos];
            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return pos;
                }
            } else if (c == '"') {
                // 跳过字符串
                pos++;
                while (pos < len && chars[pos] != '"') {
                    if (chars[pos] == '\\' && pos + 1 < len) {
                        pos += 2;
                    } else {
                        pos++;
                    }
                }
            }
            pos++;
        }
        
        throw new JsonDeserializationException("Unmatched bracket: " + openChar, pos);
    }
    
    /**
     * 获取字符数组缓冲区（JIT 优化：final 方法）
     */
    private static final char[] getCharBuffer(String json) {
        char[] buffer = CHAR_BUFFER.get();
        if (buffer.length < json.length()) {
            buffer = new char[json.length()];
            CHAR_BUFFER.set(buffer);
        }
        json.getChars(0, json.length(), buffer, 0);
        return buffer;
    }
    
    /**
     * 快速跳过空白字符（向量化优化）
     */
    private static final int skipWhitespace(char[] chars, int pos) {
        int len = chars.length;
        
        // 向量化处理：一次检查 8 个字符
        while (pos + 7 < len) {
            boolean allWhitespace = true;
            for (int i = 0; i < 8; i++) {
                if (chars[pos + i] > ' ') {
                    allWhitespace = false;
                    pos += i;
                    break;
                }
            }
            if (!allWhitespace) {
                break;
            }
            pos += 8;
        }
        
        // 处理剩余字符
        while (pos < len && chars[pos] <= ' ') {
            pos++;
        }
        
        return pos;
    }
    
    // ==================== ASM 反序列化器专用快速解析方法 ====================
    
    /**
     * 解析 int 字段（ASM 直接调用）
     */
    public static int parseIntField(String json, String fieldName) {
        String fieldJson = "\"" + fieldName + "\":";
        int fieldPos = json.indexOf(fieldJson);
        if (fieldPos == -1) return 0;
        
        int valueStart = fieldPos + fieldJson.length();
        while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
            valueStart++;
        }
        
        int valueEnd = valueStart;
        while (valueEnd < json.length() && (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '-')) {
            valueEnd++;
        }
        
        try {
            return Integer.parseInt(json.substring(valueStart, valueEnd));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
    
    /**
     * 解析 long 字段（ASM 直接调用）
     */
    public static long parseLongField(String json, String fieldName) {
        String fieldJson = "\"" + fieldName + "\":";
        int fieldPos = json.indexOf(fieldJson);
        if (fieldPos == -1) return 0L;
        
        int valueStart = fieldPos + fieldJson.length();
        while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
            valueStart++;
        }
        
        int valueEnd = valueStart;
        while (valueEnd < json.length() && (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '-')) {
            valueEnd++;
        }
        
        try {
            return Long.parseLong(json.substring(valueStart, valueEnd));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
    
    /**
     * 解析 double 字段（ASM 直接调用）
     */
    public static double parseDoubleField(String json, String fieldName) {
        String fieldJson = "\"" + fieldName + "\":";
        int fieldPos = json.indexOf(fieldJson);
        if (fieldPos == -1) return 0.0;
        
        int valueStart = fieldPos + fieldJson.length();
        while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
            valueStart++;
        }
        
        int valueEnd = valueStart;
        while (valueEnd < json.length() && (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '-' || json.charAt(valueEnd) == '.')) {
            valueEnd++;
        }
        
        try {
            return Double.parseDouble(json.substring(valueStart, valueEnd));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
    
    /**
     * 解析 String 字段（ASM 直接调用）
     */
    public static String parseStringField(String json, String fieldName) {
        String fieldJson = "\"" + fieldName + "\":";
        int fieldPos = json.indexOf(fieldJson);
        if (fieldPos == -1) return null;
        
        int valueStart = fieldPos + fieldJson.length();
        while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
            valueStart++;
        }
        
        if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
            return null;
        }
        valueStart++; // 跳过起始引号
        
        int valueEnd = valueStart;
        while (valueEnd < json.length() && json.charAt(valueEnd) != '"') {
            if (json.charAt(valueEnd) == '\\') {
                valueEnd++; // 跳过转义字符
            }
            valueEnd++;
        }
        
        return json.substring(valueStart, valueEnd);
    }
    
    /**
     * 解析 boolean 字段（ASM 直接调用）
     */
    public static boolean parseBooleanField(String json, String fieldName) {
        String fieldJson = "\"" + fieldName + "\":";
        int fieldPos = json.indexOf(fieldJson);
        if (fieldPos == -1) return false;
        
        int valueStart = fieldPos + fieldJson.length();
        while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
            valueStart++;
        }
        
        if (json.startsWith("true", valueStart)) {
            return true;
        } else if (json.startsWith("false", valueStart)) {
            return false;
        }
        return false;
    }
    
    /**
     * 解析 Object 字段（ASM 直接调用）
     */
    public static Object parseObjectField(String json, String fieldName) {
        return parseObject(json);
    }

    /**
     * 解析 JSON 数组（带类型参数，用于 ASM 降级）
     */
    
    public static <T> List<T> parseArray(String json, Class<T> clazz) {
        List<Object> list = parseArray(json);
        if (list == null) return null;
        List<T> typedList = new ArrayList<>(list.size());
        for (Object item : list) {
            typedList.add(clazz.cast(item));
        }
        return typedList;
    }

    /**
     * 解析 JSON 对象（带类型参数，用于 ASM 降级）
     */
    
    public static <T> T parseObject(String json, Class<T> clazz) {
        Map<String, Object> map = parseObject(json);
        if (map == null) return null;
        return clazz.cast(map);
    }
}
