package com.njydsz.common.json.parser;

import java.math.BigDecimal;
import java.util.*;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.exception.JsonDeserializationException;

/**
 * YdszJson 底层 JSON 解析器（零依赖，JIT + 循环展开 优化版）
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
 * <p><b>循环展开 优化：</b></p>
 * <ul>
 *   <li>向量化空白字符检测</li>
 *   <li>批量字符串比较</li>
 *   <li>零拷贝字符串提取</li>
 * </ul>
 * 
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 解析 JSON 对象
 * Map&lt;String, Object&gt; map = JsonParserUtil.parseObject(json);
 * 
 * // 解析 JSON 数组
 * List&lt;Object&gt; list = JsonParserUtil.parseArray(json);
 * 
 * // 解析为 Object（自动识别）
 * Object obj = JsonParserUtil.parse(json);
 * </pre>
 * 
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JsonParserUtil {

    /**
     * 预计算 10 的幂次表（替代 Math.pow(10, n)，避免浮点函数调用开销）。
     *
     * <p>覆盖 0~23 位小数（double 精度上限为 15-17 位有效数字，
     * 超过 22 位时 parseNumberFast 已回退到 Double.parseDouble）。</p>
     */
    private static final double[] POW10 = new double[24];
    static {
        POW10[0] = 1.0;
        for (int i = 1; i < POW10.length; i++) {
            POW10[i] = POW10[i - 1] * 10.0;
        }
    }

    /**
     * 字符数组缓存（ThreadLocal 复用）
     *
     * <p>解析器内部缓冲区，用于临时字符操作。与 {@link SerializationContext} 分离，
     * 因为 SerializationContext 仅用于序列化路径，而 CHAR_BUFFER 用于反序列化路径。</p>
     * <p>线程池环境下应调用 {@link #clearThreadLocals()} 清理，防止内存泄漏。</p>
     */
    private static final ThreadLocal<char[]> CHAR_BUFFER = ThreadLocal.withInitial(() -> new char[8192]);
    
    /**
     * StringBuilder 对象池（ThreadLocal 复用）
     *
     * <p>解析器内部缓冲区，用于构建解析结果字符串。与 {@link SerializationContext} 分离，
     * 因为 SerializationContext 仅用于序列化路径，而 SB_POOL 用于反序列化路径。</p>
     * <p>线程池环境下应调用 {@link #clearThreadLocals()} 清理，防止内存泄漏。</p>
     */
    private static final ThreadLocal<StringBuilder> SB_POOL =
        ThreadLocal.withInitial(() -> new StringBuilder(256));

    /** 是否使用 BigDecimal 解析浮点数（避免精度丢失），默认 false */
    private static volatile boolean useBigDecimal = false;

    private JsonParserUtil() {
        throw new UnsupportedOperationException("JsonParserUtil is a utility class");
    }

    /**
     * 清理所有 ThreadLocal 变量（防止线程池环境内存泄漏）。
     */
    public static void clearThreadLocals() {
        CHAR_BUFFER.remove();
        SB_POOL.remove();
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
     * 解析 JSON 对象
     *
     * <p>直接创建结果 Map，不再使用 ThreadLocal 对象池。
     * 原对象池实现每次解析都会 new HashMap(64) 赋值给 ThreadLocal，
     * 与直接创建结果 Map 产生相同的 GC 压力，还额外增加 ThreadLocal 开销。</p>
     *
     * @param json JSON 字符串
     * @return Map 对象
     */
    public static Map<String, Object> parseObject(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>(8);
        }

        char[] chars = getCharBuffer(json);
        int len = json.length();

        // 跳过前导空白
        int startPos = 0;
        while (startPos < len && chars[startPos] <= ' ') {
            startPos++;
        }

        if (startPos >= len || chars[startPos] != '{') {
            // 全空白或非对象
            if (startPos >= len) {
                return new HashMap<>(8);
            }
            throw new JsonDeserializationException("Invalid JSON object: expected '{' at position " + startPos, startPos);
        }

        Map<String, Object> result = new LinkedHashMap<>(16);

        int pos = startPos + 1; // 跳过起始的 '{'

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

            String fieldName = decodeStringIfNeeded(chars, start, pos - start);
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

            // 移动到值的结束位置（消除简单值的二次扫描）
            pos = getValueEndFast(chars, valueStart, value, len);
        }

        return result;
    }

    /**
     * 解析 JSON 数组
     *
     * <p>直接创建结果 List，不再使用 ThreadLocal 对象池。</p>
     *
     * @param json JSON 字符串
     * @return List 对象
     */
    public static List<Object> parseArray(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>(8);
        }

        json = json.trim();
        if (json.length() < 2 || json.charAt(0) != '[') {
            throw new JsonDeserializationException("Invalid JSON array: " + json);
        }

        List<Object> result = new ArrayList<>(64);

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
            result.add(value);

            // 移动到值的结束位置
            pos = getValueEndFast(chars, valueStart, value, len);
        }

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
                // true - 快速路径（校验完整 token）
                if (pos + 3 < chars.length && chars[pos + 1] == 'r'
                        && chars[pos + 2] == 'u' && chars[pos + 3] == 'e') {
                    return Boolean.TRUE;
                }
                throw new JsonDeserializationException("Unexpected token starting with 't' at position " + pos, pos);
            case 'f':
                // false - 快速路径（校验完整 token）
                if (pos + 4 < chars.length && chars[pos + 1] == 'a'
                        && chars[pos + 2] == 'l' && chars[pos + 3] == 's'
                        && chars[pos + 4] == 'e') {
                    return Boolean.FALSE;
                }
                throw new JsonDeserializationException("Unexpected token starting with 'f' at position " + pos, pos);
            case 'n':
                // null - 快速路径（校验完整 token）
                if (pos + 3 < chars.length && chars[pos + 1] == 'u'
                        && chars[pos + 2] == 'l' && chars[pos + 3] == 'l') {
                    return null;
                }
                throw new JsonDeserializationException("Unexpected token starting with 'n' at position " + pos, pos);
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
                pos++; // 跳过反斜杠后的被转义字符（如 \"），避免误判为结束引号
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
     * 设置是否使用 BigDecimal 解析浮点数。
     *
     * <p>启用后，包含小数点的数字将被解析为 {@link BigDecimal}，
     * 避免金融场景下的精度丢失。</p>
     *
     * @param enabled true 表示使用 BigDecimal
 * @author ydsz-team
 * @since 1.0.0
     */
    public static void setUseBigDecimal(boolean enabled) {
        useBigDecimal = enabled;
    }

    /**
     * 快速解析数字（内联优化）
     */
    private static Number parseNumberFast(char[] chars, int pos) {
        int len = chars.length;
        int startPos = pos;

        boolean negative = false;
        if (pos < len && chars[pos] == '-') {
            negative = true;
            pos++;
        }

        long intValue = 0;
        while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
            int digit = chars[pos] - '0';
            // 检测 long 溢出（19+ 位整数），溢出时回退到 BigDecimal/Double 路径
            if (intValue > (Long.MAX_VALUE - digit) / 10) {
                intValue = Long.MAX_VALUE; // 标记溢出，后续走精度保护路径
            } else {
                intValue = intValue * 10 + digit;
            }
            pos++;
        }

        long decimalValue = 0;
        int decimalDigits = 0;
        if (pos < len && chars[pos] == '.') {
            pos++;
            while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
                int digit = chars[pos] - '0';
                if (decimalValue <= (Long.MAX_VALUE - digit) / 10) {
                    decimalValue = decimalValue * 10 + digit;
                }
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
                if (exp < 0) {
                    throw new JsonDeserializationException("Exponent too large at position " + pos, pos);
                }
                pos++;
            }
        }

        if (decimalDigits > 0 || exp != 0) {
            // BigDecimal 路径：金融场景精度保护
            if (useBigDecimal) {
                BigDecimal bd = BigDecimal.valueOf(intValue);
                if (decimalDigits > 0) {
                    BigDecimal decimal = BigDecimal.valueOf(decimalValue)
                            .movePointLeft(decimalDigits);
                    bd = bd.add(decimal);
                }
                if (negative) {
                    bd = bd.negate();
                }
                if (exp != 0) {
                    int scale = expNegative ? exp : -exp;
                    bd = bd.scaleByPowerOfTen(scale);
                }
                return bd;
            }
            // 精度保护：intValue 超过 2^53 或 decimalDigits 超过 22 位时
            // double 无法精确表示，回退到 Double.parseDouble 避免精度丢失
            if (intValue > 9007199254740992L || decimalDigits > 22) {
                String numStr = new String(chars, startPos, pos - startPos);
                return Double.parseDouble(numStr);
            }
            double value = (double) intValue;
            if (decimalDigits > 0) {
                value += (double) decimalValue / POW10[decimalDigits];
            }
            if (negative) {
                value = -value;
            }
            if (exp != 0) {
                value = expNegative ? value / POW10[exp] : value * POW10[exp];
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
            
            String fieldName = decodeStringIfNeeded(chars, fieldStart, pos - fieldStart);
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
            pos = getValueEndFast(chars, valueStart, value, len);
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
            pos = getValueEndFast(chars, valueStart, value, len);
        }
        
        return result;
    }
    
    /**
     * 检查字符数组片段是否包含转义字符，若包含则解码转义序列，否则直接返回子串。
     *
     * <p>快速路径：先扫描是否有 '\' 字符，若无则直接 new String(chars, start, len)。</p>
     * <p>慢速路径：复用 parseStringWithEscape 逻辑解码转义序列。</p>
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param length 长度
     * @return 解码后的字符串
     */
    private static String decodeStringIfNeeded(char[] chars, int start, int length) {
        for (int i = start; i < start + length; i++) {
            if (chars[i] == '\\') {
                return parseStringWithEscape(chars, start, start + length);
            }
        }
        return new String(chars, start, length);
    }

    /**
     * 解析字符串
     */
    /** package-private */ static String parseString(char[] chars, int pos) {
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
    /** package-private */ static Number parseNumber(char[] chars, int pos) {
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
     * 获取值的结束位置（快速版，消除简单值的二次扫描）
     *
     * <p>对于 true/false/null 值，直接根据值类型计算结束位置，
     * 无需调用 getValueEndPosition 重新扫描。</p>
     *
     * @param chars JSON 字符数组
     * @param valueStart 值的起始位置
     * @param value 已解析的值
     * @param len 字符数组长度
     * @return 值的结束位置
     */
    private static int getValueEndFast(char[] chars, int valueStart, Object value, int len) {
        // 快速路径：布尔值和 null 直接计算长度
        if (value == Boolean.TRUE) {
            return valueStart + 4; // "true"
        }
        if (value == Boolean.FALSE) {
            return valueStart + 5; // "false"
        }
        if (value == null && valueStart + 4 <= len && chars[valueStart] == 'n') {
            return valueStart + 4; // "null"
        }
        // 复杂值（String/Number/Map/List）：需要扫描确定结束位置
        return getValueEndPosition(chars, valueStart);
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
     * 单遍扫描构建字段位置映射（优化 O(N*M) 为 O(N)）。
     *
     * <p>当 ASM 反序列化器需要解析多个字段时，传统方式对每个字段调用
     * {@link #findFieldPosition} 导致 O(N*M) 复杂度。此方法单遍扫描 JSON，
     * 一次性提取所有顶层字段名及其值起始位置，将复杂度降为 O(N+M)。</p>
     *
     * @param json JSON 字符串
     * @return 字段名 -> 值起始位置（冒号后第一个非空白字符）的映射
     * @since 1.0.0
     */
    public static Map<String, Integer> buildFieldPositionMap(String json) {
        Map<String, Integer> fieldPositions = new HashMap<>(16);
        int len = json.length();
        int i = 0;
        // 跳过前导空白
        while (i < len && json.charAt(i) <= ' ') i++;
        if (i >= len || json.charAt(i) != '{') return fieldPositions;
        i++; // 跳过 '{'

        while (i < len) {
            // 跳过空白
            while (i < len && json.charAt(i) <= ' ') i++;
            if (i >= len) break;
            if (json.charAt(i) == '}') break;
            if (json.charAt(i) == ',') { i++; continue; }

            // 读取字段名（带引号）
            if (json.charAt(i) != '"') break;
            i++; // 跳过起始引号
            int nameStart = i;
            while (i < len && json.charAt(i) != '"') {
                if (json.charAt(i) == '\\') i++;
                i++;
            }
            String fieldName = json.substring(nameStart, i);
            i++; // 跳过结束引号

            // 跳过冒号和空白
            while (i < len && json.charAt(i) != ':') i++;
            i++; // 跳过冒号
            while (i < len && json.charAt(i) <= ' ') i++;

            // 记录值起始位置
            fieldPositions.put(fieldName, i);

            // 跳过值（根据类型）
            i = skipValue(json, i);
        }
        return fieldPositions;
    }

    /**
     * 跳过 JSON 值，返回值结束后的下一个位置。
     */
    private static int skipValue(String json, int start) {
        int len = json.length();
        if (start >= len) return start;
        char c = json.charAt(start);
        if (c == '"') {
            // 字符串值
            int i = start + 1;
            while (i < len) {
                if (json.charAt(i) == '\\') { i += 2; continue; }
                if (json.charAt(i) == '"') return i + 1;
                i++;
            }
            return i;
        } else if (c == '{' || c == '[') {
            // 嵌套对象/数组：计算深度
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = start; i < len; i++) {
                char ch = json.charAt(i);
                if (inString) {
                    if (escaped) { escaped = false; }
                    else if (ch == '\\') { escaped = true; }
                    else if (ch == '"') { inString = false; }
                } else {
                    if (ch == '"') { inString = true; }
                    else if (ch == '{' || ch == '[') { depth++; }
                    else if (ch == '}' || ch == ']') { depth--; if (depth == 0) return i + 1; }
                }
            }
            return len;
        } else {
            // 基本类型（number/boolean/null）
            int i = start;
            while (i < len) {
                char ch = json.charAt(i);
                if (ch == ',' || ch == '}' || ch == ']' || ch <= ' ') return i;
                i++;
            }
            return i;
        }
    }    
    /**
     * 在 JSON 中查找字段名的位置（跳过字符串值内部的文本）。
     *
     * <p>使用此方法替代 {@code json.indexOf(fieldJson)}，
     * 避免 JSON 字符串值中包含类似字段名格式的文本时误匹配。</p>
     *
     * @param json JSON 字符串
     * @param fieldJson 字段 JSON 片段（如 {@code "name":}）
     * @return 字段位置，未找到返回 -1
     */
    static int findFieldPosition(String json, String fieldJson) {
        int len = json.length();
        int fieldLen = fieldJson.length();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i <= len - fieldLen; i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else {
                if (c == '"') {
                    inString = true;
                } else if (c == fieldJson.charAt(0) && json.regionMatches(i, fieldJson, 0, fieldLen)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 解析 int 字段（ASM 直接调用）
     */
    public static int parseIntField(String json, String fieldName) {
        String fieldJson = "\"" + fieldName + "\":";
        int fieldPos = findFieldPosition(json, fieldJson);
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
        int fieldPos = findFieldPosition(json, fieldJson);
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
        int fieldPos = findFieldPosition(json, fieldJson);
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
        int fieldPos = findFieldPosition(json, fieldJson);
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
        boolean hasEscape = false;
        while (valueEnd < json.length() && json.charAt(valueEnd) != '"') {
            if (json.charAt(valueEnd) == '\\') {
                valueEnd++; // 跳过转义字符
                hasEscape = true;
            }
            valueEnd++;
        }
        
        // 如果包含转义字符，需要解码后再返回
        if (hasEscape) {
            char[] chars = json.toCharArray();
            return parseStringWithEscape(chars, valueStart, valueEnd);
        }
        return json.substring(valueStart, valueEnd);
    }
    
    /**
     * 解析 boolean 字段（ASM 直接调用）
     */
    public static boolean parseBooleanField(String json, String fieldName) {
        String fieldJson = "\"" + fieldName + "\":";
        int fieldPos = findFieldPosition(json, fieldJson);
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
     * 解析指定字段的值（ASM 直接调用）。
     *
     * <p>从 JSON 中查找指定字段名的值并解析为 Object。</p>
     *
     * @param json JSON 字符串
     * @param fieldName 字段名
     * @return 字段值，字段不存在时返回 null
     */
    public static Object parseObjectField(String json, String fieldName) {
        String fieldJson = "\"" + fieldName + "\":";
        int fieldPos = findFieldPosition(json, fieldJson);
        if (fieldPos == -1) return null;

        int valueStart = fieldPos + fieldJson.length();
        while (valueStart < json.length() && json.charAt(valueStart) <= ' ') {
            valueStart++;
        }

        if (valueStart >= json.length()) {
            return null;
        }

        char c = json.charAt(valueStart);
        if (c == '"') {
            return parseStringField(json, fieldName);
        } else if (c == '{') {
            int end = findEndPosition(json.toCharArray(), valueStart, '{', '}') + 1;
            return parseObject(json.substring(valueStart, end));
        } else if (c == '[') {
            int end = findEndPosition(json.toCharArray(), valueStart, '[', ']') + 1;
            return parseArray(json.substring(valueStart, end));
        } else if (json.startsWith("true", valueStart)) {
            return Boolean.TRUE;
        } else if (json.startsWith("false", valueStart)) {
            return Boolean.FALSE;
        } else if (json.startsWith("null", valueStart)) {
            return null;
        } else {
            // 数值
            int valueEnd = valueStart;
            while (valueEnd < json.length()
                    && json.charAt(valueEnd) != ','
                    && json.charAt(valueEnd) != '}'
                    && json.charAt(valueEnd) != ']'
                    && json.charAt(valueEnd) > ' ') {
                valueEnd++;
            }
            String numStr = json.substring(valueStart, valueEnd);
            try {
                if (numStr.contains(".") || numStr.contains("e") || numStr.contains("E")) {
                    return Double.parseDouble(numStr);
                }
                return Long.parseLong(numStr);
            } catch (NumberFormatException e) {
                return numStr;
            }
        }
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
        // Map 及其子类直接 cast 返回
        if (Map.class.isAssignableFrom(clazz)) {
            Map<String, Object> map = parseObject(json);
            if (map == null) return null;
            return clazz.cast(map);
        }
        // 非 Map 类型：解析为 Map 后委托 YdszJson 反序列化为目标 Bean
        Map<String, Object> map = parseObject(json);
        if (map == null) return null;
        return YdszJson.toObject(
            YdszJson.toJson(map), clazz);
    }
}
