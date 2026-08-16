package com.njydsz.common.json.parser;

import java.math.BigDecimal;
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

    /** 是否使用 BigDecimal 解析浮点数（避免精度丢失），默认 false（按线程隔离，避免跨线程泄漏） */
    private static final ThreadLocal<Boolean> useBigDecimal = ThreadLocal.withInitial(() -> false);

    /** 递归解析最大嵌套深度（防止栈溢出攻击），与 JSONReader.DEFAULT_MAX_DEPTH 对齐，默认 256 */
    private static volatile int maxParseDepth = 256;

    /**
     * 线程级解析深度覆盖（P0-3 修复：多 Mapper 实例隔离）。
     *
     * <p>{@code JsonMapper} 调用期间将自身 maxDepth 写入本覆盖，
     * {@link #resolveMaxParseDepth()} 优先读取，避免静态全局值被多实例互相覆盖。
     * 与 {@code JSONReader#setCallDepthOverride} 保持同一套语义。</p>
     *
     * @since 1.2.3
     */
    private static final ThreadLocal<Integer> CALL_PARSE_DEPTH = new ThreadLocal<>();

    /** 设置全局递归解析最大嵌套深度（默认 256，与 JSONReader 对齐） */
    public static void setMaxParseDepth(int depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("maxParseDepth must be > 0, got: " + depth);
        }
        maxParseDepth = depth;
    }

    /** 获取当前递归解析最大嵌套深度 */
    public static int getMaxParseDepth() {
        return maxParseDepth;
    }

    /**
     * 设置线程级解析深度覆盖（框架内部使用，供 JsonMapper 调用期间隔离实例配置）。
     *
     * @param depth 覆盖值（null 清除覆盖，回退静态全局值）
     * @since 1.2.3
     */
    public static void setCallParseDepthOverride(Integer depth) {
        if (depth != null) {
            if (depth <= 0) {
                throw new IllegalArgumentException("callParseDepth must be > 0, got: " + depth);
            }
            CALL_PARSE_DEPTH.set(depth);
        } else {
            CALL_PARSE_DEPTH.remove();
        }
    }

    /**
     * 获取当前线程的解析深度覆盖值（未设置返回 null，框架内部使用）。
     *
     * @return 覆盖值，未设置返回 null
     * @since 1.2.3
     */
    public static Integer getCallParseDepthOverride() {
        return CALL_PARSE_DEPTH.get();
    }

    /**
     * 解析当前生效的递归解析深度（优先线程级覆盖，P0-3）。
     *
     * @return 生效的最大解析深度
     * @since 1.2.3
     */
    static int resolveMaxParseDepth() {
        Integer callDepth = CALL_PARSE_DEPTH.get();
        return callDepth != null ? callDepth : maxParseDepth;
    }

    private JsonParserUtil() {
        throw new UnsupportedOperationException("JsonParserUtil is a utility class");
    }

    /**
     * 清理所有 ThreadLocal 变量（防止线程池环境内存泄漏）。
     */
    public static void clearThreadLocals() {
        CHAR_BUFFER.remove();
        SB_POOL.remove();
        useBigDecimal.remove();
        CALL_PARSE_DEPTH.remove();
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
            if (startPos >= len) {
                return new HashMap<>(8);
            }
            throw new JsonDeserializationException("Invalid JSON object: expected '{' at position " + startPos, startPos);
        }

        // 委托给 parseObjectRecursiveImpl 统一实现（参数化初始容量 64）
        Object result = parseObjectRecursiveImpl(chars, startPos, 64, 1);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        return map;
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

        char[] chars = getCharBuffer(json);
        // 委托给 parseArrayRecursiveImpl 统一实现，消除重复代码
        @SuppressWarnings("unchecked")
        List<Object> result = (List<Object>) parseArrayRecursiveImpl(chars, 0, 1);
        return result;
    }

    /**
     * 解析 JSON 值（优化版 - 关键路径内联）
     */
    /**
     * 解析 JSON 值（返回解析后的位置，消除调用方二次扫描）。
     * @param chars 字符数组
     * @param pos 起始位置
     * @param endPos [out] 解析结束位置（值的下一个字符位置）
     */
    private static final Object parseValueWithPos(char[] chars, int pos, int[] endPos, int depth) {
        // 快速路径：跳过空白（内联）
        pos = skipWhitespace(chars, pos);

        if (pos >= chars.length) {
            endPos[0] = pos;
            return null;
        }

        char c = chars[pos];

        switch (c) {
            case '"':
                return parseStringFastWithPos(chars, pos, endPos);
            case '{':
                return parseObjectRecursiveWithPos(chars, pos, endPos, depth + 1);
            case '[':
                return parseArrayRecursiveWithPos(chars, pos, endPos, depth + 1);
            case 't':
                if (pos + 3 < chars.length && chars[pos + 1] == 'r'
                        && chars[pos + 2] == 'u' && chars[pos + 3] == 'e') {
                    endPos[0] = pos + 4;
                    return Boolean.TRUE;
                }
                throw new JsonDeserializationException("Unexpected token starting with 't' at position " + pos, pos);
            case 'f':
                if (pos + 4 < chars.length && chars[pos + 1] == 'a'
                        && chars[pos + 2] == 'l' && chars[pos + 3] == 's'
                        && chars[pos + 4] == 'e') {
                    endPos[0] = pos + 5;
                    return Boolean.FALSE;
                }
                throw new JsonDeserializationException("Unexpected token starting with 'f' at position " + pos, pos);
            case 'n':
                if (pos + 3 < chars.length && chars[pos + 1] == 'u'
                        && chars[pos + 2] == 'l' && chars[pos + 3] == 'l') {
                    endPos[0] = pos + 4;
                    return null;
                }
                throw new JsonDeserializationException("Unexpected token starting with 'n' at position " + pos, pos);
            case '-':
            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                return parseNumberFastWithPos(chars, pos, endPos);
            default:
                throw new JsonDeserializationException("Unexpected character at position " + pos + ": " + c, pos);
        }
    }

    /** 兼容旧调用方（委托给 withPos 版本） */
    private static final Object parseValue(char[] chars, int pos) {
        int[] endPos = new int[1];
        return parseValueWithPos(chars, pos, endPos, 1);
    }

    /**
     * 快速解析字符串（返回值和结束位置）
     */
    private static String parseStringFastWithPos(char[] chars, int pos, int[] endPos) {
        int len = chars.length;
        pos++; // 跳过起始引号

        int start = pos;
        boolean hasEscape = false;

        while (pos < len) {
            char c = chars[pos];
            if (c == '"') {
                endPos[0] = pos + 1; // 结束引号后
                if (!hasEscape) {
                    return new String(chars, start, pos - start);
                } else {
                    return parseStringWithEscape(chars, start, pos);
                }
            } else if (c == '\\') {
                hasEscape = true;
                pos++;
            }
            pos++;
        }

        throw new JsonDeserializationException("Unterminated string", pos);
    }

    /** 兼容旧调用方 */
    private static String parseStringFast(char[] chars, int pos) {
        int[] endPos = new int[1];
        return parseStringFastWithPos(chars, pos, endPos);
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
                    case 'n': sb.append('\\n'); break;
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
        useBigDecimal.set(enabled);
    }

    /**
     * 查询当前线程是否使用 BigDecimal 解析浮点数。
     */
    public static boolean isUseBigDecimal() {
        return useBigDecimal.get();
    }

    private static Number parseNumberFastWithPos(char[] chars, int pos, int[] endPos) {
        Number result = parseNumberFast(chars, pos, endPos);
        return result;
    }

    private static Number parseNumberFast(char[] chars, int pos) {
        return parseNumberFast(chars, pos, new int[1]);
    }

    /**
     * 快速解析数字（内联优化，可返回解析终点）
     *
     * <p>溢出处理策略：</p>
     * <ul>
     *   <li>整数部分超过 long 范围时标记 overflow，不再累加</li>
     *   <li>溢出后统一回退到字符串解析（BigDecimal/Double）</li>
     *   <li>特例：-9223372036854775808（Long.MIN_VALUE）虽超出 long 正数范围，
     *       但其负值可表示，直接返回 Long.MIN_VALUE</li>
     * </ul>
     */
    private static Number parseNumberFast(char[] chars, int pos, int[] endPos) {
        int len = chars.length;
        int startPos = pos;

        boolean negative = false;
        if (pos < len && chars[pos] == '-') {
            negative = true;
            pos++;
        }

        long intValue = 0;
        boolean overflow = false;
        while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {
            int digit = chars[pos] - '0';
            if (!overflow && intValue > (Long.MAX_VALUE - digit) / 10) {
                // 检测 long 溢出（19+ 位整数），标记后停止累加
                overflow = true;
            }
            if (!overflow) {
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

        // 溢出路径：统一使用字符串解析，避免 intValue 不准确
        if (overflow) {
            endPos[0] = pos;
            String numStr = new String(chars, startPos, pos - startPos);
            // 特例：Long.MIN_VALUE 的绝对值超出 long 正数范围，但负值可表示
            if (negative && numStr.equals("-9223372036854775808")) {
                return Long.MIN_VALUE;
            }
            if (useBigDecimal.get()) {
                return new BigDecimal(numStr);
            }
            return Double.parseDouble(numStr);
        }

        if (decimalDigits > 0 || exp != 0) {
            // BigDecimal 路径：金融场景精度保护
            if (useBigDecimal.get()) {
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
                endPos[0] = pos;
                return bd;
            }
            // 精度保护：intValue 超过 2^53 或 decimalDigits 超过 22 位时
            // double 无法精确表示，回退到 Double.parseDouble 避免精度丢失
            if (intValue > 9007199254740992L || decimalDigits > 22) {
                String numStr = new String(chars, startPos, pos - startPos);
                endPos[0] = pos;
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
                if (exp < POW10.length) {
                    value = expNegative ? value / POW10[exp] : value * POW10[exp];
                } else {
                    // 指数超出查表范围，回退字符串解析，避免 POW10 数组越界并保证正确性
                    String numStr = new String(chars, startPos, pos - startPos);
                    return Double.parseDouble(numStr);
                }
            }
            endPos[0] = pos;
            return Double.valueOf(value);
        } else {
            endPos[0] = pos;
            // 行业惯例（Jackson/Fastjson2）：int 范围内返回 Integer，超出返回 Long
            long result = negative ? -intValue : intValue;
            if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
                return (int) result;
            }
            return result;
        }
    }

    private static Object parseObjectRecursiveWithPos(char[] chars, int start, int[] endPos, int depth) {
        Object result = parseObjectRecursiveImpl(chars, start, 64, depth + 1);
        endPos[0] = getValueEndPosition(chars, start); // 对象/数组仍需一次扫描定位 `}`
        return result;
    }

    private static Object parseArrayRecursive(char[] chars, int start) {
        return parseArrayRecursiveImpl(chars, start, 1);
    }

    private static Object parseArrayRecursiveWithPos(char[] chars, int start, int[] endPos, int depth) {
        Object result = parseArrayRecursiveImpl(chars, start, depth + 1);
        endPos[0] = getValueEndPosition(chars, start);
        return result;
    }

    private static Object parseObjectRecursiveImpl(char[] chars, int start, int initialCapacity, int depth) {
        if (depth > resolveMaxParseDepth()) {
            throw new JsonDeserializationException("JSON nesting depth exceeds limit: " + depth, start);
        }
        int len = chars.length;
        Map<String, Object> result = new LinkedHashMap<>(initialCapacity);
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

            // 解析值（返回解析终点，消除 getValueEndFast 二次扫描）
            int[] endPos = new int[1];
            Object value = parseValueWithPos(chars, pos, endPos, depth + 1);
            result.put(fieldName, value);
            pos = endPos[0];
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
    private static Object parseArrayRecursiveImpl(char[] chars, int start, int depth) {
        if (depth > resolveMaxParseDepth()) {
            throw new JsonDeserializationException("JSON nesting depth exceeds limit: " + depth, start);
        }
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

            // 解析值（返回解析终点，消除 getValueEndFast 二次扫描）
            int[] endPos = new int[1];
            Object value = parseValueWithPos(chars, pos, endPos, depth + 1);
            result.add(value);
            pos = endPos[0];
        }

        return result;
    }

    /**
     * 检查字符数组片段是否包含转义字符，若包含则解码转义序列，否则直接返回子串。
     *
     * <p>快速路径：先扫描是否有 '\' 字符，若无则直接 new String(chars, start, len)。</p>\n     * <p>慢速路径：复用 parseStringWithEscape 逻辑解码转义序列。</p>\n     *\n     * @param chars 字符数组\n     * @param start 起始位置\n     * @param length 长度\n     * @return 解码后的字符串\n     */\n    private static String decodeStringIfNeeded(char[] chars, int start, int length) {\n        for (int i = start; i < start + length; i++) {\n            if (chars[i] == '\\') {\n                return parseStringWithEscape(chars, start, start + length);\n            }\n        }\n        return new String(chars, start, length);\n    }\n\n    /**\n     * 解析字符串\n     */\n    /** package-private */ static String parseString(char[] chars, int pos) {\n        int len = chars.length;\n\n        if (chars[pos] != '"') {
            throw new JsonDeserializationException("Expected '\"' at position " + pos, pos);
        }
        pos++; // 跳过起始引号

        StringBuilder sb = new StringBuilder(len - pos);

        while (pos < len) {
            char c = chars[pos];
            if (c == '"') {\n                // 结束引号\n                return sb.toString();\n            } else if (c == '\\') {\n                // 转义字符\n                pos++;\n                if (pos >= len) {\n                    throw new JsonDeserializationException("Unexpected end of string", pos);\n                }\n                char escaped = chars[pos];\n                switch (escaped) {\n                    case '"': sb.append('"'); break;\n                    case '\\': sb.append('\\'); break;\n                    case '/': sb.append('/'); break;\n                    case 'b': sb.append('\b'); break;\n                    case 'f': sb.append('\f'); break;\n                    case 'n': sb.append('\\n'); break;\n                    case 'r': sb.append('\r'); break;\n                    case 't': sb.append('\t'); break;\n                    case 'u':\n                        // Unicode 转义\n                        if (pos + 4 >= len) {\n                            throw new JsonDeserializationException("Invalid unicode escape at position " + pos, pos);\n                        }\n                        String hex = new String(chars, pos + 1, 4);\n                        sb.append((char) Integer.parseInt(hex, 16));\n                        pos += 4;\n                        break;\n                    default:\n                        sb.append(escaped);\n                }\n            } else {\n                sb.append(c);\n            }\n            pos++;\n        }\n\n        throw new JsonDeserializationException("Unterminated string", len - 1);\n    }\n\n    /**\n     * 解析数字\n     */\n    /** package-private */ static Number parseNumber(char[] chars, int pos) {\n        int len = chars.length;\n        int start = pos;\n\n        // 跳过负号\n        if (pos < len && chars[pos] == '-') {\n            pos++;\n        }\n\n        // 解析整数部分\n        while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {\n            pos++;\n        }\n\n        // 检查小数\n        boolean isDecimal = false;\n        if (pos < len && chars[pos] == '.') {\n            isDecimal = true;\n            pos++;\n            while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {\n                pos++;\n            }\n        }\n\n        // 检查指数\n        if (pos < len && (chars[pos] == 'e' || chars[pos] == 'E')) {\n            isDecimal = true;\n            pos++;\n            if (pos < len && (chars[pos] == '+' || chars[pos] == '-')) {\n                pos++;\n            }\n            while (pos < len && chars[pos] >= '0' && chars[pos] <= '9') {\n                pos++;\n            }\n        }\n\n        String numStr = new String(chars, start, pos - start);\n        if (isDecimal) {\n            return Double.parseDouble(numStr);\n        } else {\n            try {\n                return Long.parseLong(numStr);\n            } catch (NumberFormatException e) {\n                return Double.parseDouble(numStr);\n            }\n        }\n    }\n\n    /**\n     * 获取值的结束位置（快速版，消除简单值的二次扫描）\n     *\n     * <p>对于 true/false/null 值，直接根据值类型计算结束位置，\n     * 无需调用 getValueEndPosition 重新扫描。</p>\n     *\n     * @param chars JSON 字符数组\n     * @param valueStart 值的起始位置\n     * @param value 已解析的值\n     * @param len 字符数组长度\n     * @return 值的结束位置\n     */\n    private static int getValueEndFast(char[] chars, int valueStart, Object value, int len) {\n        // 快速路径：布尔值和 null 直接计算长度\n        if (value == Boolean.TRUE) {\n            return valueStart + 4; // "true"\n        }\n        if (value == Boolean.FALSE) {\n            return valueStart + 5; // "false"\n        }\n        if (value == null && valueStart + 4 <= len && chars[valueStart] == 'n') {\n            return valueStart + 4; // "null"\n        }\n        // 复杂值（String/Number/Map/List）：需要扫描确定结束位置\n        return getValueEndPosition(chars, valueStart);\n    }\n\n    /**\n     * 获取值的结束位置\n     */\n    private static int getValueEndPosition(char[] chars, int pos) {\n        int len = chars.length;\n\n        // 跳过空白\n        while (pos < len && chars[pos] <= ' ') {\n            pos++;\n        }\n\n        if (pos >= len) {\n            return pos;\n        }\n\n        char c = chars[pos];\n\n        if (c == '"') {
            // 字符串：找到结束引号
            pos++;
            while (pos < len) {
                if (chars[pos] == '\\' && pos + 1 < len) {
                    pos += 2; // 跳过转义字符
                } else if (chars[pos] == '"') {\n                    return pos + 1;\n                } else {\n                    pos++;\n                }\n            }\n        } else if (c == '{') {\n            // 对象：找到匹配的 }\n            return findEndPosition(chars, pos, '{', '}') + 1;\n        } else if (c == '[') {\n            // 数组：找到匹配的 ]\n            return findEndPosition(chars, pos, '[', ']') + 1;\n        } else {\n            // 基本类型：找到逗号、} 或 ]\n            while (pos < len) {\n                char ch = chars[pos];\n                if (ch == ',' || ch == '}' || ch == ']') {\n                    return pos;\n                }\n                pos++;\n            }\n        }\n\n        return pos;\n    }\n\n    /**\n     * 查找匹配的结束位置\n     */\n    private static int findEndPosition(char[] chars, int start, char openChar, char closeChar) {\n        int depth = 0;\n        int pos = start;\n        int len = chars.length;\n\n        while (pos < len) {\n            char c = chars[pos];\n            if (c == openChar) {\n                depth++;\n            } else if (c == closeChar) {\n                depth--;\n                if (depth == 0) {\n                    return pos;\n                }\n            } else if (c == '"') {
                // 跳过字符串
                pos++;
                while (pos < len && chars[pos] != '"') {\n                    if (chars[pos] == '\\' && pos + 1 < len) {\n                        pos += 2;\n                    } else {\n                        pos++;\n                    }\n                }\n            }\n            pos++;\n        }\n\n        throw new JsonDeserializationException("Unmatched bracket: " + openChar, pos);\n    }\n\n    /**\n     * 获取字符数组缓冲区（JIT 优化：final 方法）\n     */\n    private static final char[] getCharBuffer(String json) {\n        char[] buffer = CHAR_BUFFER.get();\n        if (buffer.length < json.length()) {\n            buffer = new char[json.length()];\n            CHAR_BUFFER.set(buffer);\n        }\n        json.getChars(0, json.length(), buffer, 0);\n        return buffer;\n    }\n\n    /**\n     * 快速跳过空白字符（向量化优化）\n     */\n    private static final int skipWhitespace(char[] chars, int pos) {\n        int len = chars.length;\n\n        // 向量化处理：一次检查 8 个字符\n        while (pos + 7 < len) {\n            boolean allWhitespace = true;\n            for (int i = 0; i < 8; i++) {\n                if (chars[pos + i] > ' ') {\n                    allWhitespace = false;\n                    pos += i;\n                    break;\n                }\n            }\n            if (!allWhitespace) {\n                break;\n            }\n            pos += 8;\n        }\n\n        // 处理剩余字符\n        while (pos < len && chars[pos] <= ' ') {\n            pos++;\n        }\n\n        return pos;\n    }\n\n    // ==================== ASM 反序列化器专用快速解析方法 ====================\n\n    /**\n     * 单遍扫描构建字段位置映射（优化 O(N*M) 为 O(N)）。\n     *\n     * <p>当 ASM 反序列化器需要解析多个字段时，传统方式对每个字段调用\n     * {@link #findFieldPosition} 导致 O(N*M) 复杂度。此方法单遍扫描 JSON，\n     * 一次性提取所有顶层字段名及其值起始位置，将复杂度降为 O(N+M)。</p>\n     *\n     * @param json JSON 字符串\n     * @return 字段名 -> 值起始位置（冒号后第一个非空白字符）的映射\n     * @since 1.0.0\n     */\n    public static Map<String, Integer> buildFieldPositionMap(String json) {\n        Map<String, Integer> fieldPositions = new HashMap<>(16);\n        int len = json.length();\n        int i = 0;\n        // 跳过前导空白\n        while (i < len && json.charAt(i) <= ' ') i++;\n        if (i >= len || json.charAt(i) != '{') return fieldPositions;\n        i++; // 跳过 '{'\n\n        while (i < len) {\n            // 跳过空白\n            while (i < len && json.charAt(i) <= ' ') i++;\n            if (i >= len) break;\n            if (json.charAt(i) == '}') break;\n            if (json.charAt(i) == ',') { i++; continue; }\n\n            // 读取字段名（带引号）\n            if (json.charAt(i) != '"') break;
            i++; // 跳过起始引号
            int nameStart = i;
            while (i < len && json.charAt(i) != '"') {\n                if (json.charAt(i) == '\\') i++;\n                i++;\n            }\n            String fieldName = json.substring(nameStart, i);\n            i++; // 跳过结束引号\n\n            // 跳过冒号和空白\n            while (i < len && json.charAt(i) != ':') i++;\n            i++; // 跳过冒号\n            while (i < len && json.charAt(i) <= ' ') i++;\n\n            // 记录值起始位置\n            fieldPositions.put(fieldName, i);\n\n            // 跳过值（根据类型）\n            i = skipValue(json, i);\n        }\n        return fieldPositions;\n    }\n\n    /**\n     * 跳过 JSON 值，返回值结束后的下一个位置。\n     */\n    private static int skipValue(String json, int start) {\n        int len = json.length();\n        if (start >= len) return start;\n        char c = json.charAt(start);\n        if (c == '"') {
            // 字符串值
            int i = start + 1;
            while (i < len) {
                if (json.charAt(i) == '\\') { i += 2; continue; }
                if (json.charAt(i) == '"') return i + 1;\n                i++;\n            }\n            return i;\n        } else if (c == '{' || c == '[') {\n            // 嵌套对象/数组：计算深度\n            int depth = 0;\n            boolean inString = false;\n            boolean escaped = false;\n            for (int i = start; i < len; i++) {\n                char ch = json.charAt(i);\n                if (inString) {\n                    if (escaped) { escaped = false; }\n                    else if (ch == '\\') { escaped = true; }\n                    else if (ch == '"') { inString = false; }
                } else {
                    if (ch == '"') { inString = true; }\n                    else if (ch == '{' || ch == '[') { depth++; }\n                    else if (ch == '}' || ch == ']') { depth--; if (depth == 0) return i + 1; }\n                }\n            }\n            return len;\n        } else {\n            // 基本类型（number/boolean/null）\n            int i = start;\n            while (i < len) {\n                char ch = json.charAt(i);\n                if (ch == ',' || ch == '}' || ch == ']' || ch <= ' ') return i;\n                i++;\n            }\n            return i;\n        }\n    }\n    /**\n     * 在 JSON 中查找字段名的位置（跳过字符串值内部的文本）。\n     *\n     * <p>使用此方法替代 {@code json.indexOf(fieldJson)}，\n     * 避免 JSON 字符串值中包含类似字段名格式的文本时误匹配。</p>\n     *\n     * @param json JSON 字符串\n     * @param fieldJson 字段 JSON 片段（如 {@code "name":}）\n     * @return 字段位置，未找到返回 -1\n     */\n    static int findFieldPosition(String json, String fieldJson) {\n        int len = json.length();\n        int fieldLen = fieldJson.length();\n        boolean inString = false;\n        boolean escaped = false;\n        for (int i = 0; i <= len - fieldLen; i++) {\n            char c = json.charAt(i);\n            if (inString) {\n                if (escaped) {\n                    escaped = false;\n                } else if (c == '\\') {\n                    escaped = true;\n                } else if (c == '"') {
                    inString = false;
                }
            } else {
                // 先检查是否匹配字段模式（fieldJson 以 '"' 开头），\n                // 再判断是否进入字符串值\n                if (c == fieldJson.charAt(0) && json.regionMatches(i, fieldJson, 0, fieldLen)) {\n                    return i;\n                } else if (c == '"') {
                    inString = true;
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
        return YdszJson.fromJson(
            YdszJson.toJson(map), clazz);
    }
}
