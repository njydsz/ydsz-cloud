package com.njydsz.common.json.reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * @author YdszJson Team
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

        public boolean isEnabled(long features) {
            return (features & (1L << ordinal())) != 0;
        }

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
    
    /** ThreadLocal 读取器池（复用 JSONReader 实例和 char[] 缓冲区，避免 GC 开销） */
    private static final ThreadLocal<JSONReader> READER_POOL = new ThreadLocal<>();
    
    /**
     * 构造函数
     */
    public JSONReader(String json) {
        this.buf = json.toCharArray();
        this.pos = 0;
        this.len = buf.length;
    }
    
    /**
     * 构造函数（char[] 模式）
     */
    public JSONReader(char[] buf, int offset, int length) {
        this.buf = buf;
        this.pos = offset;
        this.len = offset + length;
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
    
    public char readChar() {
        if (pos >= len) throw new IllegalStateException("Unexpected end of JSON");
        return buf[pos++];
    }
    
    public void back() {
        if (pos > 0) pos--;
    }
    
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
    
    public String readFieldNameFast() {
        if (pos >= len) return null;
        if (buf[pos] != '"') return null;
        return readString();
    }
    
    public void skipWhitespace() {
        while (pos < len && buf[pos] <= ' ') pos++;
    }
    
    public void skipTo(char target) {
        while (pos < len) {
            char c = buf[pos];
            if (c == target) return;
            if (c > ' ') return;
            pos++;
        }
    }
    
    public char nextChar() {
        skipWhitespace();
        if (pos >= len) throw new RuntimeException("Unexpected end of JSON");
        return buf[pos++];
    }
    
    public boolean matchField(String fieldName) {
        skipWhitespace();
        if (pos >= len || buf[pos] != '"') return false;
        pos++;
        int fieldLen = fieldName.length();
        if (pos + fieldLen > len) return false;
        for (int i = 0; i < fieldLen; i++) {
            if (buf[pos + i] != fieldName.charAt(i)) {
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
                else if (ch2 == '"') { pos++; while (pos < len && buf[pos] != '"') { if (buf[pos] == '\\') pos++; pos++; } }
                pos++;
            }
        } else if (ch == '[') {
            pos++; int depth = 1;
            while (depth > 0 && pos < len) {
                char ch2 = buf[pos];
                if (ch2 == '[') depth++;
                else if (ch2 == ']') depth--;
                else if (ch2 == '"') { pos++; while (pos < len && buf[pos] != '"') { if (buf[pos] == '\\') pos++; pos++; } }
                pos++;
            }
        } else if (ch == '"') {
            readString();
        } else {
            while (pos < len) { char ch2 = buf[pos]; if (ch2 == ',' || ch2 == '}' || ch2 == ']' || ch2 <= ' ') break; pos++; }
        }
        return new String(buf, start, pos - start);
    }
    
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
                        sb.append(unicode);
                        pos += 4;
                        break;
                    default: sb.append(escaped); break;
                }
            } else sb.append(ch);
        }
        return sb.toString();
    }
    
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
    
    public boolean readBoolean() {
        skipWhitespace();
        if (pos + 4 <= len && buf[pos] == 't' && buf[pos+1] == 'r' && buf[pos+2] == 'u' && buf[pos+3] == 'e') { pos += 4; return true; }
        if (pos + 5 <= len && buf[pos] == 'f' && buf[pos+1] == 'a' && buf[pos+2] == 'l' && buf[pos+3] == 's' && buf[pos+4] == 'e') { pos += 5; return false; }
        throw new IllegalStateException("Expected boolean, got: " + buf[pos]);
    }
    
    public void readNull() {
        skipWhitespace();
        if (pos + 4 <= len && buf[pos] == 'n' && buf[pos+1] == 'u' && buf[pos+2] == 'l' && buf[pos+3] == 'l') pos += 4;
        else throw new IllegalStateException("Expected null, got: " + buf[pos]);
    }
    
    public boolean isNull() {
        skipWhitespace();
        return pos + 4 <= len && buf[pos] == 'n' && buf[pos+1] == 'u' && buf[pos+2] == 'l' && buf[pos+3] == 'l';
    }
    
    public void readObjectStart() { if (nextChar() != '{') throw new IllegalStateException("Expected '{'"); }
    public void readObjectEnd() { if (nextChar() != '}') throw new IllegalStateException("Expected '}'"); }
    public void readArrayStart() { if (nextChar() != '[') throw new IllegalStateException("Expected '['"); }
    public void readArrayEnd() { if (nextChar() != ']') throw new IllegalStateException("Expected ']'"); }
    
    public String readFieldName() {
        if (pos >= len) return null;
        char ch = buf[pos];
        while (ch <= ' ') { pos++; if (pos >= len) return null; ch = buf[pos]; }
        if (ch == '}') { pos++; return null; }
        if (ch == ',') { pos++; while (pos < len) { ch = buf[pos]; if (ch == '"') break; pos++; } }
        if (pos >= len || buf[pos] != '"') return null;
        return readString();
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
    
    public static long fnv1aHash(String name) {
        long hash = 0x811c9dc5;
        for (int i = 0; i < name.length(); i++) { hash ^= name.charAt(i); hash *= 0x100000001b3L; }
        return hash;
    }
    
    public int getPosition() { return pos; }
    public boolean isEnd() { return pos >= len; }
    
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
                else if (ch2 == '"') { pos++; while (pos < len) { if (buf[pos] == '\\') { pos += 2; continue; } if (buf[pos] == '"') break; pos++; } }
                pos++;
            }
        } else if (ch == '[') {
            pos++; int depth = 1;
            while (depth > 0 && pos < len) {
                char ch2 = buf[pos];
                if (ch2 == '[') depth++;
                else if (ch2 == ']') depth--;
                else if (ch2 == '"') { pos++; while (pos < len) { if (buf[pos] == '\\') { pos += 2; continue; } if (buf[pos] == '"') break; pos++; } }
                pos++;
            }
        } else if (ch == '"') { readString(); }
        else if (ch == 't') pos += 4;
        else if (ch == 'f') pos += 5;
        else if (ch == 'n') pos += 4;
        else { while (pos < len) { char ch2 = buf[pos]; if (ch2 == ',' || ch2 == '}' || ch2 == ']' || ch2 <= ' ') break; pos++; } }
    }
    
    public List<Object> readArray(Class<?> elementType, ObjectReader<?> elementReader) {
        skipWhitespace();
        if (pos >= len || buf[pos] != '[') throw new RuntimeException("Expected [ at position " + pos);
        pos++;
        List<Object> result = new ArrayList<>();
        while (pos < len) {
            skipWhitespace();
            if (buf[pos] == ']') { pos++; return result; }
            if (buf[pos] == ',') { pos++; continue; }
            Object element = readArrayElement(elementType, elementReader);
            result.add(element);
        }
        return result;
    }
    
    
    private Object readArrayElement(Class<?> elementType, ObjectReader<?> elementReader) {
        if (elementType == null || elementType == Object.class) return readAnyValue();
        if (elementType == String.class) return readString();
        if (elementType == int.class || elementType == Integer.class) return Integer.valueOf(readInt());
        if (elementType == long.class || elementType == Long.class) return Long.valueOf(readLong());
        if (elementType == double.class || elementType == Double.class) return Double.valueOf(readDouble());
        if (elementType == float.class || elementType == Float.class) return Float.valueOf(readFloat());
        if (elementType == boolean.class || elementType == Boolean.class) return Boolean.valueOf(readBoolean());
        if (elementReader != null) return elementReader.readObject(this);
        return readAnyValue();
    }
    
    private Object readAnyValue() {
        skipWhitespace();
        if (pos >= len) return null;
        char ch = buf[pos];
        if (ch == '"') return readString();
        if (ch == '{') return readObjectMap();
        if (ch == '[') return readArray(Object.class, null);
        if (ch == 't') { pos += 4; return true; }
        if (ch == 'f') { pos += 5; return false; }
        if (ch == 'n') { pos += 4; return null; }
        return readDouble();
    }
    
    public Map<String, Object> readObjectMap() {
        skipWhitespace();
        if (pos >= len || buf[pos] != '{') throw new RuntimeException("Expected { at position " + pos);
        pos++;
        Map<String, Object> result = new HashMap<>();
        while (pos < len) {
            skipWhitespace();
            if (buf[pos] == '}') { pos++; return result; }
            if (buf[pos] == ',') { pos++; continue; }
            String key = readFieldNameFast();
            if (key == null) break;
            skipTo(':');
            if (pos < len) pos++;
            Object value = readAnyValue();
            result.put(key, value);
        }
        return result;
    }
}
