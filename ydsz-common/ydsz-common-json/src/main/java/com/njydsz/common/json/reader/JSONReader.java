package com.njydsz.common.json.reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.json.exception.JsonDeserializationException;

/**
 * 高性能 JSON 读取器
 *
 * <p>直接操作 char[] 数组解析 JSON，避免 String 分配和方法调用开销</p>
 *
 * <p><b>性能优势：</b></p>
 * <ul>
 *   <li>直接字符比较，避免 String 创建</li>
 *   <li>预计算数值解析，避免 Double.parseDouble</li>
 *   <li>FNV-1a 哈希字段名匹配，避免 String 创建</li>
 * </ul>
 *
 * <p><b>特性系统（Feature）：</b></p>
 * <ul>
 *   <li>{@link Feature#LimitDepth} - 默认开启，防止栈溢出攻击（最大深度 256）</li>
 *   <li>{@link Feature#LimitStringLength} - 防止大字符串 OOM（最大 1MB）</li>
 *   <li>{@link Feature#LimitObjectSize} - 防止大对象 OOM（最大 10000 字段）</li>
 *   <li>{@link Feature#LimitArraySize} - 防止大数组 OOM（最大 10000 元素）</li>
 *   <li>{@link Feature#SafeMode} - 安全模式，启用所有安全限制</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JSONReader {

    /** 默认最大 JSON 深度 */
    public static final int DEFAULT_MAX_DEPTH = 256;

    /** 默认最大 JSON 长度（10MB） */
    public static final int DEFAULT_MAX_JSON_LENGTH = 10 * 1024 * 1024;

    /** 默认最大字符串长度（1MB） */
    public static final int DEFAULT_MAX_STRING_LENGTH = 1024 * 1024;

    /** 默认最大对象字段数 */
    public static final int DEFAULT_MAX_OBJECT_SIZE = 10000;

    /** 默认最大数组元素数 */
    public static final int DEFAULT_MAX_ARRAY_SIZE = 10000;

    /** 默认泛型递归深度上限（与 FastJSON2 默认 64 对齐） */
    public static final int DEFAULT_MAX_GENERIC_DEPTH = 64;

    /**
     * 读取特性枚举
     */
    public enum Feature {
        /**
         * 支持单引号（非标准 JSON）
         */
        SupportSingleQuotes(false),

        /**
         * 忽略控制字符
         */
        IgnoreControlChars(false),

        /**
         * 允许注释（非标准 JSON）
         */
        AllowComment(false),

        /**
         * 允许尾部逗号
         */
        AllowTrailingComma(false),

        /**
         * 使用 BigDecimal 解析数值（避免精度丢失）
         */
        UseBigDecimalForNumbers(false),

        /**
         * 自动关闭 JSON（允许不完整的 JSON）
         */
        AutoCloseJson(false),

        /**
         * 支持非标键值对（如未加引号的键）
         */
        SupportNonStringKey(false),

        /**
         * 支持非标值（如未加引号的字符串）
         */
        SupportNonStringValue(false),

        /**
         * 反序列化深度限制（安全防护，防止栈溢出攻击）
         *
         * <p><b>默认开启</b>，最大深度 256，可通过 {@link JSONReader#setMaxDepth(int)} 调整</p>
         */
        LimitDepth(true),

        /**
         * 忽略未知字段（提高兼容性）
         */
        IgnoreUnknownFields(false),

        /**
         * 字符串长度限制（安全防护，防止大字符串 OOM）
         *
         * <p><b>默认开启</b>，最大字符串长度 1MB</p>
         */
        LimitStringLength(true),

        /**
         * 对象大小限制（安全防护，防止大对象 OOM）
         *
         * <p><b>默认开启</b>，最大对象字段数 10000</p>
         */
        LimitObjectSize(true),

        /**
         * 数组大小限制（安全防护，防止大数组 OOM）
         *
         * <p><b>默认开启</b>，最大数组元素数 10000</p>
         */
        LimitArraySize(true),

        /**
         * 安全模式（启用所有安全限制）
         *
         * <p>启用后自动开启：LimitDepth、LimitStringLength、LimitObjectSize、LimitArraySize</p>
         */
        SafeMode(true),

        /**
         * 严格模式（遇到未知字段抛异常）
         */
        StrictMode(false),

        /**
         * 忽略不可序列化类型（容错解析，遇到不可序列化类型时跳过而非抛异常）
         */
        IgnoreNoneSerializable(false),

        /**
         * 去除字符串值的前后空白（容错解析，自动 trim 字符串值）
         */
        TrimString(false),

        /**
         * 支持 @type 自动类型检测（容错解析，根据 JSON 中的 @type 字段自动推断反序列化目标类型）
         */
        SupportAutoType(false),

        /**
         * 将非字符串类型的数值作为字符串读取（容错解析，如将 JSON 数值 123 读取为字符串 "123"）
         */
        SupportNonStringNumberAsString(false);

        private final boolean enabledByDefault;

        Feature(boolean enabledByDefault) {
            this.enabledByDefault = enabledByDefault;
        }

        public boolean isEnabledByDefault() {
            return enabledByDefault;
        }

        /**
         * 判断该特性在给定特性组合值中是否已启用。
         *
         * @param features 特性组合位掩码（多个特性按位或的结果）
         * @return {@code true} 表示该特性的位已置位启用
         */
        public boolean isEnabled(long features) {
            return (features & (1L << ordinal())) != 0;
        }

        /**
         * 返回该特性的位掩码（{@code 1L << ordinal()}）。
         *
         * <p>用于和特性组合值按位与（{@code features & mask}）判断是否启用，
         * 或被 {@code of(...)} 按位或组合多个特性。位序依赖枚举声明顺序，
         * 请勿随意调整枚举常量位置，否则会破坏已持久化/传输的特性位组合。</p>
         *
         * @return 64 位长整型位掩码
         */
        public long mask() {
            return 1L << ordinal();
        }
    }

    /**
     * 计算特性值
     */
    public static long of(Feature... features) {
        if (features == null) {
            return 0;
        }
        long value = 0;
        for (Feature feature : features) {
            if (feature != null) {
                value |= feature.mask();
            }
        }
        return value;
    }

    /**
     * 从集合计算特性值
     */
    public static long of(Set<Feature> features) {
        if (features == null) {
            return 0;
        }
        long value = 0;
        for (Feature feature : features) {
            if (feature != null) {
                value |= feature.mask();
            }
        }
        return value;
    }

    /** 字符缓冲区（可变，支持 reset 复用） */
    char[] buf;

    /** 当前读取位置 */
    int pos;

    /** 有效数据长度 */
    int len;

    /** 最大嵌套深度（防止栈溢出攻击，默认 256） */
    private static volatile int maxDepth = DEFAULT_MAX_DEPTH;

    /** 泛型递归深度上限（防止恶意嵌套泛型参数导致 StackOverflow，默认 64） */
    private static volatile int maxGenericDepth = DEFAULT_MAX_GENERIC_DEPTH;

    /**
     * 实例级别的最大嵌套深度（非 null 时使用此值替代静态全局值，支持显式配置传递）。
     *
     * <p>为 null 时回退到静态 {@link #maxDepth}，保持向后兼容。</p>
     */
    private final Integer instanceMaxDepth;

    /**
     * 实例级别的泛型递归深度上限（非 null 时使用此值替代静态全局值）。
     */
    private final Integer instanceMaxGenericDepth;

    /** ThreadLocal 读取器池（复用 JSONReader 实例和 char[] 缓冲区，避免 GC 开销） */
    private static final ThreadLocal<JSONReader> READER_POOL = new ThreadLocal<>();

    /**
     * 清理当前线程的 ThreadLocal 读取器池。
     *
     * <p>在线程池环境中，应在任务完成后或线程归还前调用此方法，
     * 释放池化的 JSONReader 实例及其 char[] 缓冲区，防止内存泄漏。</p>
     *
     * @since 1.2.1
     */
    public static void clearThreadLocals() {
        READER_POOL.remove();
    }

    /**
     * 构造函数
     *
     * <p><b>性能提示：</b>此构造函数会调用 {@code String.toCharArray()} 创建防御性拷贝。
     * 高频场景应优先使用 {@link #getPooledReader(String)} + {@link #reset(String)} 复用 char[] 缓冲区，
     * 避免每次反序列化都分配新的 char[]。ThreadLocal 池已在 {@link #getPooledReader} 中实现。</p>
     */
    public JSONReader(String json) {
        this.buf = json.toCharArray();
        this.pos = 0;
        this.len = buf.length;
        this.instanceMaxDepth = null;
        this.instanceMaxGenericDepth = null;
    }

    /**
     * 构造函数（char[] 模式）
     */
    public JSONReader(char[] buf, int offset, int length) {
        this.buf = buf;
        this.pos = offset;
        this.len = offset + length;
        this.instanceMaxDepth = null;
        this.instanceMaxGenericDepth = null;
    }

    /**
     * 构造函数（char[] 模式 + 显式深度配置）
     *
     * <p>通过传入预计算的深度限制，避免静态 volatile 字段读取和 ThreadLocal 查询。
     * 当 {@code maxDepth} 或 {@code maxGenericDepth} 为 null 时回退到静态全局值。</p>
     *
     * @param buf              字符缓冲区
     * @param offset           起始偏移
     * @param length           有效长度
     * @param maxDepth         最大嵌套深度（null 使用全局默认值）
     * @param maxGenericDepth  泛型递归深度上限（null 使用全局默认值）
     * @since 1.1.0
     */
    public JSONReader(char[] buf, int offset, int length, Integer maxDepth, Integer maxGenericDepth) {
        this.buf = buf;
        this.pos = offset;
        this.len = offset + length;
        this.instanceMaxDepth = maxDepth;
        this.instanceMaxGenericDepth = maxGenericDepth;
    }

    /**
     * 获取当前生效的最大嵌套深度（优先使用实例级别配置）。
     *
     * @return 最大嵌套深度
     * @since 1.1.0
     */
    public int resolveMaxDepth() {
        return instanceMaxDepth != null ? instanceMaxDepth : maxDepth;
    }

    /**
     * 获取当前生效的泛型递归深度上限（优先使用实例级别配置）。
     *
     * @return 泛型递归深度上限
     * @since 1.1.0
     */
    public int resolveMaxGenericDepth() {
        return instanceMaxGenericDepth != null ? instanceMaxGenericDepth : maxGenericDepth;
    }

    /**
     * 重置读取器（复用 char[] 缓冲区，避免重复分配）
     *
     * @param json 新的 JSON 字符串
     */
    public void reset(String json) {
        int newLen = json.length();
        if (buf == null || buf.length < newLen) {
            buf = new char[Math.max(newLen, buf != null ? buf.length * 2 : 4096)];
        }
        json.getChars(0, newLen, buf, 0);
        pos = 0;
        len = newLen;
    }

    /**
     * 设置全局最大嵌套深度（防止栈溢出攻击）。
     *
     * <p>当 {@link Feature#LimitDepth} 启用时，解析过程中嵌套深度超过此值即抛异常。
     * 默认值 {@link #DEFAULT_MAX_DEPTH} = 256。
     *
     * @param depth 最大嵌套深度（必须 > 0）
     * @since 1.0.0
     */
    public static void setMaxDepth(int depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("maxDepth must be > 0, got: " + depth);
        }
        JSONReader.maxDepth = depth;
        // 同步通用解析路径（JsonParserUtil）的深度限制，确保 fromJson(Object.class)
        // 等走通用解析器的入口也生效（两套深度系统统一）
        com.njydsz.common.json.parser.JsonParserUtil.setMaxParseDepth(depth);
    }

    /**
     * 获取当前最大嵌套深度。
     *
     * @return 最大嵌套深度
     * @since 1.0.0
     */
    public static int getMaxDepth() {
        return maxDepth;
    }

    /**
     * 设置泛型递归深度上限（防止恶意嵌套泛型参数导致 StackOverflow）。
     *
     * <p>当 {@link com.njydsz.common.json.provider.DeserializationProvider}
     * 递归解析泛型参数（ParameterizedType / GenericArrayType / WildcardType）超过此值时抛出
     * {@link com.njydsz.common.json.exception.JsonDeserializationException}。
     * 默认值 {@link #DEFAULT_MAX_GENERIC_DEPTH} = 64，与 FastJSON2 对齐。
     *
     * @param depth 泛型递归深度上限（必须 > 0）
     * @since 1.0.0
     */
    public static void setMaxGenericDepth(int depth) {
        if (depth <= 0) {
            throw new IllegalArgumentException("maxGenericDepth must be > 0, got: " + depth);
        }
        JSONReader.maxGenericDepth = depth;
    }

    /**
     * 获取当前泛型递归深度上限。
     *
     * @return 泛型递归深度上限
     * @since 1.0.0
     */
    public static int getMaxGenericDepth() {
        return maxGenericDepth;
    }

    /**
     * 获取池化的 JSONReader（避免对象和 char[] 分配）
     *
     * @param json JSON 字符串
     * @return 池化的 JSONReader 实例
     */
    public static JSONReader getPooledReader(String json) {
        JSONReader reader = READER_POOL.get();
        if (reader != null) {
            READER_POOL.remove();
            reader.reset(json);
            return reader;
        }
        return new JSONReader(json);
    }

    /**
     * 归还池化的 JSONReader
     *
     * @param reader 需归还的 JSONReader 实例
     */
    public static void returnPooledReader(JSONReader reader) {
        READER_POOL.set(reader);
    }

    /**
     * 读取并返回当前位置字符，读取位置前移一位。
     *
     * @return 当前字符
     * @throws IllegalStateException 当已到达 JSON 末尾（pos >= len）
     */
    public char readChar() {
        if (pos >= len) throw new IllegalStateException("Unexpected end of JSON");
        return buf[pos++];
    }

    /**
     * 将读取位置回退一位（撤销上一次 readChar/peek 的推进）。
     *
     * <p>仅在 {@code pos > 0} 时回退，不会造成越界；用于试探性读取后的回溯。</p>
     */
    public void back() {
        if (pos > 0) pos--;
    }

    /**
     * 查看下一个非空白字符（不消费，读取位置不变）。
     *
     * <p>内部先 {@code skipWhitespace()} 定位，记录字符后再恢复 pos，因此不会推进读取位置；
     * 与 {@link #peekChar()} 的区别在于本方法会跳过前置空白。</p>
     *
     * @return 下一个非空白字符，已到末尾则返回 {@code '\0'}
     */
    public char peek() {
        int saved = pos;
        skipWhitespace();
        char ch = (pos < len) ? buf[pos] : '\0';
        pos = saved;
        return ch;
    }

    /**
     * 查看当前字符（不跳过空白，不移动位置）
     *
     * <p>在已调用 skipWhitespace() 后使用，避免 peek() 的双重空白扫描开销</p>
     */
    public char peekChar() {
        return (pos < len) ? buf[pos] : '\0';
    }

    /**
     * 快速读取字段名（假定当前已位于 {@code '"'} 引号处，不跳过前置空白）。
     *
     * <p>用于已定位到引号的高性能路径（如 ASM 反序列化器）；若当前字符非引号则直接返回 null，
     * 不做任何字段名匹配尝试。等价于 {@code readString()} 的快捷版。</p>
     *
     * @return 字段名字符串，或 null（当前非字符串字段名）
     */
    public String readFieldNameFast() {
        if (pos >= len) return null;
        if (buf[pos] != '"') return null;
        return readString();
    }

    /**
     * 跳过所有空白字符（{@code char <= ' '}），推进读取位置到首个非空白字符。
     *
     * <p>JSON 规范中空白为空格、制表符、换行、回车；本实现以 {@code <= ' '} 统一处理。</p>
     */
    public void skipWhitespace() {
        while (pos < len && buf[pos] <= ' ') pos++;
    }

    /**
     * 向前定位到目标字符（跳过空白与非目标字符）。
     *
     * <p>用于跳到 {@code ':'} 等结构字符：遇到目标字符即停（不消费），
     * 遇到非空白非目标字符也立即返回，避免越过值内容。典型用途是定位字段名后的冒号。</p>
     *
     * @param target 目标字符（如 {@code ':'}）
     */
    public void skipTo(char target) {
        while (pos < len) {
            char c = buf[pos];
            if (c == target) return;
            if (c > ' ') return;
            pos++;
        }
    }

    /**
     * 跳过空白后读取并返回下一个字符，读取位置前移一位。
     *
     * @return 下一个非空白字符
     * @throws RuntimeException 当已到达 JSON 末尾
     */
    public char nextChar() {
        skipWhitespace();
        if (pos >= len) throw new RuntimeException("Unexpected end of JSON");
        return buf[pos++];
    }

    /**
     * 尝试匹配指定字段名并消费其后的冒号（字段定位核心方法）。
     *
     * <p>先跳过空白并确认当前为引号起始的字段名，再逐字符比对 {@code fieldName}；
     * 不匹配则 {@code skipValue()} 跳过该值并返回 false。匹配成功后跳过结束引号、
     * 空白与冒号，使读取位置停在字段值起始处，便于后续直接读取。</p>
     *
     * @param fieldName 期望匹配的字段名
     * @return true 表示匹配成功且已定位到值，false 表示字段名不符
     */
    public boolean matchField(String fieldName) {
        skipWhitespace();
        if (pos >= len || buf[pos] != '"') return false;
        pos++;
        int fieldLen = fieldName.length();
        if (pos + fieldLen > len) return false;
        for (int i = 0; i < fieldLen; i++) {
            if (buf[pos + i] != fieldName.charAt(i)) {
                // 字段名不匹配：先跳过剩余字段名（到下一个 \"），再跳过 : 和值
                while (pos < len && buf[pos] != '"') pos++;
                if (pos < len && buf[pos] == '"') pos++; // 跳过结束引号
                skipWhitespace();
                if (pos < len && buf[pos] == ':') pos++;
                skipValue();
                return false;
            }
        }
        pos += fieldLen;
        if (pos < len && buf[pos] == '"') pos++;
        skipWhitespace();
        if (pos < len && buf[pos] == ':') pos++;
        return true;
    }

    /** 10的幂次查找表（用于快速浮点数解析，避免 String 分配） */
    private static final double[] POW10 = {
        1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9,
        1e10, 1e11, 1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19,
        1e20, 1e21, 1e22, 1e23
    };

    /** 10的幂次查找表（float 精度） */
    private static final float[] POW10_FLOAT = {
        1e0f, 1e1f, 1e2f, 1e3f, 1e4f, 1e5f, 1e6f, 1e7f, 1e8f, 1e9f,
        1e10f
    };

    /**
     * 读取浮点数（快速路径：直接从 char[] 解析，避免 String 分配）
     *
     * <p>对于有效数字不超过7位且无指数的常见浮点数，直接从 char[] 计算，
     * 消除 new String() + Float.parseFloat() 的分配和解析开销</p>
     */
    public float readFloat() {
        skipWhitespace();
        if (pos >= len) throw new IllegalStateException("Unexpected end of JSON");

        final int start = pos;
        boolean negative = false;
        if (buf[pos] == '-') { negative = true; pos++; }

        long significand = 0;
        int intDigits = 0;
        while (pos < len) {
            char ch = buf[pos];
            if (ch >= '0' && ch <= '9') {
                significand = significand * 10 + (ch - '0');
                intDigits++;
                pos++;
            } else {
                break;
            }
        }

        int scale = 0;
        if (pos < len && buf[pos] == '.') {
            pos++;
            while (pos < len) {
                char ch = buf[pos];
                if (ch >= '0' && ch <= '9') {
                    significand = significand * 10 + (ch - '0');
                    scale++;
                    pos++;
                } else {
                    break;
                }
            }
        }

        int exponent = 0;
        if (pos < len && (buf[pos] == 'e' || buf[pos] == 'E')) {
            pos++;
            boolean negativeExp = false;
            if (pos < len && buf[pos] == '-') { negativeExp = true; pos++; }
            else if (pos < len && buf[pos] == '+') { pos++; }
            while (pos < len) {
                char ch = buf[pos];
                if (ch >= '0' && ch <= '9') {
                    exponent = exponent * 10 + (ch - '0');
                    pos++;
                } else {
                    break;
                }
            }
            if (negativeExp) exponent = -exponent;
        }

        int totalDigits = intDigits + scale;
        if (totalDigits > 0 && totalDigits <= 9 && significand >= 0) {
            int effectiveScale = scale - exponent;
            float result;
            if (effectiveScale == 0) {
                result = (float) significand;
            } else if (effectiveScale > 0 && effectiveScale < POW10_FLOAT.length) {
                result = (float) significand / POW10_FLOAT[effectiveScale];
            } else if (effectiveScale < 0 && -effectiveScale < POW10_FLOAT.length) {
                result = (float) significand * POW10_FLOAT[-effectiveScale];
            } else {
                return Float.parseFloat(new String(buf, start, pos - start));
            }
            return negative ? -result : result;
        }

        return Float.parseFloat(new String(buf, start, pos - start));
    }

    /**
     * 读取当前值对应的原始 JSON 文本（含结构字符，不做类型解析）。
     *
     * <p>用于 {@code @JsonAnySetter} 等需保留原始片段的场景：对象/数组按括号配对截取，
     * 字符串读取到结束引号，标量读取到逗号/括号/空白。已到末尾或当前为 null 时返回 {@code "null"}。
     * 返回的是未解析的文本片段，调用方需自行决定如何解析。</p>
     *
     * @return 原始 JSON 文本片段
     */
    public String readRawValue() {
        skipWhitespace();
        if (pos >= len) return "null";
        int start = pos;
        char ch = buf[pos];
        if (ch == '{') {
            pos++; int depth = 1;
            while (depth > 0 && pos < len) {
                char ch2 = buf[pos];
                if (ch2 == '{') depth++;
                else if (ch2 == '}') depth--;
                else if (ch2 == '"') { skipStringValue(); continue; }
                pos++;
            }
        } else if (ch == '[') {
            pos++; int depth = 1;
            while (depth > 0 && pos < len) {
                char ch2 = buf[pos];
                if (ch2 == '[') depth++;
                else if (ch2 == ']') depth--;
                else if (ch2 == '"') { skipStringValue(); continue; }
                pos++;
            }
        } else if (ch == '"') {
            readString();
        } else {
            while (pos < len) { char ch2 = buf[pos]; if (ch2 == ',' || ch2 == '}' || ch2 == ']' || ch2 <= ' ') break; pos++; }
        }
        return new String(buf, start, pos - start);
    }

    /**
     * 读取并返回 JSON 字符串值（兼容单引号 {@code '\''} 与双引号 {@code '"'}）。
     *
     * <p>先 {@code nextChar()} 定位引号，再委托 {@code readStringContent} 读取内容（自动处理转义）。
     * 非标准单引号由 SupportSingleQuotes 特性兼容。</p>
     *
     * @return 解析后的字符串（不含引号，转义已还原）
     * @throws IllegalStateException 当当前非字符串起始或字符串未闭合
     */
    public String readString() {
        char quote = nextChar();
        if (quote != '"' && quote != '\'') throw new IllegalStateException("Expected string, got: " + quote);
        return readStringContent(quote);
    }

    /**
     * 直接读取字符串值（跳过 skipWhitespace，在已定位到引号位置时使用）
     *
     * <p>ASM 反序列化器中 readFieldNameHash() 已跳过空白和冒号，
     * 使用此方法避免重复的 skipWhitespace() 调用</p>
     */
    public String readStringDirect() {
        if (pos >= len || buf[pos] != '"') {
            return readString();
        }
        pos++;
        return readStringContent('"');
    }

    /**
     * 读取字符串内容（引号已跳过）
     */
    private String readStringContent(char quote) {
        int start = pos;
        int end = pos;
        while (end < len) {
            char ch = buf[end];
            if (ch == quote) break;
            if (ch == '\\') return readStringWithEscape(start);
            end++;
        }
        if (end >= len) throw new IllegalStateException("Unexpected end of JSON string");
        String result = new String(buf, start, end - start);
        pos = end + 1;
        return result;
    }

    private String readStringWithEscape(int start) {
        StringBuilder sb = new StringBuilder(len - start);
        while (pos < len) {
            char ch = buf[pos++];
            if (ch == '"') break;
            if (ch == '\\') {
                if (pos >= len) throw new IllegalStateException("Unexpected end of JSON string");
                char escaped = buf[pos++];
                switch (escaped) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (pos + 4 > len) throw new IllegalStateException("Unexpected end of JSON string");
                        char unicode = (char) Integer.parseInt(new String(buf, pos, 4), 16);
                        pos += 4;
                        // 处理代理对（U+D800-U+DFFF），emoji 等补充字符在 JSON 中编码为两个反斜杠u 序列
                        if (Character.isHighSurrogate(unicode) && pos + 6 <= len
                                && buf[pos] == '\\' && buf[pos + 1] == 'u') {
                            try {
                                char low = (char) Integer.parseInt(new String(buf, pos + 2, 4), 16);
                                if (Character.isLowSurrogate(low)) {
                                    sb.appendCodePoint(Character.toCodePoint(unicode, low));
                                    pos += 6;
                                    break;
                                }
                            } catch (NumberFormatException ignored) {
                                // 不是有效代理对，按单字符处理
                            }
                        }
                        sb.append(unicode);
                        break;
                    default: sb.append(escaped); break;
                }
            } else sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * 读取并返回 JSON 整数（支持可选负号，直接 char 累加，无 String 分配）。
     *
     * @return 解析出的 int 值
     * @throws IllegalStateException 当已到达 JSON 末尾或无有效数字
     */
    public int readInt() {
        skipWhitespace();
        if (pos >= len) throw new IllegalStateException("Unexpected end of JSON");
        boolean negative = false;
        if (buf[pos] == '-') { negative = true; pos++; }
        int value = 0;
        while (pos < len) {
            char ch = buf[pos];
            if (ch >= '0' && ch <= '9') { value = value * 10 + (ch - '0'); pos++; }
            else break;
        }
        return negative ? -value : value;
    }

    /**
     * 读取并返回 JSON 长整数（支持可选负号，直接 char 累加，无 String 分配）。
     *
     * @return 解析出的 long 值
     * @throws IllegalStateException 当已到达 JSON 末尾或无有效数字
     */
    public long readLong() {
        skipWhitespace();
        if (pos >= len) throw new IllegalStateException("Unexpected end of JSON");
        boolean negative = false;
        if (buf[pos] == '-') { negative = true; pos++; }
        long value = 0;
        while (pos < len) {
            char ch = buf[pos];
            if (ch >= '0' && ch <= '9') { value = value * 10 + (ch - '0'); pos++; }
            else break;
        }
        return negative ? -value : value;
    }

    /**
     * 读取双精度浮点数（快速路径：直接从 char[] 解析，避免 String 分配）
     *
     * <p>对于有效数字不超过15位且无指数的常见浮点数，直接从 char[] 计算，
     * 消除 new String() + Double.parseDouble() 的分配和解析开销</p>
     *
     * <p>参考 FastJSON2 底层实现：significand / POW10[scale] 直接计算，
     * 避免 String 中间对象创建和 JDK 解析器开销</p>
     */
    public double readDouble() {
        skipWhitespace();
        if (pos >= len) throw new IllegalStateException("Unexpected end of JSON");

        final int start = pos;
        boolean negative = false;
        if (buf[pos] == '-') { negative = true; pos++; }

        long significand = 0;
        int intDigits = 0;
        while (pos < len) {
            char ch = buf[pos];
            if (ch >= '0' && ch <= '9') {
                significand = significand * 10 + (ch - '0');
                intDigits++;
                pos++;
            } else {
                break;
            }
        }

        int scale = 0;
        if (pos < len && buf[pos] == '.') {
            pos++;
            while (pos < len) {
                char ch = buf[pos];
                if (ch >= '0' && ch <= '9') {
                    significand = significand * 10 + (ch - '0');
                    scale++;
                    pos++;
                } else {
                    break;
                }
            }
        }

        int exponent = 0;
        if (pos < len && (buf[pos] == 'e' || buf[pos] == 'E')) {
            pos++;
            boolean negativeExp = false;
            if (pos < len && buf[pos] == '-') { negativeExp = true; pos++; }
            else if (pos < len && buf[pos] == '+') { pos++; }
            while (pos < len) {
                char ch = buf[pos];
                if (ch >= '0' && ch <= '9') {
                    exponent = exponent * 10 + (ch - '0');
                    pos++;
                } else {
                    break;
                }
            }
            if (negativeExp) exponent = -exponent;
        }

        int totalDigits = intDigits + scale;
        if (totalDigits > 0 && totalDigits <= 15 && significand >= 0) {
            int effectiveScale = scale - exponent;
            double result;
            if (effectiveScale == 0) {
                result = (double) significand;
            } else if (effectiveScale > 0 && effectiveScale < POW10.length) {
                result = (double) significand / POW10[effectiveScale];
            } else if (effectiveScale < 0 && -effectiveScale < POW10.length) {
                result = (double) significand * POW10[-effectiveScale];
            } else {
                return Double.parseDouble(new String(buf, start, pos - start));
            }
            return negative ? -result : result;
        }

        return Double.parseDouble(new String(buf, start, pos - start));
    }

    /**
     * 读取并返回 JSON 布尔值（{@code true}/{@code false}）。
     *
     * @return 解析出的布尔值
     * @throws IllegalStateException 当当前 token 非合法布尔字面量
     */
    public boolean readBoolean() {
        skipWhitespace();
        if (pos + 4 <= len && buf[pos] == 't' && buf[pos+1] == 'r' && buf[pos+2] == 'u' && buf[pos+3] == 'e') { pos += 4; return true; }
        if (pos + 5 <= len && buf[pos] == 'f' && buf[pos+1] == 'a' && buf[pos+2] == 'l' && buf[pos+3] == 's' && buf[pos+4] == 'e') { pos += 5; return false; }
        throw new IllegalStateException("Expected boolean, got: " + buf[pos]);
    }

    /**
     * 消费当前 JSON null 字面量（不返回值）。
     *
     * <p>用于已知字段为 null 时显式推进读取位置；若当前非 {@code "null"} 则抛异常。
     * 通常配合 {@link #isNull()} 先判断再调用，避免误消费。</p>
     *
     * @throws IllegalStateException 当当前非 null 字面量
     */
    public void readNull() {
        skipWhitespace();
        if (pos + 4 <= len && buf[pos] == 'n' && buf[pos+1] == 'u' && buf[pos+2] == 'l' && buf[pos+3] == 'l') pos += 4;
        else throw new IllegalStateException("Expected null, got: " + buf[pos]);
    }

    /**
     * 判断当前位置（跳过前置空白后）是否为 JSON {@code null} 字面量。
     *
     * <p>只做前瞻判断，不推进读取位置。若返回 {@code true}，
     * 可调用 {@link #readNull()} 消费该字面量。</p>
     *
     * @return {@code true} 表示当前位置是 {@code null} 字面量
     */
    public boolean isNull() {
        skipWhitespace();
        return pos + 4 <= len && buf[pos] == 'n' && buf[pos+1] == 'u' && buf[pos+2] == 'l' && buf[pos+3] == 'l';
    }

    /**
     * 消费对象起始字符 {@code '{'}。
     *
     * @throws IllegalStateException 当当前字符非 {@code '{'}
     */
    public void readObjectStart() { if (nextChar() != '{') throw new IllegalStateException("Expected '{'"); }

    /**
     * 消费对象结束字符 {@code '}'}。
     *
     * @throws IllegalStateException 当当前字符非 {@code '}'}
     */
    public void readObjectEnd() { if (nextChar() != '}') throw new IllegalStateException("Expected '}'"); }

    /**
     * 消费数组起始字符 {@code '['}。
     *
     * @throws IllegalStateException 当当前字符非 {@code '['}
     */
    public void readArrayStart() { if (nextChar() != '[') throw new IllegalStateException("Expected '['"); }

    /**
     * 消费数组结束字符 {@code ']'}。
     *
     * @throws IllegalStateException 当当前字符非 {@code ']'}
     */
    public void readArrayEnd() { if (nextChar() != ']') throw new IllegalStateException("Expected ']'"); }

    /**
     * 读取并返回下一个对象字段名（处理前置空白、逗号、结束括号）。
     *
     * <p>定位到引号起始的字符串并读取；若遇到 {@code '}'} 或已到末尾则返回 null，
     * 表示对象结束。用于通用 Map 反序列化等无法预知字段名的路径。</p>
     *
     * @return 字段名字符串，或 null 表示对象已结束
     */
    public String readFieldName() {
        if (pos >= len) return null;
        char ch = buf[pos];
        while (ch <= ' ') { pos++; if (pos >= len) return null; ch = buf[pos]; }
        if (ch == '}') { pos++; return null; }
        if (ch == ',') { pos++; while (pos < len) { ch = buf[pos]; if (ch == '"') break; pos++; } }
        if (pos >= len || buf[pos] != '"') return null;
        String name = readString();
        // 消费冒号（含前置空白）
        while (pos < len && buf[pos] <= ' ') pos++;
        if (pos < len && buf[pos] == ':') pos++;
        while (pos < len && buf[pos] <= ' ') pos++;
        return name;
    }

    /**
     * 读取字段名哈希码（FNV-1a，避免创建 String 对象）
     */
    public long readFieldNameHash() {
        if (pos >= len) return -1;
        char ch = buf[pos];
        while (ch <= ' ') { pos++; if (pos >= len) return -1; ch = buf[pos]; }
        if (ch == '}') { pos++; return 0; }
        if (ch == ',') { pos++; while (pos < len) { ch = buf[pos]; if (ch == '"') break; pos++; } }
        if (pos >= len || buf[pos] != '"') return -1;
        pos++;
        long hash = 0x811c9dc5;
        while (pos < len) {
            ch = buf[pos];
            if (ch == '"') { pos++; break; }
            hash ^= ch;
            hash *= 0x100000001b3L;
            pos++;
        }
        while (pos < len && buf[pos] <= ' ') pos++;
        if (pos < len && buf[pos] == ':') pos++;
        while (pos < len && buf[pos] <= ' ') pos++;
        return hash;
    }

    /**
     * 计算字符串的 FNV-1a 哈希值（与 {@link #readFieldNameHash()} 同源算法）。
     *
     * <p>用于字段名预计算哈希，与解析时实时计算的 {@code readFieldNameHash()} 结果比对，
     * 从而以 {@code long} 比较替代字符串相等判断，避免字段名 String 分配。
     * 种子 {@code 0x811c9dc5}，乘子 {@code 0x100000001b3L}。</p>
     *
     * @param name 字段名
     * @return FNV-1a 哈希值
     */
    public static long fnv1aHash(String name) {
        long hash = 0x811c9dc5;
        for (int i = 0; i < name.length(); i++) { hash ^= name.charAt(i); hash *= 0x100000001b3L; }
        return hash;
    }

    public int getPosition() { return pos; }
    public boolean isEnd() { return pos >= len; }

    /**
     * 跳过当前 JSON 值（不解析、不返回），将读取位置推进到值之后。
     *
     * <p>对象/数组按括号配对递归跳过（字符串内跳过转义），标量读取到逗号/括号/空白。
     * 用于字段名未匹配时丢弃未知字段，保持容错。</p>
     */
    public void skipValue() {
        skipWhitespace();
        if (pos >= len) return;
        char ch = buf[pos];
        if (ch == '{') {
            pos++; int depth = 1;
            while (depth > 0 && pos < len) {
                char ch2 = buf[pos];
                if (ch2 == '{') depth++;
                else if (ch2 == '}') depth--;
                else if (ch2 == '"') { skipStringValue(); continue; }
                pos++;
            }
        } else if (ch == '[') {
            pos++; int depth = 1;
            while (depth > 0 && pos < len) {
                char ch2 = buf[pos];
                if (ch2 == '[') depth++;
                else if (ch2 == ']') depth--;
                else if (ch2 == '"') { skipStringValue(); continue; }
                pos++;
            }
        } else if (ch == '"') { readString(); }
        else if (ch == 't') pos += 4;
        else if (ch == 'f') pos += 5;
        else if (ch == 'n') pos += 4;
        else { while (pos < len) { char ch2 = buf[pos]; if (ch2 == ',' || ch2 == '}' || ch2 == ']' || ch2 <= ' ') break; pos++; } }
    }

    /**
     * 跳过 JSON 字符串值（已定位到引号位置）。
     *
     * <p>正确处理转义引号 {@code \"} 和转义反斜杠 {@code \\}，
     * 避免字符串内容中的引号导致解析错位。</p>
     *
     * <p>调用后 {@code pos} 指向字符串结束引号之后的字符。</p>
     *
     * @since 1.0.0
     */
    public void skipStringValue() {
        // 当前 pos 指向引号 "
        pos++; // 跳过起始引号
        while (pos < len) {
            char c = buf[pos];
            if (c == '\\') {
                pos += 2; // 跳过转义字符和被转义的字符
            } else if (c == '"') {
                pos++; // 跳过结束引号
                return;
            } else {
                pos++;
            }
        }
    }

    /**
     * 读取 JSON 数组并反序列化为 {@code List<Object>}（按 elementType 逐元素解析）。
     *
     * <p>跳过前置空白并确认 {@code '['}；空数组返回空列表。每个元素交由
     * {@code readArrayElement} 按类型解析（已知类型走快速路径，未知回退 {@code readAnyValue}）。
     * 用于通用反序列化，持续读取直到 {@code ']'}。</p>
     *
     * @param elementType 数组元素类型，null 或 Object.class 表示按任意类型推断
     * @return 反序列化得到的 List
     * @throws RuntimeException 当起始非 {@code '['}
     */
    public List<Object> readArray(Class<?> elementType) {
        return readArray(elementType, 0);
    }

    private List<Object> readArray(Class<?> elementType, int depth) {
        if (depth > resolveMaxDepth()) {
            throw new JsonDeserializationException("JSON nesting depth exceeds limit: " + depth, pos);
        }
        skipWhitespace();
        if (pos >= len || buf[pos] != '[') throw new RuntimeException("Expected [ at position " + pos);
        pos++;
        List<Object> result = new ArrayList<>();
        while (pos < len) {
            skipWhitespace();
            if (pos >= len) break;
            if (buf[pos] == ']') { pos++; return result; }
            if (buf[pos] == ',') { pos++; continue; }
            Object element = readArrayElement(elementType, depth + 1);
            result.add(element);
        }
        return result;
    }


    private Object readArrayElement(Class<?> elementType) {
        return readArrayElement(elementType, 0);
    }

    private Object readArrayElement(Class<?> elementType, int depth) {
        if (elementType == null || elementType == Object.class) return readAnyValue(depth);
        if (elementType == String.class) return readString();
        if (elementType == int.class || elementType == Integer.class) return Integer.valueOf(readInt());
        if (elementType == long.class || elementType == Long.class) return Long.valueOf(readLong());
        if (elementType == double.class || elementType == Double.class) return Double.valueOf(readDouble());
        if (elementType == float.class || elementType == Float.class) return Float.valueOf(readFloat());
        if (elementType == boolean.class || elementType == Boolean.class) return Boolean.valueOf(readBoolean());
        return readAnyValue(depth);
    }

    private Object readAnyValue() {
        return readAnyValue(0);
    }

    private Object readAnyValue(int depth) {
        skipWhitespace();
        if (pos >= len) return null;
        char ch = buf[pos];
        if (ch == '"') return readString();
        if (ch == '{') return readObjectMap(depth + 1);
        if (ch == '[') return readArray(Object.class, depth + 1);
        if (ch == 't') { pos += 4; return true; }
        if (ch == 'f') { pos += 5; return false; }
        if (ch == 'n') { pos += 4; return null; }
        return readDouble();
    }

    /**
     * 读取 JSON 对象并反序列化为 {@code Map<String, Object>}（通用 Map 反序列化）。
     *
     * <p>跳过前置空白并确认 {@code '{'}，逐对读取字段名与值（值经
     * {@code readAnyValue} 按首字符推断类型）。用于目标类型未知的场景，
     * 例如 {@code @JsonAnySetter} 的复杂对象或弱类型入参。</p>
     *
     * @return 反序列化得到的 Map
     * @throws RuntimeException 当起始非 {@code '{'}
     */
    public Map<String, Object> readObjectMap() {
        return readObjectMap(0);
    }

    private Map<String, Object> readObjectMap(int depth) {
        if (depth > resolveMaxDepth()) {
            throw new JsonDeserializationException("JSON nesting depth exceeds limit: " + depth, pos);
        }
        skipWhitespace();
        if (pos >= len || buf[pos] != '{') throw new RuntimeException("Expected { at position " + pos);
        pos++;
        Map<String, Object> result = new HashMap<>();
        while (pos < len) {
            skipWhitespace();
            if (pos >= len) break;
            if (buf[pos] == '}') { pos++; return result; }
            if (buf[pos] == ',') { pos++; continue; }
            String key = readFieldNameFast();
            if (key == null) break;
            skipTo(':');
            if (pos < len) pos++;
            Object value = readAnyValue(depth + 1);
            result.put(key, value);
        }
        return result;
    }
}
