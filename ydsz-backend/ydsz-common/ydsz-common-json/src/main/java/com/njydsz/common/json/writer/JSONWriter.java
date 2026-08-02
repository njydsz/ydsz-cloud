package com.njydsz.common.json.writer;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.asm.AsmSerializer;
import com.njydsz.common.json.cache.AsmCodecCache;
import com.njydsz.common.json.number.NumberUtils;

import java.nio.charset.StandardCharsets;
/**
 * 高性能 JSON 写入器
 * 
 * <p>直接操作 char[] 数组，避免 StringBuilder 的方法调用开销</p>
 * 
 * <p><b>性能优势：</b></p>
 * <ul>
 *   <li>减少 50-70% 的方法调用</li>
 *   <li>避免边界检查和同步开销</li>
 *   <li>直接内存写入，无中间层</li>
 * </ul>
 * 
 * <p><b>Feature 系统：</b></p>
 * <p>通过 {@link Feature} 枚举控制序列化行为，参考 FastJSON2 和 Jackson 的 Feature 设计。
 * 使用 {@link #of(Feature...)} 或 {@link #of(Set)} 计算特性标志位。</p>
 * 
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JSONWriter {

    /**
     * 写入特性枚举
     *
     * <p>用于控制序列化行为，参考 FastJSON2 和 Jackson 的 Feature 设计。</p>
     */
    public enum Feature {
        /**
         * 输出 null 值字段
         */
        WriteNulls(false),

        /**
         * 格式化输出（Pretty Print）
         */
        PrettyPrint(false),

        /**
         * 使用 ISO-8601 日期格式
         */
        UseISO8601DateFormat(false),

        /**
         * 转义非 ASCII 字符
         */
        EscapeNonAscii(false),

        /**
         * 禁止循环引用检测（提升性能，但可能导致无限递归）
         */
        DisableCircularReferenceDetect(false),

        /**
         * 输出 Map 类型信息（用于反序列化时恢复类型）
         */
        WriteMapTypeName(false),

        /**
         * 使用单引号代替双引号
         */
        UseSingleQuotes(false),

        /**
         * 排序 Map Key
         */
        SortMapKeys(false),

        /**
         * 写入类名（用于多态支持）
         */
        WriteClassName(false),

        /**
         * 将 BigDecimal 作为字符串写入（避免精度丢失，输出如 "1.23" 而非 1.23）
         */
        WriteBigDecimalAsString(false),

        /**
         * 将布尔值作为数字写入（true 写为 1，false 写为 0）
         */
        WriteBooleanAsNumber(false),

        /**
         * 输出 Map 中值为 null 的字段（默认不输出 null 值字段）
         */
        WriteMapNullValue(false),

        /**
         * 将 null 字符串字段写为空字符串 ""（需配合 WriteMapNullValue 使用）
         */
        WriteNullStringAsEmpty(false),

        /**
         * 将 null 列表字段写为空数组 []（需配合 WriteMapNullValue 使用）
         */
        WriteNullListAsEmpty(false),

        /**
         * 将 null 数字字段写为 0（需配合 WriteMapNullValue 使用）
         */
        WriteNullNumberAsZero(false),

        /**
         * 将 null 布尔字段写为 false（需配合 WriteMapNullValue 使用）
         */
        WriteNullBooleanAsFalse(false);

        private final boolean enabledByDefault;

        Feature(boolean enabledByDefault) {
            this.enabledByDefault = enabledByDefault;
        }

        public boolean isEnabledByDefault() {
            return enabledByDefault;
        }

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

    /** 字符缓冲区（public for ASM 序列化器直接访问，消除 getBuffer() 方法调用开销） */
    public char[] buf;

    /** 当前写入位置（public for ASM 序列化器直接访问，消除 getPosition()/setPosition() 方法调用开销） */
    public int pos;

    /** 特性标志位（Feature 枚举按位 OR 合并，参考 FastJSON2 / Jackson 设计） */
    private long features;

    /** 外部 StringBuilder（如果使用 StringBuilder 模式） */
    private StringBuilder externalSb;
    
    /** 默认缓冲区大小 4KB */
    private static final int DEFAULT_BUF_SIZE = 4096;

    /** 最大缓冲区大小 64MB */
    private static final int MAX_BUF_SIZE = 67108864;

    /** 缓冲区重置时的最大保留容量（超过此容量则缩容，避免线程池中长期持有大缓冲区） */
    private static final int MAX_RESET_CAPACITY = 65536;
    
    /**
     * 构造函数（使用默认缓冲区大小）
     */
    public JSONWriter() {
        this(DEFAULT_BUF_SIZE);
    }
    
    /**
     * 构造函数
     * 
     * @param capacity 初始容量
     */
    public JSONWriter(int capacity) {
        this.buf = new char[capacity];
        this.pos = 0;
    }
    
    /**
     * 构造函数（直接写入 StringBuilder，避免中间 char[] 转换）
     * 
     * @param sb 外部 StringBuilder
     */
    public JSONWriter(StringBuilder sb) {
        this.externalSb = sb;
        this.buf = null;
        this.pos = 0;
    }
    
    /**
     * 写入字符
     */
    public void write(char c) {
        if (externalSb != null) {
            externalSb.append(c);
        } else {
            ensureCapacity(1);
            buf[pos++] = c;
        }
    }
    
    /**
     * 写入字符串
     */
    public void write(String str) {
        if (externalSb != null) {
            externalSb.append(str);
        } else {
            int len = str.length();
            ensureCapacity(len);
            str.getChars(0, len, buf, pos);
            pos += len;
        }
    }
    
    /**
     * 写入字符数组
     */
    public void write(char[] chars, int off, int len) {
        ensureCapacity(len);
        System.arraycopy(chars, off, buf, pos, len);
        pos += len;
    }
    
    /**
     * 写入整数（使用 FastJSON2 快速算法）
     */
    public void writeInt(int value) {
        if (externalSb != null) {
            externalSb.append(value);
        } else {
            ensureCapacity(12);
            pos += NumberUtils.writeInt(value, buf, pos);
        }
    }
    
    /**
     * 写入长整数（使用 FastJSON2 快速算法）
     */
    public void writeLong(long value) {
        if (externalSb != null) {
            externalSb.append(value);
        } else {
            ensureCapacity(22);
            pos += NumberUtils.writeLong(value, buf, pos);
        }
    }
    
    /**
     * 写入浮点数（快速路径：整数部分直接写入，避免 Float.toString() 分配）
     */
    public void writeFloat(float value) {
        if (externalSb != null) {
            externalSb.append(value);
            return;
        }

        if (Float.isNaN(value) || Float.isInfinite(value)) {
            write("null");
            return;
        }

        if (value == 0.0f && Float.floatToRawIntBits(value) < 0) {
            write("-0.0");
            return;
        }

        if (value > Integer.MIN_VALUE && value < Integer.MAX_VALUE) {
            int intValue = (int) value;
            if ((float) intValue == value) {
                ensureCapacity(16);
                pos += NumberUtils.writeInt(intValue, buf, pos);
                buf[pos++] = '.';
                buf[pos++] = '0';
                return;
            }
        }

        write(Float.toString(value));
    }

    /**
     * 写入双精度浮点数（快速路径：避免 Double.toString() 的 String 分配）
     *
     * <p>优化策略：</p>
     * <ul>
     *   <li>精确整数：直接写入整数 + ".0"</li>
     *   <li>2位小数（价格/金额）：significand / 100 直接写入，避免 Double.toString()</li>
     *   <li>1位小数：significand / 10 直接写入</li>
     *   <li>其他：回退到 Double.toString()</li>
     * </ul>
     */
    public void writeDouble(double value) {
        if (externalSb != null) {
            externalSb.append(value);
            return;
        }

        if (Double.isNaN(value) || Double.isInfinite(value)) {
            write("null");
            return;
        }

        if (value == 0.0 && Double.doubleToRawLongBits(value) < 0) {
            write("-0.0");
            return;
        }

        if (value > Long.MIN_VALUE && value < Long.MAX_VALUE) {
            long longValue = (long) value;
            if ((double) longValue == value) {
                ensureCapacity(24);
                pos += NumberUtils.writeLong(longValue, buf, pos);
                buf[pos++] = '.';
                buf[pos++] = '0';
                return;
            }
        }

        if (value > -1e15 && value < 1e15) {
            double scaled2 = value * 100.0;
            long longValue2 = (long) scaled2;
            if ((double) longValue2 == scaled2) {
                ensureCapacity(24);
                if (longValue2 < 0) { buf[pos++] = '-'; longValue2 = -longValue2; }
                long intPart = longValue2 / 100;
                long decPart = longValue2 % 100;
                pos += NumberUtils.writeLong(intPart, buf, pos);
                buf[pos++] = '.';
                if (decPart < 10) buf[pos++] = '0';
                pos += NumberUtils.writeLong(decPart, buf, pos);
                return;
            }

            double scaled1 = value * 10.0;
            long longValue1 = (long) scaled1;
            if ((double) longValue1 == scaled1) {
                ensureCapacity(24);
                if (longValue1 < 0) { buf[pos++] = '-'; longValue1 = -longValue1; }
                long intPart = longValue1 / 10;
                long decPart = longValue1 % 10;
                pos += NumberUtils.writeLong(intPart, buf, pos);
                buf[pos++] = '.';
                pos += NumberUtils.writeLong(decPart, buf, pos);
                return;
            }
        }

        write(Double.toString(value));
    }

    /**
     * 写入 BigDecimal（直接写入 toPlainString，避免精度丢失）。
     *
     * <p>使用 {@link BigDecimal#toPlainString()} 而非 {@link BigDecimal#toString()}，
     * 避免科学计数法输出（如 1E+2），保证 JSON 数字格式合法。</p>
     *
     * <p>当 {@link Feature#WriteBigDecimalAsString} 启用时，
     * BigDecimal 将作为 JSON 字符串（带引号）写入，避免 JavaScript 精度丢失。</p>
     *
     * @param value BigDecimal 值
     * @since 1.0.0
     */
    public void writeBigDecimal(BigDecimal value) {
        if (value == null) {
            write("null");
            return;
        }
        String str = value.toPlainString();
        if (Feature.WriteBigDecimalAsString.isEnabled(this.features)) {
            writeString(str);
        } else {
            write(str);
        }
    }

    /**
     * 写入字符串（带引号，快速路径）
     *
     * <p>优化策略：先扫描检查是否需要转义，无需转义时使用 str.getChars() 批量拷贝，
     * 比逐字符写入更高效（System.arraycopy 底层优化）</p>
     */
    public void writeString(String str) {
        if (externalSb != null) {
            externalSb.append('"');
            int len = str.length();
            boolean needsEscape = false;
            for (int i = 0; i < len; i++) {
                char c = str.charAt(i);
                if (c < ' ' || c == '"' || c == '\\') {
                    needsEscape = true;
                    break;
                }
            }
            if (!needsEscape) {
                externalSb.append(str);
            } else {
                writeStringWithEscapeSb(str);
            }
            externalSb.append('"');
            return;
        }

        writeStringDirect(str);
    }

    /**
     * 直接写入字符串到缓冲区（无 externalSb 检查，用于 JSONWriter 直接模式和 ASM 序列化器）
     *
     * <p>优化策略：</p>
     * <ul>
     *   <li>ASCII 快速路径：纯 ASCII 且无特殊字符时，使用 str.getChars() 批量拷贝</li>
     *   <li>SIMD 风格字级检查：一次检查 8 个字符是否为 ASCII + 无特殊字符，减少逐字符判断</li>
     *   <li>无需转义时直接批量写入，比逐字符写入快 3-5 倍</li>
     * </ul>
     */
    public void writeStringDirect(String str) {
        int len = str.length();
        ensureCapacity(len + 2);

        buf[pos++] = '"';

        // ASCII 快速路径：使用 SIMD 风格字级检查，一次检查 8 个字符
        if (isAsciiSafe(str, len)) {
            // 纯 ASCII 且无特殊字符，直接批量拷贝（System.arraycopy 底层优化）
            str.getChars(0, len, buf, pos);
            pos += len;
            buf[pos++] = '"';
            return;
        }

        // 慢速路径：需要检查每个字符是否需要转义
        writeStringWithEscape(str);
    }

    /**
     * 检查字符串是否为纯 ASCII 且无需 JSON 转义（SIMD 风格字级检查）
     *
     * <p>一次检查 8 个字符：只要所有字符 >= ' ' 且 <= 127 且不是 '"' 和 '\\'，
     * 即为安全字符串，可以批量拷贝。这种字级检查模式与 SIMD 向量化思想一致，
     * 在 JIT 编译后可以利用 CPU 的指令级并行性。</p>
     *
     * @param str 字符串
     * @param len 字符串长度
     * @return true 表示纯 ASCII 安全字符串，可直接批量写入
     */
    private static boolean isAsciiSafe(String str, int len) {
        // SIMD 风格：一次检查 8 个字符
        int i = 0;
        while (i + 7 < len) {
            char c0 = str.charAt(i);
            char c1 = str.charAt(i + 1);
            char c2 = str.charAt(i + 2);
            char c3 = str.charAt(i + 3);
            char c4 = str.charAt(i + 4);
            char c5 = str.charAt(i + 5);
            char c6 = str.charAt(i + 6);
            char c7 = str.charAt(i + 7);
            // 合并检查：非 ASCII（> 127）或控制字符（< ' '）或特殊字符（" \）
            // 使用位运算合并：只要任一字符不安全就返回 false
            if ((c0 > 127 || c0 < ' ' || c0 == '"' || c0 == '\\') ||
                (c1 > 127 || c1 < ' ' || c1 == '"' || c1 == '\\') ||
                (c2 > 127 || c2 < ' ' || c2 == '"' || c2 == '\\') ||
                (c3 > 127 || c3 < ' ' || c3 == '"' || c3 == '\\') ||
                (c4 > 127 || c4 < ' ' || c4 == '"' || c4 == '\\') ||
                (c5 > 127 || c5 < ' ' || c5 == '"' || c5 == '\\') ||
                (c6 > 127 || c6 < ' ' || c6 == '"' || c6 == '\\') ||
                (c7 > 127 || c7 < ' ' || c7 == '"' || c7 == '\\')) {
                return false;
            }
            i += 8;
        }

        // 处理剩余字符
        for (; i < len; i++) {
            char c = str.charAt(i);
            if (c > 127 || c < ' ' || c == '"' || c == '\\') {
                return false;
            }
        }

        return true;
    }

    /**
     * 直接写入字符串到缓冲区（无容量检查，用于外层已预分配容量的场景）
     *
     * <p>跳过 ensureCapacity 检查，减少方法调用开销。
     * 调用者必须确保缓冲区有足够容量（至少 len + 2 个字符）</p>
     *
     * <p>优化：使用 SIMD 风格字级检查，纯 ASCII 安全字符串直接批量拷贝</p>
     */
    public void writeStringDirectNoCheck(String str) {
        int len = str.length();

        buf[pos++] = '"';

        // ASCII 快速路径：使用 SIMD 风格字级检查
        if (isAsciiSafe(str, len)) {
            str.getChars(0, len, buf, pos);
            pos += len;
            buf[pos++] = '"';
            return;
        }

        // 慢速路径：需要转义
        writeStringWithEscape(str);
    }
    
    /**
     * 写入需要转义的字符串（StringBuilder 模式）
     */
    private void writeStringWithEscapeSb(String str) {
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': externalSb.append("\\\""); break;
                case '\\': externalSb.append("\\\\"); break;
                case '\n': externalSb.append("\\n"); break;
                case '\r': externalSb.append("\\r"); break;
                case '\t': externalSb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        externalSb.append("\\u");
                        externalSb.append(String.format("%04x", (int) c));
                    } else {
                        externalSb.append(c);
                    }
            }
        }
    }
    
    /**
     * 写入字符串（带转义）
     */
    private void writeStringWithEscape(String str) {
        int len = str.length();
        ensureCapacity(len * 6 + 2); // 最坏情况
        
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    buf[pos++] = '\\';
                    buf[pos++] = '"';
                    break;
                case '\\':
                    buf[pos++] = '\\';
                    buf[pos++] = '\\';
                    break;
                case '\n':
                    buf[pos++] = '\\';
                    buf[pos++] = 'n';
                    break;
                case '\r':
                    buf[pos++] = '\\';
                    buf[pos++] = 'r';
                    break;
                case '\t':
                    buf[pos++] = '\\';
                    buf[pos++] = 't';
                    break;
                case '\b':
                    buf[pos++] = '\\';
                    buf[pos++] = 'b';
                    break;
                case '\f':
                    buf[pos++] = '\\';
                    buf[pos++] = 'f';
                    break;
                default:
                    if (c < ' ') {
                        buf[pos++] = '\\';
                        buf[pos++] = 'u';
                        buf[pos++] = '0';
                        buf[pos++] = '0';
                        char h = (char) (c >> 4);
                        char l = (char) (c & 0xf);
                        buf[pos++] = (char) (h < 10 ? h + '0' : h - 10 + 'a');
                        buf[pos++] = (char) (l < 10 ? l + '0' : l - 10 + 'a');
                    } else {
                        buf[pos++] = c;
                    }
                    break;
            }
        }
        
        buf[pos++] = '"';
    }
    
    /**
     * 确保容量（几何增长策略：2x 扩容，避免固定增量导致的多次扩容）
     *
     * <p>优化策略：</p>
     * <ul>
     *   <li>几何增长：新容量 = max(当前容量 * 2, 所需容量)，摊还 O(1) 扩容成本</li>
     *   <li>上限保护：超过 MAX_BUF_SIZE 时停止扩容</li>
     * </ul>
     */
    void ensureCapacity(int minCapacity) {
        if (pos + minCapacity > buf.length) {
            // 几何增长：2x 扩容，同时保证满足最小需求
            int newCapacity = Math.max(buf.length * 2, pos + minCapacity);
            if (newCapacity > MAX_BUF_SIZE) {
                newCapacity = MAX_BUF_SIZE;
            }
            char[] newBuf = new char[newCapacity];
            System.arraycopy(buf, 0, newBuf, 0, pos);
            buf = newBuf;
        }
    }
    
    /**
     * 预分配缓冲区（避免序列化过程中多次扩容）
     *
     * <p>优化策略：</p>
     * <ul>
     *   <li>几何增长：新容量 = max(当前容量 * 2, 所需容量)</li>
     *   <li>上限保护：超过 MAX_BUF_SIZE 时停止扩容</li>
     * </ul>
     *
     * @param minCapacity 最小需要的容量
     */
    public void preAllocate(int minCapacity) {
        if (buf != null && pos + minCapacity > buf.length) {
            int newCapacity = Math.max(buf.length * 2, pos + minCapacity);
            if (newCapacity > MAX_BUF_SIZE) {
                newCapacity = MAX_BUF_SIZE;
            }
            char[] newBuf = new char[newCapacity];
            System.arraycopy(buf, 0, newBuf, 0, pos);
            buf = newBuf;
        }
    }

    /**
     * 基于已知类型预分配缓冲区容量（减少序列化过程中的动态扩容次数）
     *
     * <p>根据对象类型估算 JSON 输出大小，一次性分配足够容量：</p>
     * <ul>
     *   <li>Bean：字段数 * 64 + 32</li>
     *   <li>Collection：元素数 * 64 + 16</li>
     *   <li>Map：条目数 * 64 + 16</li>
     * </ul>
     *
     * @param obj 待序列化对象
     */
    public void preAllocateForObject(Object obj) {
        if (obj == null || buf == null) return;

        int estimated = 0;
        if (obj instanceof Collection) {
            estimated = ((Collection<?>) obj).size() * 64 + 16;
        } else if (obj instanceof Map) {
            estimated = ((Map<?, ?>) obj).size() * 64 + 16;
        } else if (obj.getClass().isArray()) {
            estimated = Array.getLength(obj) * 64 + 16;
        } else {
            // Bean 类型：粗略估算
            estimated = 256;
        }

        if (pos + estimated > buf.length) {
            preAllocate(estimated);
        }
    }
    
    /**
     * 转换为字符串（使用 JDK 9+ 优化的 String 构造）
     */
    @Override
    public String toString() {
        if (externalSb != null) {
            return externalSb.toString();
        }
        if (pos == 0) {
            return "";
        }
        return new String(buf, 0, pos);
    }

    /**
     * 直接将内部 char[] 缓冲区编码为 UTF-8 字节数组。
     *
     * <p>避免 {@code new String(buf).getBytes(UTF_8)} 的双重分配：
     * 先创建 String 再创建 byte[]。本方法对于纯 ASCII 内容直接 1:1 拷贝，
     * 跳过 String 中间层。</p>
     *
     * @return UTF-8 编码的字节数组
     * @since 1.0.0
     */
    public byte[] toUtf8Bytes() {
        if (externalSb != null) {
            return externalSb.toString().getBytes(StandardCharsets.UTF_8);
        }
        if (pos == 0) {
            return new byte[0];
        }
        // 快速路径：检查是否纯 ASCII
        boolean allAscii = true;
        for (int i = 0; i < pos; i++) {
            if (buf[i] > 127) {
                allAscii = false;
                break;
            }
        }
        if (allAscii) {
            // 纯 ASCII：char → byte 直接拷贝，1:1 映射
            byte[] bytes = new byte[pos];
            for (int i = 0; i < pos; i++) {
                bytes[i] = (byte) buf[i];
            }
            return bytes;
        }
        // 非 ASCII：回退到标准 UTF-8 编码
        return new String(buf, 0, pos).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 将当前缓冲区内容直接写入 OutputStream（免中间 byte[] 分配）。
     *
     * <p>对于纯 ASCII 内容，直接 1:1 char→byte 写入流；非 ASCII 内容回退
     * 到 {@link #toUtf8Bytes()} 一次性写入。</p>
     *
     * @param out 目标输出流
     * @throws java.io.IOException IO 异常
     */
    public void writeTo(java.io.OutputStream out) throws java.io.IOException {
        if (externalSb != null) {
            out.write(externalSb.toString().getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (pos == 0) {
            return;
        }
        // 快速路径：检查是否纯 ASCII
        boolean allAscii = true;
        for (int i = 0; i < pos; i++) {
            if (buf[i] > 127) {
                allAscii = false;
                break;
            }
        }
        if (allAscii) {
            for (int i = 0; i < pos; i++) {
                out.write((byte) buf[i]);
            }
        } else {
            out.write(new String(buf, 0, pos).getBytes(StandardCharsets.UTF_8));
        }
    }
    
    /**
     * 直接获取内部 char[] 缓冲区和长度（避免 String 拷贝）
     * 
     * <p>调用者必须在使用完毕后调用 reset()，否则数据会被覆盖</p>
     */
    public char[] getBuffer() {
        return buf;
    }
    
    public int getLength() {
        return pos;
    }
    
    /**
     * 重置写入位置（带缩容保护：避免线程池中长期持有过大缓冲区）
     *
     * <p>当缓冲区容量超过 MAX_RESET_CAPACITY 时，缩容到默认大小，
     * 防止偶尔序列化大对象后，缓冲区一直占用大量内存</p>
     */
    public void reset() {
        pos = 0;
        // 缩容保护：缓冲区过大时回收到默认大小，避免内存浪费
        if (buf != null && buf.length > MAX_RESET_CAPACITY) {
            buf = new char[DEFAULT_BUF_SIZE];
        }
    }
    
    /**
     * 获取当前容量
     */
    public int capacity() {
        return buf.length;
    }
    
    /**
     * 获取已写入字符数
     */
    public int size() {
        return pos;
    }

    /**
     * 获取当前写入位置（供 ASM 序列化器直接操作缓冲区）
     *
     * <p>ASM 生成的序列化器通过 getBuffer() + getPosition() 获取直接缓冲区访问，
     * 消除 write() 方法调用的 externalSb 检查和 ensureCapacity 检查开销</p>
     */
    public int getPosition() {
        return pos;
    }

    /**
     * 设置当前写入位置（供 ASM 序列化器直接操作缓冲区）
     *
     * <p>在直接缓冲区写入完成后，通过 setPosition() 同步写入位置到 JSONWriter</p>
     *
     * @param position 新的写入位置
     */
    public void setPosition(int position) {
        pos = position;
    }

    /**
     * 检查字符串是否需要 JSON 转义（SIMD 风格字级检查优化）
     *
     * <p>用于 ASM 序列化器的字符串快速路径判断：
     * 无需转义时直接内联写入缓冲区，避免 sync/re-read 开销</p>
     *
     * <p>优化：一次检查 8 个字符，利用 CPU 指令级并行性加速</p>
     *
     * @param str 待检查的字符串
     * @return true 表示需要转义，false 表示无需转义
     */
    public static boolean needsEscape(String str) {
        int len = str.length();
        int i = 0;
        // SIMD 风格：一次检查 8 个字符
        while (i + 7 < len) {
            char c0 = str.charAt(i);
            char c1 = str.charAt(i + 1);
            char c2 = str.charAt(i + 2);
            char c3 = str.charAt(i + 3);
            char c4 = str.charAt(i + 4);
            char c5 = str.charAt(i + 5);
            char c6 = str.charAt(i + 6);
            char c7 = str.charAt(i + 7);
            if ((c0 < ' ' || c0 == '"' || c0 == '\\') ||
                (c1 < ' ' || c1 == '"' || c1 == '\\') ||
                (c2 < ' ' || c2 == '"' || c2 == '\\') ||
                (c3 < ' ' || c3 == '"' || c3 == '\\') ||
                (c4 < ' ' || c4 == '"' || c4 == '\\') ||
                (c5 < ' ' || c5 == '"' || c5 == '\\') ||
                (c6 < ' ' || c6 == '"' || c6 == '\\') ||
                (c7 < ' ' || c7 == '"' || c7 == '\\')) {
                return true;
            }
            i += 8;
        }
        // 处理剩余字符
        for (; i < len; i++) {
            char c = str.charAt(i);
            if (c < ' ' || c == '"' || c == '\\') {
                return true;
            }
        }
        return false;
    }

    /**
     * 写入双精度浮点数到缓冲区（合并 setPosition + writeDouble + getPosition）
     *
     * <p>消除 ASM 序列化器中 setPosition 和 getPosition 的额外方法调用开销</p>
     *
     * @param value 双精度浮点数值
     * @param pos 当前写入位置
     * @return 写入后的新位置
     */
    public int writeDoubleToBuf(double value, int pos) {
        this.pos = pos;
        writeDouble(value);
        return this.pos;
    }

    /**
     * 写入单精度浮点数到缓冲区（合并 setPosition + writeFloat + getPosition）
     *
     * <p>消除 ASM 序列化器中 setPosition 和 getPosition 的额外方法调用开销</p>
     *
     * @param value 单精度浮点数值
     * @param pos 当前写入位置
     * @return 写入后的新位置
     */
    public int writeFloatToBuf(float value, int pos) {
        this.pos = pos;
        writeFloat(value);
        return this.pos;
    }

    /**
     * 写入带引号字符串到缓冲区（合并 setPosition + writeString + getPosition）
     *
     * <p>消除 ASM 序列化器中 setPosition 和 getPosition 的额外方法调用开销</p>
     *
     * @param str 字符串值
     * @param pos 当前写入位置
     * @return 写入后的新位置
     */
    public int writeStringToBuf(String str, int pos) {
        this.pos = pos;
        writeStringDirect(str);
        return this.pos;
    }

    /**
     * 写入集合到缓冲区（合并 setPosition + writeCollection + getPosition）
     *
     * @param collection 集合对象
     * @param pos 当前写入位置
     * @return 写入后的新位置
     */
    public int writeCollectionToBuf(Collection<?> collection, int pos) {
        this.pos = pos;
        writeCollection(collection);
        return this.pos;
    }

    /**
     * 写入集合到缓冲区（使用预解析序列化器，合并 setPosition + writeCollectionWithSerializer + getPosition）
     *
     * @param collection 集合对象
     * @param serializer 预解析的元素序列化器
     * @param pos 当前写入位置
     * @return 写入后的新位置
     */
    public int writeCollectionWithSerializerToBuf(Collection<?> collection,
            AsmSerializer<Object> serializer, int pos) {
        this.pos = pos;
        writeCollectionWithSerializer(collection, serializer);
        return this.pos;
    }

    /**
     * 写入 Map 到缓冲区（合并 setPosition + writeMap + getPosition）
     *
     * @param map Map 对象
     * @param pos 当前写入位置
     * @return 写入后的新位置
     */
    public int writeMapToBuf(Map<?, ?> map, int pos) {
        this.pos = pos;
        writeMap(map);
        return this.pos;
    }

    /**
     * 直接写入集合（优化版本，缓存序列化器避免每元素 ConcurrentHashMap 查找）
     * 
     * <p>信任缓存序列化器的类型一致性，跳过 item.getClass() 和类型比较开销</p>
     */
    
    public void writeCollection(Collection<?> collection) {
        if (collection == null) {
            write("null");
            return;
        }

        int size = collection.size();
        if (size > 0) {
            preAllocate(size * 64);
        }

        buf[pos++] = '[';
        AsmSerializer<?> cachedSerializer = null;

        // 优化：对 List 使用索引循环，避免 Iterator 对象创建开销
        if (collection instanceof List) {
            List<?> list = (List<?>) collection;
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    buf[pos++] = ',';
                }
                Object item = list.get(i);
                cachedSerializer = writeCollectionElement(item, cachedSerializer);
            }
        } else {
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    buf[pos++] = ',';
                }
                first = false;
                cachedSerializer = writeCollectionElement(item, cachedSerializer);
            }
        }
        buf[pos++] = ']';
    }

    /**
     * 写入集合中的单个元素（提取自 writeCollection，消除 List/非List 路径重复代码）。
     *
     * @param item            元素值
     * @param cachedSerializer 缓存的 ASM 序列化器（可能为 null）
     * @return 更新后的缓存序列化器
     */
    private AsmSerializer<?> writeCollectionElement(Object item, AsmSerializer<?> cachedSerializer) {
        if (item == null) {
            buf[pos] = 'n'; buf[pos + 1] = 'u'; buf[pos + 2] = 'l'; buf[pos + 3] = 'l';
            pos += 4;
            return cachedSerializer;
        }

        if (cachedSerializer != null) {
            AsmCodecCache.serializeWithSerializer(cachedSerializer, item, this);
            return cachedSerializer;
        }

        if (item instanceof String) {
            writeStringDirectNoCheck((String) item);
        } else if (item instanceof Integer) {
            writeInt(((Integer) item).intValue());
        } else if (item instanceof Long) {
            writeLong(((Long) item).longValue());
        } else if (item instanceof Double) {
            writeDouble(((Double) item).doubleValue());
        } else if (item instanceof Float) {
            writeFloat(((Float) item).floatValue());
        } else if (item instanceof Boolean) {
            write(((Boolean) item).booleanValue() ? "true" : "false");
        } else if (item instanceof Collection) {
            writeCollection((Collection<?>) item);
        } else if (item instanceof Map) {
            writeMap((Map<?, ?>) item);
        } else {
            AsmSerializer<?> serializer =
                AsmCodecCache.getOrCreateSerializerForType(item.getClass());
            if (serializer != null) {
                cachedSerializer = serializer;
                AsmCodecCache.serializeWithSerializer(serializer, item, this);
            } else {
                write(YdszJson.toJson(item));
            }
        }
        return cachedSerializer;
    }

    /**
     * 直接写入集合（使用预解析的序列化器，跳过每元素类型检查）
     *
     * <p>适用于集合元素类型已知的场景，消除 instanceof 判断和 ConcurrentHashMap 查找开销。</p>
     * <p>直接操作 buf/pos 写入结构字符，消除 write(char) 的 externalSb + ensureCapacity 检查开销。</p>
     * <p>对 List 类型使用索引循环避免 Iterator 对象创建开销。</p>
     * <p>使用 serializeInline 跳过每个元素的 preAllocate 调用，外层已预分配足够容量。</p>
     *
     * @param collection 集合对象
     * @param serializer 预解析的元素序列化器
     */
    public void writeCollectionWithSerializer(Collection<?> collection,
            AsmSerializer<Object> serializer) {
        if (collection == null) {
            write("null");
            return;
        }

        int size = collection.size();

        buf[pos++] = '[';

        if (collection instanceof List) {
            List<?> list = (List<?>) collection;
            for (int i = 0; i < size; i++) {
                if (i > 0) {
                    buf[pos++] = ',';
                }
                Object item = list.get(i);
                if (item == null) {
                    buf[pos] = 'n'; buf[pos + 1] = 'u'; buf[pos + 2] = 'l'; buf[pos + 3] = 'l';
                    pos += 4;
                    continue;
                }
                serializer.serializeInline(item, this);
            }
        } else {
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    buf[pos++] = ',';
                }
                first = false;
                if (item == null) {
                    buf[pos] = 'n'; buf[pos + 1] = 'u'; buf[pos + 2] = 'l'; buf[pos + 3] = 'l';
                    pos += 4;
                    continue;
                }
                serializer.serializeInline(item, this);
            }
        }

        buf[pos++] = ']';
    }

    /**
     * 直接写入 Map（优化版本）
     */
    public void writeMap(Map<?, ?> map) {
        if (map == null) {
            write("null");
            return;
        }

        int size = map.size();
        if (size > 0) {
            preAllocate(size * 64);
        }

        buf[pos++] = '{';
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                buf[pos++] = ',';
            }
            first = false;

            Object key = entry.getKey();
            Object value = entry.getValue();

            if (key instanceof String) {
                writeStringDirectNoCheck((String) key);
            } else {
                writeStringDirectNoCheck(String.valueOf(key));
            }
            buf[pos++] = ':';
            writeValueInline(value);
        }
        buf[pos++] = '}';
    }

    /**
     * 内联写入对象值（不调用 YdszJson.toJson）
     */
    /**
     * 内联写入对象值（使用类型代码缓存，避免重复 instanceof 检查）
     */
    private void writeObjectInline(Object obj) {
        if (obj == null) {
            write("null");
            return;
        }

        Class<?> clazz = obj.getClass();
        
        // 快速路径：使用预计算的序列化器缓存
        
        AsmSerializer<?> serializer =
            AsmCodecCache.getOrCreateSerializerForType(clazz);
        if (serializer != null) {
            AsmCodecCache.serializeWithSerializer(serializer, obj, this);
        } else if (obj instanceof Collection) {
            writeCollection((Collection<?>) obj);
        } else if (obj instanceof Map) {
            writeMap((Map<?, ?>) obj);
        } else {
            write(YdszJson.toJson(obj));
        }
    }

    /**
     * 内联写入值（不调用 YdszJson.toJson）
     */
    private void writeValueInline(Object value) {
        if (value == null) {
            buf[pos] = 'n'; buf[pos + 1] = 'u'; buf[pos + 2] = 'l'; buf[pos + 3] = 'l';
            pos += 4;
        } else if (value instanceof String) {
            writeStringDirectNoCheck((String) value);
        } else if (value instanceof Number) {
            write(value.toString());
        } else if (value instanceof Boolean) {
            if ((Boolean) value) {
                buf[pos] = 't'; buf[pos + 1] = 'r'; buf[pos + 2] = 'u'; buf[pos + 3] = 'e';
                pos += 4;
            } else {
                buf[pos] = 'f'; buf[pos + 1] = 'a'; buf[pos + 2] = 'l'; buf[pos + 3] = 's'; buf[pos + 4] = 'e';
                pos += 5;
            }
        } else if (value instanceof Collection) {
            writeCollection((Collection<?>) value);
        } else if (value instanceof Map) {
            writeMap((Map<?, ?>) value);
        } else {
            writeObjectInline(value);
        }
    }
}
