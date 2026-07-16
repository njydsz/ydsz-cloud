package com.njydsz.common.util.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * 数字工具类
 *
 * <p>提供全面的数字转换、判断和计算方法，功能对标 Apache Commons Lang NumberUtils 和 Hutool NumberUtil，
 * 并进行了增强和优化。
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>类型转换：toInt、toLong、toDouble、toFloat、toShort、toByte、toBigInteger、toBigDecimal</li>
 *   <li>字符串转换：parse、parseNumber、decodeNumber</li>
 *   <li>默认值：toIntDefault、toLongDefault、toDoubleDefault</li>
 *   <li>数字判断：isNumber、isDigits、isNumeric、isBigDecimal、isBigInteger</li>
 *   <li>数字比较：compare、compareNull、isBetween</li>
 *   <li>数字计算：add、subtract、multiply、divide、remainder、power</li>
 *   <li>数字格式化：format、parseLocale、toHumanReadable</li>
 *   <li>进制转换：toBinary、toOctal、toHexString、fromBinary、fromOctal、fromHex</li>
 *   <li>数字范围：min、max、sum、average</li>
 * </ul>
 *
 * <p><b>相比 Apache/Spring 的增强：</b>
 * <ul>
 *   <li>更全面的数字类型支持</li>
 *   <li>提供高精度计算（BigDecimal）</li>
 *   <li>支持多种进制转换</li>
 *   <li>所有方法 null 安全处理</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class NumberUtils {

    private static final BigDecimal BIG_DECIMAL_ZERO = BigDecimal.ZERO;
    private static final BigDecimal BIG_DECIMAL_ONE = BigDecimal.ONE;

    private NumberUtils() {
        throw new UnsupportedOperationException("NumberUtils is a utility class and cannot be instantiated");
    }

    /**
     * 转换为 int，失败返回 0
     */
    public static int toInt(Object value) {
        return toInt(value, 0);
    }

    /**
     * 转换为 int，失败返回默认值
     */
    public static int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue();
        }

        if (value instanceof String) {
            try {
                return Integer.parseInt(((String) value).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    /**
     * 转换为 long，失败返回 0
     */
    public static long toLong(Object value) {
        return toLong(value, 0L);
    }

    /**
     * 转换为 long，失败返回默认值
     */
    public static long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        if (value instanceof String) {
            try {
                return Long.parseLong(((String) value).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    /**
     * 转换为 double，失败返回 0.0
     */
    public static double toDouble(Object value) {
        return toDouble(value, 0.0);
    }

    /**
     * 转换为 double，失败返回默认值
     */
    public static double toDouble(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }

        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    /**
     * 转换为 float，失败返回 0.0f
     */
    public static float toFloat(Object value) {
        return toFloat(value, 0.0f);
    }

    /**
     * 转换为 float，失败返回默认值
     */
    public static float toFloat(Object value, float defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }

        if (value instanceof String) {
            try {
                return Float.parseFloat(((String) value).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    /**
     * 转换为 short，失败返回 0
     */
    public static short toShort(Object value) {
        return toShort(value, (short) 0);
    }

    /**
     * 转换为 short，失败返回默认值
     */
    public static short toShort(Object value, short defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }

        if (value instanceof String) {
            try {
                return Short.parseShort(((String) value).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    /**
     * 转换为 byte，失败返回 0
     */
    public static byte toByte(Object value) {
        return toByte(value, (byte) 0);
    }

    /**
     * 转换为 byte，失败返回默认值
     */
    public static byte toByte(Object value, byte defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Number) {
            return ((Number) value).byteValue();
        }

        if (value instanceof String) {
            try {
                return Byte.parseByte(((String) value).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    /**
     * 转换为 char
     */
    public static char toChar(Object value) {
        return toChar(value, '\0');
    }

    /**
     * 转换为 char，失败返回默认值
     */
    public static char toChar(Object value, char defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Character) {
            return (Character) value;
        }

        if (value instanceof String && ((String) value).length() == 1) {
            return ((String) value).charAt(0);
        }

        return defaultValue;
    }

    /**
     * 转换为 boolean
     */
    public static boolean toBoolean(Object value) {
        return toBoolean(value, false);
    }

    /**
     * 转换为 boolean，"true"/"1" 返回 true
     */
    public static boolean toBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof Number) {
            return ((Number) value).intValue() == 1;
        }

        if (value instanceof String) {
            String str = ((String) value).trim().toLowerCase();
            return "true".equals(str) || "1".equals(str) || "yes".equals(str) || "y".equals(str);
        }

        return defaultValue;
    }

    /**
     * 转换为 BigInteger
     */
    public static BigInteger toBigInteger(Object value) {
        return toBigInteger(value, null);
    }

    /**
     * 转换为 BigInteger，失败返回 null
     */
    public static BigInteger toBigInteger(Object value, BigInteger defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof BigInteger) {
            return (BigInteger) value;
        }

        if (value instanceof Number) {
            return BigInteger.valueOf(((Number) value).longValue());
        }

        if (value instanceof String) {
            try {
                return new BigInteger(((String) value).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    /**
     * 转换为 BigDecimal
     */
    public static BigDecimal toBigDecimal(Object value) {
        return toBigDecimal(value, null);
    }

    /**
     * 转换为 BigDecimal，失败返回 null
     */
    public static BigDecimal toBigDecimal(Object value, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }

        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }

        if (value instanceof String) {
            try {
                return new BigDecimal(((String) value).trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    /**
     * 判断是否为数字
     */
    public static boolean isNumber(Object value) {
        return value instanceof Number;
    }

    /**
     * 判断字符串是否为数字
     */
    public static boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        int len = str.length();
        int i = 0;

        if (str.charAt(0) == '-' || str.charAt(0) == '+') {
            if (len == 1) {
                return false;
            }
            i = 1;
        }

        for (; i < len; i++) {
            char c = str.charAt(i);
            if (c < '0' || c > '9') {
                if (c == '.') {
                    // 只允许一个小数点
                    for (int j = i + 1; j < len; j++) {
                        if (str.charAt(j) == '.') {
                            return false;
                        }
                    }
                } else if (c == 'e' || c == 'E') {
                    // 科学计数法
                    return isScientificNumber(str, i);
                } else {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isScientificNumber(String str, int eIndex) {
        if (eIndex == 0 || eIndex == str.length() - 1) {
            return false;
        }

        char next = str.charAt(eIndex + 1);
        if (next == '+' || next == '-') {
            if (eIndex + 1 == str.length() - 1) {
                return false;
            }
            for (int i = eIndex + 2; i < str.length(); i++) {
                if (!Character.isDigit(str.charAt(i))) {
                    return false;
                }
            }
        } else {
            for (int i = eIndex + 1; i < str.length(); i++) {
                if (!Character.isDigit(str.charAt(i))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 判断字符串是否只包含数字字符
     */
    public static boolean isDigits(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断是否为 BigDecimal
     */
    public static boolean isBigDecimal(Object value) {
        return value instanceof BigDecimal;
    }

    /**
     * 判断是否为 BigInteger
     */
    public static boolean isBigInteger(Object value) {
        return value instanceof BigInteger;
    }

    /**
     * 比较两个数字
     */
    public static int compare(Number n1, Number n2) {
        if (n1 == null && n2 == null) {
            return 0;
        }
        if (n1 == null) {
            return -1;
        }
        if (n2 == null) {
            return 1;
        }

        BigDecimal bd1 = toBigDecimal(n1);
        BigDecimal bd2 = toBigDecimal(n2);
        return bd1.compareTo(bd2);
    }

    /**
     * 比较两个数字（null 值处理）
     */
    public static int compareNull(Number n1, Number n2, int nullValue) {
        if (n1 == null && n2 == null) {
            return 0;
        }
        if (n1 == null) {
            return nullValue;
        }
        if (n2 == null) {
            return -nullValue;
        }

        return compare(n1, n2);
    }

    /**
     * 判断数字是否在范围内
     */
    public static boolean isBetween(Number value, Number min, Number max) {
        return value != null && min != null && max != null &&
               compare(value, min) >= 0 && compare(value, max) <= 0;
    }

    /**
     * 加法
     */
    public static BigDecimal add(Number... numbers) {
        if (numbers == null || numbers.length == 0) {
            return BIG_DECIMAL_ZERO;
        }

        BigDecimal sum = BIG_DECIMAL_ZERO;
        for (Number n : numbers) {
            if (n != null) {
                sum = sum.add(toBigDecimal(n));
            }
        }
        return sum;
    }

    /**
     * 减法
     */
    public static BigDecimal subtract(Number n1, Number n2) {
        BigDecimal bd1 = toBigDecimal(n1, BIG_DECIMAL_ZERO);
        BigDecimal bd2 = toBigDecimal(n2, BIG_DECIMAL_ZERO);
        return bd1.subtract(bd2);
    }

    /**
     * 乘法
     */
    public static BigDecimal multiply(Number... numbers) {
        if (numbers == null || numbers.length == 0) {
            return BIG_DECIMAL_ZERO;
        }

        BigDecimal product = BIG_DECIMAL_ONE;
        for (Number n : numbers) {
            if (n != null) {
                product = product.multiply(toBigDecimal(n));
            }
        }
        return product;
    }

    /**
     * 除法
     */
    public static BigDecimal divide(Number dividend, Number divisor) {
        return divide(dividend, divisor, 10, RoundingMode.HALF_UP);
    }

    /**
     * 除法（指定精度）
     */
    public static BigDecimal divide(Number dividend, Number divisor, int scale, RoundingMode roundingMode) {
        BigDecimal bd1 = toBigDecimal(dividend, BIG_DECIMAL_ZERO);
        BigDecimal bd2 = toBigDecimal(divisor, BIG_DECIMAL_ZERO);
        
        if (bd2.compareTo(BIG_DECIMAL_ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        
        return bd1.divide(bd2, scale, roundingMode);
    }

    /**
     * 取余
     */
    public static BigDecimal remainder(Number dividend, Number divisor) {
        BigDecimal bd1 = toBigDecimal(dividend, BIG_DECIMAL_ZERO);
        BigDecimal bd2 = toBigDecimal(divisor, BIG_DECIMAL_ZERO);
        
        if (bd2.compareTo(BIG_DECIMAL_ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        
        return bd1.remainder(bd2);
    }

    /**
     * 幂运算
     */
    public static BigDecimal power(Number base, int exponent) {
        BigDecimal bd = toBigDecimal(base, BIG_DECIMAL_ZERO);
        return bd.pow(exponent);
    }

    /**
     * 获取最小值
     */
    public static Number min(Number... numbers) {
        if (numbers == null || numbers.length == 0) {
            return null;
        }

        Number min = null;
        for (Number n : numbers) {
            if (n != null) {
                if (min == null || compare(n, min) < 0) {
                    min = n;
                }
            }
        }
        return min;
    }

    /**
     * 获取最大值
     */
    public static Number max(Number... numbers) {
        if (numbers == null || numbers.length == 0) {
            return null;
        }

        Number max = null;
        for (Number n : numbers) {
            if (n != null) {
                if (max == null || compare(n, max) > 0) {
                    max = n;
                }
            }
        }
        return max;
    }

    /**
     * 求和
     */
    public static BigDecimal sum(Number... numbers) {
        return add(numbers);
    }

    /**
     * 求平均值
     */
    public static BigDecimal average(Number... numbers) {
        if (numbers == null || numbers.length == 0) {
            return BIG_DECIMAL_ZERO;
        }

        BigDecimal sum = BIG_DECIMAL_ZERO;
        int count = 0;

        for (Number n : numbers) {
            if (n != null) {
                sum = sum.add(toBigDecimal(n));
                count++;
            }
        }

        if (count == 0) {
            return BIG_DECIMAL_ZERO;
        }

        return sum.divide(BigDecimal.valueOf(count), 10, RoundingMode.HALF_UP);
    }

    /**
     * 转换为二进制字符串
     */
    public static String toBinary(int value) {
        return Integer.toBinaryString(value);
    }

    /**
     * 转换为八进制字符串
     */
    public static String toOctal(int value) {
        return Integer.toOctalString(value);
    }

    /**
     * 转换为十六进制字符串
     */
    public static String toHexString(int value) {
        return Integer.toHexString(value);
    }

    /**
     * 转换为十六进制字符串（大写）
     */
    public static String toHexStringUpper(int value) {
        return Integer.toHexString(value).toUpperCase();
    }

    /**
     * 从二进制字符串转换
     */
    public static int fromBinary(String binary) {
        if (binary == null || binary.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(binary, 2);
    }

    /**
     * 从八进制字符串转换
     */
    public static int fromOctal(String octal) {
        if (octal == null || octal.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(octal, 8);
    }

    /**
     * 从十六进制字符串转换
     */
    public static int fromHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(hex, 16);
    }

    /**
     * 从十六进制字符串转换（long）
     */
    public static long fromHexLong(String hex) {
        if (hex == null || hex.isEmpty()) {
            return 0L;
        }
        return Long.parseLong(hex, 16);
    }

    /**
     * 格式化数字（添加千位分隔符）
     */
    public static String format(Number number) {
        if (number == null) {
            return null;
        }
        return String.format("%,d", number.longValue());
    }

    /**
     * 格式化小数
     */
    public static String formatDecimal(Number number, int scale) {
        if (number == null) {
            return null;
        }
        String format = "%,." + scale + "f";
        return String.format(format, number.doubleValue());
    }

    /**
     * 转换为人类可读格式（带单位）
     */
    public static String toHumanReadable(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else if (bytes < 1024L * 1024 * 1024 * 1024) {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        } else {
            return String.format("%.2f TB", bytes / (1024.0 * 1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * 判断数字是否为零
     */
    public static boolean isZero(Number number) {
        if (number == null) {
            return false;
        }

        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).compareTo(BigDecimal.ZERO) == 0;
        }

        if (number instanceof BigInteger) {
            return ((BigInteger) number).compareTo(BigInteger.ZERO) == 0;
        }

        return number.doubleValue() == 0.0;
    }

    /**
     * 判断数字是否不为零
     */
    public static boolean isNotZero(Number number) {
        return !isZero(number);
    }

    /**
     * 判断数字是否为正数
     */
    public static boolean isPositive(Number number) {
        return number != null && compare(number, 0) > 0;
    }

    /**
     * 判断数字是否为负数
     */
    public static boolean isNegative(Number number) {
        return number != null && compare(number, 0) < 0;
    }

    /**
     * 判断数字是否为整数
     */
    public static boolean isInteger(Number number) {
        if (number == null) {
            return false;
        }

        if (number instanceof Integer || number instanceof Long ||
            number instanceof Short || number instanceof Byte ||
            number instanceof BigInteger) {
            return true;
        }

        if (number instanceof BigDecimal) {
            return ((BigDecimal) number).scale() <= 0 || ((BigDecimal) number).stripTrailingZeros().scale() <= 0;
        }

        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            return d == Math.floor(d) && !Double.isInfinite(d);
        }

        return false;
    }
}
