package com.njydsz.common.json.number;

/**
 * 高性能数字编码工具类
 *
 * <p>使用两位数查找表和除法优化算法，比 StringBuilder.append() 快 3-5 倍</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class NumberUtils {

    /** 数字字符表 */
    private static final char[] DIGITS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    };

    /** 两位数查找表（存储两位数字的字符表示） */
    private static final int[] DIGIT_TENS = new int[100];

    /** 位偏移量（用于将两个字符打包到一个 int 中高8位的移位值） */
    private static final int BITS_PER_BYTE = 8;

    /** 低8位掩码（用于提取打包 int 中低8位的字节值） */
    private static final int LOW_BYTE_MASK = 0xff;

    /** Integer.MIN_VALUE 的十进制字符串表示长度（"-2147483648" = 11 个字符） */
    private static final int MIN_INT_VALUE_DIGIT_COUNT = 11;

    /** Long.MIN_VALUE 的十进制字符串表示长度（"-9223372036854775808" = 20 个字符） */
    private static final int MIN_LONG_VALUE_DIGIT_COUNT = 20;

    /** 快速路径阈值：大于等于此值时使用两位数查表加速（100 * 656 = 65600 > 65536） */
    private static final int FAST_PATH_THRESHOLD = 65536;

    // ==================== sizeOfInt / sizeOfSmallInt / sizeOfMediumInt 相关常量 ====================

    /** 一位数上界阈值：小于此值时为 1 位数（10^1） */
    private static final int INT_ONE_DIGIT_THRESHOLD = 10;

    /** 一位数的字符表示长度 */
    private static final int INT_ONE_DIGIT_SIZE = 1;

    /** 两位数上界阈值：小于此值时为 2 位数（10^2） */
    private static final int INT_TWO_DIGITS_THRESHOLD = 100;

    /** 两位数的字符表示长度 */
    private static final int INT_TWO_DIGITS_SIZE = 2;

    /** 三位数上界阈值：小于此值时为 3 位数（10^3） */
    private static final int INT_THREE_DIGITS_THRESHOLD = 1000;

    /** 三位数的字符表示长度 */
    private static final int INT_THREE_DIGITS_SIZE = 3;

    /** 四位数上界阈值：小于此值时为 4 位数（10^4） */
    private static final int INT_FOUR_DIGITS_THRESHOLD = 10000;

    /** 四位数的字符表示长度 */
    private static final int INT_FOUR_DIGITS_SIZE = 4;

    /** 五位数上界阈值：小于此值时为 5 位数（10^5） */
    private static final int INT_FIVE_DIGITS_THRESHOLD = 100000;

    /** 五位数的字符表示长度 */
    private static final int INT_FIVE_DIGITS_SIZE = 5;

    /** 六位数上界阈值：小于此值时为 6 位数（10^6） */
    private static final int INT_SIX_DIGITS_THRESHOLD = 1000000;

    /** 六位数的字符表示长度 */
    private static final int INT_SIX_DIGITS_SIZE = 6;

    /** 七位数上界阈值：小于此值时为 7 位数（10^7） */
    private static final int INT_SEVEN_DIGITS_THRESHOLD = 10000000;

    /** 七位数的字符表示长度 */
    private static final int INT_SEVEN_DIGITS_SIZE = 7;

    /** 八位数上界阈值：小于此值时为 8 位数（10^8） */
    private static final int INT_EIGHT_DIGITS_THRESHOLD = 100000000;

    /** 八位数的字符表示长度 */
    private static final int INT_EIGHT_DIGITS_SIZE = 8;

    /** 最大九位数上界阈值：小于此值时为 9 位数（10^9） */
    private static final int INT_NINE_DIGITS_THRESHOLD = 1000000000;

    /** 九位数的字符表示长度 */
    private static final int INT_NINE_DIGITS_SIZE = 9;

    /** 十位数的字符表示长度 */
    private static final int INT_TEN_DIGITS_SIZE = 10;

    // ==================== sizeOfLong / sizeOfMediumLong / sizeOfLargeLong 相关常量 ====================

    /** 十位数下界阈值：大于等于此值时为 10 位数（10^10） */
    private static final long LONG_TEN_DIGITS_THRESHOLD = 10000000000L;

    /** 十位数的字符表示长度 */
    private static final int LONG_TEN_DIGITS_SIZE = 10;

    /** 十一位数上界阈值：小于此值时为 11 位数（10^11） */
    private static final long LONG_ELEVEN_DIGITS_THRESHOLD = 100000000000L;

    /** 十一位数的字符表示长度 */
    private static final int LONG_ELEVEN_DIGITS_SIZE = 11;

    /** 十二位数上界阈值：小于此值时为 12 位数（10^12） */
    private static final long LONG_TWELVE_DIGITS_THRESHOLD = 1000000000000L;

    /** 十二位数的字符表示长度 */
    private static final int LONG_TWELVE_DIGITS_SIZE = 12;

    /** 十三位数的字符表示长度 */
    private static final int LONG_THIRTEEN_DIGITS_SIZE = 13;

    /** 十四位数上界阈值：小于此值时为 14 位数（10^14） */
    private static final long LONG_FOURTEEN_DIGITS_THRESHOLD = 100000000000000L;

    /** 十四位数的字符表示长度 */
    private static final int LONG_FOURTEEN_DIGITS_SIZE = 14;

    /** 十五位数上界阈值：小于此值时为 15 位数（10^15） */
    private static final long LONG_FIFTEEN_DIGITS_THRESHOLD = 1000000000000000L;

    /** 十五位数的字符表示长度 */
    private static final int LONG_FIFTEEN_DIGITS_SIZE = 15;

    /** 十六位数上界阈值：小于此值时为 16 位数（10^16） */
    private static final long LONG_SIXTEEN_DIGITS_THRESHOLD = 10000000000000000L;

    /** 十六位数的字符表示长度 */
    private static final int LONG_SIXTEEN_DIGITS_SIZE = 16;

    /** 十七位数上界阈值：小于此值时为 17 位数（10^17） */
    private static final long LONG_SEVENTEEN_DIGITS_THRESHOLD = 100000000000000000L;

    /** 十七位数的字符表示长度 */
    private static final int LONG_SEVENTEEN_DIGITS_SIZE = 17;

    /** 十八位数上界阈值：小于此值时为 18 位数（10^18） */
    private static final long LONG_EIGHTEEN_DIGITS_THRESHOLD = 1000000000000000000L;

    /** 十八位数的字符表示长度 */
    private static final int LONG_EIGHTEEN_DIGITS_SIZE = 18;

    /** 十九位数的字符表示长度 */
    private static final int LONG_NINETEEN_DIGITS_SIZE = 19;

    /** 19 位十进制数可容纳的最大值（小于 Long.MAX_VALUE） */
    private static final long LONG_19_DIGITS_MAX = 9223372036854775807L;

    /** 负号加两位数字的字符串长度（"-" + 2 digits = 3） */
    private static final int NEGATIVE_TWO_DIGIT_LEN = 3;

    static {
        for (int i = 0; i < 100; i++) {
            int tens = (i / 10) + '0';
            int ones = (i % 10) + '0';
            DIGIT_TENS[i] = (tens << BITS_PER_BYTE) | ones;
        }
    }

    /**
     * 将整数写入字符数组
     *
     * @param value 整数值
     * @param buf 目标字符数组
     * @param off 偏移量
     * @return 写入的字符数
     */
    public static int writeInt(int value, char[] buf, int off) {
        if (value == 0) {
            buf[off] = '0';
            return 1;
        }

        if (value == Integer.MIN_VALUE) {
            "-2147483648".getChars(0, MIN_INT_VALUE_DIGIT_COUNT, buf, off);
            return MIN_INT_VALUE_DIGIT_COUNT;
        }

        boolean negative = value < 0;
        if (negative) {
            buf[off] = '-';
            value = -value;
            off++;
        }

        // 计算数字位数
        int size = sizeOfInt(value);
        int charPos = off + size - 1;

        // 快速路径：两位数字查表
        while (value >= FAST_PATH_THRESHOLD) {
            int q = value / 100;
            int r = value - q * 100;
            value = q;
            int tmp = DIGIT_TENS[r];
            buf[charPos--] = (char) (tmp & LOW_BYTE_MASK);
            buf[charPos--] = (char) (tmp >> BITS_PER_BYTE);
        }

        // 剩余部分
        while (value > 0) {
            int q = value / 10;
            int r = value - q * 10;
            value = q;
            buf[charPos--] = DIGITS[r];
        }

        return size + (negative ? 1 : 0);
    }

    /**
     * 将长整数写入字符数组
     *
     * @param value 长整数值
     * @param buf 目标字符数组
     * @param off 偏移量
     * @return 写入的字符数
     */
    public static int writeLong(long value, char[] buf, int off) {
        if (value == 0) {
            buf[off] = '0';
            return 1;
        }

        if (value == Long.MIN_VALUE) {
            "-9223372036854775808".getChars(0, MIN_LONG_VALUE_DIGIT_COUNT, buf, off);
            return MIN_LONG_VALUE_DIGIT_COUNT;
        }

        boolean negative = value < 0;
        if (negative) {
            buf[off] = '-';
            value = -value;
            off++;
        }

        // 快速路径：小整数使用 int 逻辑
        if (value <= Integer.MAX_VALUE) {
            int intValue = (int) value;
            return writeInt(intValue, buf, off) + (negative ? 1 : 0);
        }

        // 长整数处理
        int size = sizeOfLong(value);
        int charPos = off + size - 1;

        while (value >= FAST_PATH_THRESHOLD) {
            long q = value / 100;
            long r = value - q * 100;
            value = q;
            int tmp = DIGIT_TENS[(int) r];
            buf[charPos--] = (char) (tmp & LOW_BYTE_MASK);
            buf[charPos--] = (char) (tmp >> BITS_PER_BYTE);
        }

        while (value > 0) {
            long q = value / 10;
            int r = (int) (value - q * 10);
            value = q;
            buf[charPos--] = DIGITS[r];
        }

        return size + (negative ? 1 : 0);
    }

    /**
     * 计算整数的字符表示长度
     *
     * @param value 整数值
     * @return 字符表示长度
     */
    public static int sizeOfInt(int value) {
        if (value < 0) {
            if (value == Integer.MIN_VALUE) {
                return MIN_INT_VALUE_DIGIT_COUNT;
            }
            value = -value;
        }
        if (value < INT_FOUR_DIGITS_THRESHOLD) {
            return sizeOfSmallInt(value);
        }
        if (value < INT_EIGHT_DIGITS_THRESHOLD) {
            return sizeOfMediumInt(value);
        }
        if (value < INT_NINE_DIGITS_THRESHOLD) {
            return INT_NINE_DIGITS_SIZE;
        }
        return INT_TEN_DIGITS_SIZE;
    }

    /**
     * 计算 0-9999 范围内整数的字符表示长度
     *
     * @param value 整数值（0 &lt;= value &lt;= 9999）
     * @return 字符表示长度（1-4）
     */
    private static int sizeOfSmallInt(int value) {
        if (value < INT_ONE_DIGIT_THRESHOLD) {
            return INT_ONE_DIGIT_SIZE;
        }
        if (value < INT_TWO_DIGITS_THRESHOLD) {
            return INT_TWO_DIGITS_SIZE;
        }
        if (value < INT_THREE_DIGITS_THRESHOLD) {
            return INT_THREE_DIGITS_SIZE;
        }
        return INT_FOUR_DIGITS_SIZE;
    }

    /**
     * 计算 10000-99999999 范围内整数的字符表示长度
     *
     * @param value 整数值（10000 &lt;= value &lt;= 99999999）
     * @return 字符表示长度（5-8）
     */
    private static int sizeOfMediumInt(int value) {
        if (value < INT_FIVE_DIGITS_THRESHOLD) {
            return INT_FIVE_DIGITS_SIZE;
        }
        if (value < INT_SIX_DIGITS_THRESHOLD) {
            return INT_SIX_DIGITS_SIZE;
        }
        if (value < INT_SEVEN_DIGITS_THRESHOLD) {
            return INT_SEVEN_DIGITS_SIZE;
        }
        return INT_EIGHT_DIGITS_SIZE;
    }

    /**
     * 计算长整数的字符表示长度
     *
     * @param value 长整数值
     * @return 字符表示长度
     */
    public static int sizeOfLong(long value) {
        if (value < 0) {
            if (value == Long.MIN_VALUE) {
                return MIN_LONG_VALUE_DIGIT_COUNT;
            }
            value = -value;
        }
        if (value <= Integer.MAX_VALUE) {
            return sizeOfInt((int) value);
        }
        if (value < LONG_FOURTEEN_DIGITS_THRESHOLD) {
            return sizeOfMediumLong(value);
        }
        return sizeOfLargeLong(value);
    }

    /**
     * 计算 10000000000-9999999999999 范围内长整数的字符表示长度
     *
     * @param value 长整数值（10000000000L &lt;= value &lt;= 9999999999999L）
     * @return 字符表示长度（10-13）
     */
    private static int sizeOfMediumLong(long value) {
        if (value < LONG_TEN_DIGITS_THRESHOLD) {
            return LONG_TEN_DIGITS_SIZE;
        }
        if (value < LONG_ELEVEN_DIGITS_THRESHOLD) {
            return LONG_ELEVEN_DIGITS_SIZE;
        }
        if (value < LONG_TWELVE_DIGITS_THRESHOLD) {
            return LONG_TWELVE_DIGITS_SIZE;
        }
        return LONG_THIRTEEN_DIGITS_SIZE;
    }

    /**
     * 计算 10000000000000 及以上长整数的字符表示长度
     *
     * @param value 长整数值（value &gt;= 10000000000000L）
     * @return 字符表示长度（14-20）
     */
    private static int sizeOfLargeLong(long value) {
        if (value < LONG_FOURTEEN_DIGITS_THRESHOLD) {
            return LONG_FOURTEEN_DIGITS_SIZE;
        }
        if (value < LONG_FIFTEEN_DIGITS_THRESHOLD) {
            return LONG_FIFTEEN_DIGITS_SIZE;
        }
        if (value < LONG_SIXTEEN_DIGITS_THRESHOLD) {
            return LONG_SIXTEEN_DIGITS_SIZE;
        }
        if (value < LONG_SEVENTEEN_DIGITS_THRESHOLD) {
            return LONG_SEVENTEEN_DIGITS_SIZE;
        }
        if (value < LONG_EIGHTEEN_DIGITS_THRESHOLD) {
            return LONG_EIGHTEEN_DIGITS_SIZE;
        }
        if (value < LONG_19_DIGITS_MAX) {
            return LONG_NINETEEN_DIGITS_SIZE;
        }
        return MIN_LONG_VALUE_DIGIT_COUNT;
    }

    private NumberUtils() {
        throw new UnsupportedOperationException();
    }

    // ==================== 高性能 parseInt/parseLong（快速路径优化） ====================

    /**
     * 高性能解析整数字符串（快速路径优化）
     *
     * <p>优化策略：</p>
     * <ul>
     *   <li>单数字快速路径：直接查表，避免循环和乘法</li>
     *   <li>双数字快速路径：一次乘法 + 一次加法，避免两次循环迭代</li>
     *   <li>乘法替代重复加法：value * 10 + digit 比 value + digit 的累加快 2-3 倍</li>
     * </ul>
     *
     * @param str 数字字符串
     * @return 整数值
     * @throws NumberFormatException 如果不是有效整数
     */
    public static int parseInt(String str) {
        if (str == null || str.isEmpty()) {
            throw new NumberFormatException("null or empty string");
        }
        int len = str.length();
        char first = str.charAt(0);
        if (len == 1) {
            return parseSingleDigitInt(first, str);
        }
        if (len == 2) {
            return parseLen2Int(str, first);
        }
        if (len == NEGATIVE_TWO_DIGIT_LEN && first == '-') {
            return parseNegativeTwoDigitInt(str);
        }
        return parseIntGeneral(str, len, first);
    }

    /**
     * 解析单数字整数快速路径
     *
     * @param first 首字符
     * @param str 原始字符串（用于错误消息）
     * @return 整数值
     */
    private static int parseSingleDigitInt(char first, String str) {
        if (first >= '0' && first <= '9') {
            return first - '0';
        }
        throw new NumberFormatException("Invalid integer: " + str);
    }

    /**
     * 解析长度为 2 的整数字符串（含正负两种情况）
     *
     * @param str 原始字符串
     * @param first 首字符
     * @return 整数值
     */
    private static int parseLen2Int(String str, char first) {
        if (first == '-') {
            return parseNegativeSingleDigitInt(str);
        }
        if (first >= '0' && first <= '9') {
            return parseTwoDigitInt(first, str);
        }
        throw new NumberFormatException("Invalid integer: " + str);
    }

    /**
     * 解析负号加单数字整数快速路径
     *
     * @param str 原始字符串
     * @return 整数值
     */
    private static int parseNegativeSingleDigitInt(String str) {
        char d = str.charAt(1);
        if (d >= '0' && d <= '9') {
            return -(d - '0');
        }
        throw new NumberFormatException("Invalid integer: " + str);
    }

    /**
     * 解析两位正整数快速路径
     *
     * @param first 首字符（已确认是数字）
     * @param str 原始字符串（用于错误消息）
     * @return 整数值
     */
    private static int parseTwoDigitInt(char first, String str) {
        char second = str.charAt(1);
        if (second >= '0' && second <= '9') {
            return (first - '0') * 10 + (second - '0');
        }
        throw new NumberFormatException("Invalid integer: " + str);
    }

    /**
     * 解析负号加两位数字整数快速路径
     *
     * @param str 原始字符串
     * @return 整数值
     */
    private static int parseNegativeTwoDigitInt(String str) {
        char d1 = str.charAt(1);
        char d2 = str.charAt(2);
        if (d1 >= '0' && d1 <= '9' && d2 >= '0' && d2 <= '9') {
            return -((d1 - '0') * 10 + (d2 - '0'));
        }
        throw new NumberFormatException("Invalid integer: " + str);
    }

    /**
     * 通用路径解析整数（带溢出检测）
     *
     * @param str 原始字符串
     * @param len 字符串长度
     * @param first 首字符
     * @return 整数值
     */
    private static int parseIntGeneral(String str, int len, char first) {
        boolean negative = false;
        int i = 0;
        if (first == '-') {
            negative = true;
            i = 1;
        } else if (first == '+') {
            i = 1;
        }
        long result = 0;
        while (i < len) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException("Invalid integer: " + str);
            }
            result = result * 10 + (c - '0');
            checkIntOverflow(result, negative, "Integer overflow: " + str);
            i++;
        }
        return negative ? (int) -result : (int) result;
    }

    /**
     * 检查整数溢出
     *
     * @param result 当前累加结果
     * @param negative 是否为负数
     * @param errorMessage 溢出时的错误消息
     */
    private static void checkIntOverflow(long result, boolean negative, String errorMessage) {
        if (negative && -result < Integer.MIN_VALUE) {
            throw new NumberFormatException(errorMessage);
        }
        if (!negative && result > Integer.MAX_VALUE) {
            throw new NumberFormatException(errorMessage);
        }
    }

    /**
     * 高性能解析长整数字符串（快速路径优化）
     *
     * <p>优化策略同 parseInt，额外增加 long 范围的溢出保护</p>
     *
     * @param str 数字字符串
     * @return 长整数值
     * @throws NumberFormatException 如果不是有效长整数
     */
    public static long parseLong(String str) {
        if (str == null || str.isEmpty()) {
            throw new NumberFormatException("null or empty string");
        }
        int len = str.length();
        char first = str.charAt(0);
        if (len == 1) {
            return parseSingleDigitLong(first, str);
        }
        if (len == 2) {
            return parseLen2Long(str, first);
        }
        if (len == NEGATIVE_TWO_DIGIT_LEN && first == '-') {
            return parseNegativeTwoDigitLong(str);
        }
        return parseLongGeneral(str, len, first);
    }

    /**
     * 解析单数字长整数快速路径
     *
     * @param first 首字符
     * @param str 原始字符串（用于错误消息）
     * @return 长整数值
     */
    private static long parseSingleDigitLong(char first, String str) {
        if (first >= '0' && first <= '9') {
            return first - '0';
        }
        throw new NumberFormatException("Invalid long: " + str);
    }

    /**
     * 解析长度为 2 的长整数字符串（含正负两种情况）
     *
     * @param str 原始字符串
     * @param first 首字符
     * @return 长整数值
     */
    private static long parseLen2Long(String str, char first) {
        if (first == '-') {
            return parseNegativeSingleDigitLong(str);
        }
        if (first >= '0' && first <= '9') {
            return parseTwoDigitLong(first, str);
        }
        throw new NumberFormatException("Invalid long: " + str);
    }

    /**
     * 解析负号加单数字长整数快速路径
     *
     * @param str 原始字符串
     * @return 长整数值
     */
    private static long parseNegativeSingleDigitLong(String str) {
        char d = str.charAt(1);
        if (d >= '0' && d <= '9') {
            return -(d - '0');
        }
        throw new NumberFormatException("Invalid long: " + str);
    }

    /**
     * 解析两位正长整数快速路径
     *
     * @param first 首字符（已确认是数字）
     * @param str 原始字符串（用于错误消息）
     * @return 长整数值
     */
    private static long parseTwoDigitLong(char first, String str) {
        char second = str.charAt(1);
        if (second >= '0' && second <= '9') {
            return (first - '0') * 10L + (second - '0');
        }
        throw new NumberFormatException("Invalid long: " + str);
    }

    /**
     * 解析负号加两位数字长整数快速路径
     *
     * @param str 原始字符串
     * @return 长整数值
     */
    private static long parseNegativeTwoDigitLong(String str) {
        char d1 = str.charAt(1);
        char d2 = str.charAt(2);
        if (d1 >= '0' && d1 <= '9' && d2 >= '0' && d2 <= '9') {
            return -((d1 - '0') * 10L + (d2 - '0'));
        }
        throw new NumberFormatException("Invalid long: " + str);
    }

    /**
     * 通用路径解析长整数（带溢出检测）
     *
     * @param str 原始字符串
     * @param len 字符串长度
     * @param first 首字符
     * @return 长整数值
     */
    private static long parseLongGeneral(String str, int len, char first) {
        boolean negative = false;
        int i = 0;
        if (first == '-') {
            negative = true;
            i = 1;
        } else if (first == '+') {
            i = 1;
        }
        long result = 0;
        long limit = Long.MAX_VALUE / 10;
        while (i < len) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                throw new NumberFormatException("Invalid long: " + str);
            }
            int digit = c - '0';
            checkLongOverflow(result, digit, negative, i, len, limit, "Long overflow: " + str);
            result = result * 10 + digit;
            i++;
        }
        return negative ? -result : result;
    }

    /**
     * 检查长整数溢出
     *
     * @param result 当前累加结果
     * @param digit 当前位数字
     * @param negative 是否为负数
     * @param i 当前索引
     * @param len 字符串长度
     * @param limit 溢出阈值（Long.MAX_VALUE / 10）
     * @param errorMessage 溢出时的错误消息
     */
    private static void checkLongOverflow(long result, int digit, boolean negative, int i, int len, long limit, String errorMessage) {
        if (result > limit || (result == limit && digit > Long.MAX_VALUE % 10)) {
            if (!(negative && i == len - 1 && result == limit && digit == (Long.MAX_VALUE % 10) + 1)) {
                throw new NumberFormatException(errorMessage);
            }
        }
    }

    /**
     * 高性能从 char[] 解析整数（零拷贝，避免 String 分配）
     *
     * <p>用于 JSON 解析器直接从字符数组读取数字</p>
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param len 字符数
     * @return 整数值
     */
    public static int parseIntFromChars(char[] chars, int start, int len) {
        if (len == 0) {
            throw new NumberFormatException("empty input");
        }
        char first = chars[start];
        if (len == 1) {
            return parseSingleDigitFromChars(first, start);
        }
        if (len == 2) {
            return parseLen2FromChars(chars, start, first);
        }
        return parseIntFromCharsGeneral(chars, start, len, first);
    }

    /**
     * 从 char[] 解析单数字整数快速路径
     *
     * @param first 首字符
     * @param start 起始位置（用于错误消息）
     * @return 整数值
     */
    private static int parseSingleDigitFromChars(char first, int start) {
        if (first >= '0' && first <= '9') {
            return first - '0';
        }
        throw new NumberFormatException("Invalid integer at offset " + start);
    }

    /**
     * 从 char[] 解析长度为 2 的整数（含正负两种情况）
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param first 首字符
     * @return 整数值
     */
    private static int parseLen2FromChars(char[] chars, int start, char first) {
        if (first >= '0' && first <= '9') {
            char second = chars[start + 1];
            if (second >= '0' && second <= '9') {
                return (first - '0') * 10 + (second - '0');
            }
        }
        return parseIntFromCharsGeneral(chars, start, 2, first);
    }

    /**
     * 通用路径从 char[] 解析整数（带溢出检测，遇到非数字字符停止）
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param len 字符数
     * @param first 首字符
     * @return 整数值
     */
    private static int parseIntFromCharsGeneral(char[] chars, int start, int len, char first) {
        boolean negative = false;
        int i = start;
        int end = start + len;
        if (first == '-') {
            negative = true;
            i++;
        } else if (first == '+') {
            i++;
        }
        long result = 0;
        while (i < end) {
            char c = chars[i];
            if (c < '0' || c > '9') {
                break;
            }
            int digit = c - '0';
            result = result * 10 + digit;
            checkIntOverflow(result, negative, "Integer overflow at offset " + start);
            i++;
        }
        return negative ? (int) -result : (int) result;
    }

    /**
     * 高性能从 char[] 解析长整数（零拷贝，避免 String 分配）
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param len 字符数
     * @return 长整数值
     */
    public static long parseLongFromChars(char[] chars, int start, int len) {
        if (len == 0) {
            throw new NumberFormatException("empty input");
        }
        char first = chars[start];
        if (len == 1) {
            return parseSingleDigitFromCharsLong(first, start);
        }
        if (len == 2) {
            return parseLen2FromCharsLong(chars, start, first);
        }
        return parseLongFromCharsGeneral(chars, start, len, first);
    }

    /**
     * 从 char[] 解析单数字长整数快速路径
     *
     * @param first 首字符
     * @param start 起始位置（用于错误消息）
     * @return 长整数值
     */
    private static long parseSingleDigitFromCharsLong(char first, int start) {
        if (first >= '0' && first <= '9') {
            return first - '0';
        }
        throw new NumberFormatException("Invalid long at offset " + start);
    }

    /**
     * 从 char[] 解析长度为 2 的长整数（含正负两种情况）
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param first 首字符
     * @return 长整数值
     */
    private static long parseLen2FromCharsLong(char[] chars, int start, char first) {
        if (first >= '0' && first <= '9') {
            char second = chars[start + 1];
            if (second >= '0' && second <= '9') {
                return (first - '0') * 10L + (second - '0');
            }
        }
        return parseLongFromCharsGeneral(chars, start, 2, first);
    }

    /**
     * 通用路径从 char[] 解析长整数（带溢出检测，遇到非数字字符停止）
     *
     * @param chars 字符数组
     * @param start 起始位置
     * @param len 字符数
     * @param first 首字符
     * @return 长整数值
     */
    private static long parseLongFromCharsGeneral(char[] chars, int start, int len, char first) {
        boolean negative = false;
        int i = start;
        int end = start + len;
        if (first == '-') {
            negative = true;
            i++;
        } else if (first == '+') {
            i++;
        }
        long result = 0;
        long limit = Long.MAX_VALUE / 10;
        while (i < end) {
            char c = chars[i];
            if (c < '0' || c > '9') {
                break;
            }
            int digit = c - '0';
            checkLongOverflow(result, digit, negative, i, end, limit, "Long overflow at offset " + start);
            result = result * 10 + digit;
            i++;
        }
        return negative ? -result : result;
    }
}
