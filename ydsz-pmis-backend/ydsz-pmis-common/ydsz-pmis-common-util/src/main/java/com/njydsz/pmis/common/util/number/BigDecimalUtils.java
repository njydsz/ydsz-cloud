package com.njydsz.pmis.common.util.number;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * BigDecimal 高精度计算工具类
 * <p>
 * 参考阿里巴巴、Google 等大厂最佳实践，提供：
 * 1. 安全的数值创建方式（避免 double 精度丢失）
 * 2. 完整的四则运算（空值安全）
 * 3. 精确的比较方法（使用 compareTo）
 * 4. 丰富的统计计算（最大值、最小值、平均值、中位数等）
 * 5. 格式化与精度控制
 * 6. 百分比计算
 * 7. 批量处理与 Stream 支持
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class BigDecimalUtils {

    private static final int DEFAULT_SCALE = 2;

    private static final RoundingMode DEFAULT_ROUNDING_MODE = RoundingMode.HALF_UP;

    private static final BigDecimal BIG_DECIMAL_ZERO = BigDecimal.ZERO;

    private static final BigDecimal BIG_DECIMAL_ONE = BigDecimal.ONE;

    private static final BigDecimal BIG_DECIMAL_HUNDRED = new BigDecimal("100");

    private BigDecimalUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * 获取零值常量
     *
     * @return BigDecimal.ZERO
     */
    public static BigDecimal zero() {
        return BIG_DECIMAL_ZERO;
    }

    /**
     * 获取壹值常量
     *
     * @return BigDecimal.ONE
     */
    public static BigDecimal one() {
        return BIG_DECIMAL_ONE;
    }

    /**
     * 获取佰值常量
     *
     * @return BigDecimal(100)
     */
    public static BigDecimal hundred() {
        return BIG_DECIMAL_HUNDRED;
    }

    /**
     * 安全的字符串转 BigDecimal
     * <p>
     * 避免使用 new BigDecimal(double) 导致的精度丢失问题
     * 空字符串或 null 返回零值
     *
     * @param value 字符串数值
     * @return 转换后的 BigDecimal，null 或空字符串返回零值
     */
    public static BigDecimal valueOf(String value) {
        return value == null || value.trim().isEmpty() ? BIG_DECIMAL_ZERO : new BigDecimal(value.trim());
    }

    /**
     * long 转 BigDecimal
     *
     * @param value long 值
     * @return BigDecimal.valueOf(value)
     */
    public static BigDecimal valueOf(long value) {
        return BigDecimal.valueOf(value);
    }

    /**
     * double 转 BigDecimal（内部使用 String 转换，避免精度丢失）
     *
     * @param value double 值
     * @return BigDecimal.valueOf(value)
     */
    public static BigDecimal valueOf(double value) {
        return BigDecimal.valueOf(value);
    }

    /**
     * 加法运算（空值视为 0）
     *
     * @param v1 加数 1
     * @param v2 加数 2
     * @return v1 + v2
     */
    public static BigDecimal add(BigDecimal v1, BigDecimal v2) {
        return (v1 == null ? BIG_DECIMAL_ZERO : v1).add(v2 == null ? BIG_DECIMAL_ZERO : v2);
    }

    /**
     * 加法运算并设置精度
     *
     * @param v1 加数 1
     * @param v2 加数 2
     * @param scale 保留小数位数
     * @param roundingMode 舍入模式
     * @return v1 + v2，保留指定精度
     */
    public static BigDecimal add(BigDecimal v1, BigDecimal v2, int scale, RoundingMode roundingMode) {
        return add(v1, v2).setScale(scale, roundingMode);
    }

    /**
     * 可变参数累加
     *
     * @param values 可变参数数组
     * @return 所有非 null 值的和
     */
    public static BigDecimal addAll(BigDecimal... values) {
        if (values == null || values.length == 0) {
            return BIG_DECIMAL_ZERO;
        }
        return Arrays.stream(values)
                .filter(v -> v != null)
                .reduce(BIG_DECIMAL_ZERO, BigDecimal::add);
    }

    /**
     * 减法运算（空值视为 0）
     *
     * @param v1 被减数
     * @param v2 减数
     * @return v1 - v2
     */
    public static BigDecimal subtract(BigDecimal v1, BigDecimal v2) {
        return (v1 == null ? BIG_DECIMAL_ZERO : v1).subtract(v2 == null ? BIG_DECIMAL_ZERO : v2);
    }

    /**
     * 减法运算并设置精度
     *
     * @param v1 被减数
     * @param v2 减数
     * @param scale 保留小数位数
     * @param roundingMode 舍入模式
     * @return v1 - v2，保留指定精度
     */
    public static BigDecimal subtract(BigDecimal v1, BigDecimal v2, int scale, RoundingMode roundingMode) {
        return subtract(v1, v2).setScale(scale, roundingMode);
    }

    /**
     * 乘法运算（空值返回 0）
     *
     * @param v1 被乘数
     * @param v2 乘数
     * @return v1 * v2
     */
    public static BigDecimal multiply(BigDecimal v1, BigDecimal v2) {
        if (v1 == null || v2 == null) {
            return BIG_DECIMAL_ZERO;
        }
        return v1.multiply(v2);
    }

    /**
     * 乘法运算并设置精度
     *
     * @param v1 被乘数
     * @param v2 乘数
     * @param scale 保留小数位数
     * @param roundingMode 舍入模式
     * @return v1 * v2，保留指定精度
     */
    public static BigDecimal multiply(BigDecimal v1, BigDecimal v2, int scale, RoundingMode roundingMode) {
        if (v1 == null || v2 == null) {
            return BIG_DECIMAL_ZERO;
        }
        return v1.multiply(v2).setScale(scale, roundingMode);
    }

    /**
     * 除法运算（默认精度 2 位，四舍五入）
     * <p>
     * 空值或除数为零时返回零值
     *
     * @param v1 被除数
     * @param v2 除数
     * @return v1 / v2，保留 2 位小数
     */
    public static BigDecimal divide(BigDecimal v1, BigDecimal v2) {
        if (v1 == null || v2 == null || v2.compareTo(BIG_DECIMAL_ZERO) == 0) {
            return BIG_DECIMAL_ZERO;
        }
        return v1.divide(v2, DEFAULT_SCALE, DEFAULT_ROUNDING_MODE);
    }

    /**
     * 除法运算（指定精度和舍入模式）
     * <p>
     * 空值或除数为零时返回零值
     *
     * @param v1 被除数
     * @param v2 除数
     * @param scale 保留小数位数
     * @param roundingMode 舍入模式
     * @return v1 / v2，保留指定精度
     */
    public static BigDecimal divide(BigDecimal v1, BigDecimal v2, int scale, RoundingMode roundingMode) {
        if (v1 == null || v2 == null || v2.compareTo(BIG_DECIMAL_ZERO) == 0) {
            return BIG_DECIMAL_ZERO;
        }
        return v1.divide(v2, scale, roundingMode);
    }

    /**
     * 除法运算（使用 MathContext 控制精度）
     * <p>
     * 空值或除数为零时返回零值
     *
     * @param v1 被除数
     * @param v2 除数
     * @param mathContext 数学上下文（精度和舍入模式）
     * @return v1 / v2
     */
    public static BigDecimal divide(BigDecimal v1, BigDecimal v2, MathContext mathContext) {
        if (v1 == null || v2 == null || v2.compareTo(BIG_DECIMAL_ZERO) == 0) {
            return BIG_DECIMAL_ZERO;
        }
        return v1.divide(v2, mathContext);
    }

    /**
     * 取模运算（空值或除数为零返回 0）
     *
     * @param v1 被除数
     * @param v2 除数
     * @return v1 % v2
     */
    public static BigDecimal remainder(BigDecimal v1, BigDecimal v2) {
        if (v1 == null || v2 == null || v2.compareTo(BIG_DECIMAL_ZERO) == 0) {
            return BIG_DECIMAL_ZERO;
        }
        return v1.remainder(v2);
    }

    /**
     * 取模运算并设置精度
     *
     * @param v1 被除数
     * @param v2 除数
     * @param scale 保留小数位数
     * @param roundingMode 舍入模式
     * @return v1 % v2，保留指定精度
     */
    public static BigDecimal remainder(BigDecimal v1, BigDecimal v2, int scale, RoundingMode roundingMode) {
        if (v1 == null || v2 == null || v2.compareTo(BIG_DECIMAL_ZERO) == 0) {
            return BIG_DECIMAL_ZERO;
        }
        return v1.remainder(v2).setScale(scale, roundingMode);
    }

    /**
     * 计算中位数（保持原列表不变）
     * <p>
     * 如果列表元素个数为奇数，返回中间值；
     * 如果为偶数，返回中间两个数的平均值（保留 3 位小数）
     *
     * @param list BigDecimal 列表
     * @return 中位数，空列表返回 null
     */
    public static BigDecimal median(List<BigDecimal> list) {
        return Optional.ofNullable(list)
                .filter(Predicate.not(List::isEmpty))
                .map(l -> {
                    List<BigDecimal> sorted = l.stream()
                            .filter(v -> v != null)
                            .sorted()
                            .collect(Collectors.toList());
                    if (sorted.isEmpty()) {
                        return null;
                    }
                    int size = sorted.size();
                    if (size % 2 == 1) {
                        return sorted.get(size / 2);
                    }
                    return sorted.get(size / 2 - 1)
                            .add(sorted.get(size / 2))
                            .divide(BIG_DECIMAL_TWO, 3, RoundingMode.HALF_UP);
                })
                .orElse(null);
    }

    private static final BigDecimal BIG_DECIMAL_TWO = new BigDecimal("2");

    /**
     * 升序排序（返回新列表）
     *
     * @param list 原始列表
     * @return 升序排序后的新列表，null 返回 null
     */
    public static List<BigDecimal> sort(List<BigDecimal> list) {
        return Optional.ofNullable(list)
                .map(l -> l.stream()
                        .filter(v -> v != null)
                        .sorted()
                        .collect(Collectors.toList()))
                .orElse(null);
    }

    /**
     * 降序排序（返回新列表）
     *
     * @param list 原始列表
     * @return 降序排序后的新列表，null 返回 null
     */
    public static List<BigDecimal> sortDescending(List<BigDecimal> list) {
        return Optional.ofNullable(list)
                .map(l -> l.stream()
                        .filter(v -> v != null)
                        .sorted(Collections.reverseOrder())
                        .collect(Collectors.toList()))
                .orElse(null);
    }

    /**
     * 计算列表最大值
     *
     * @param list BigDecimal 列表
     * @return 最大值，空列表返回 null
     */
    public static BigDecimal max(List<BigDecimal> list) {
        return Optional.ofNullable(list)
                .filter(l -> !l.isEmpty())
                .map(Collections::max)
                .orElse(null);
    }

    /**
     * 计算可变参数最大值
     *
     * @param values 可变参数数组
     * @return 最大值，空数组返回 null
     */
    public static BigDecimal max(BigDecimal... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        return Arrays.stream(values)
                .filter(v -> v != null)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    /**
     * 计算列表最小值
     *
     * @param list BigDecimal 列表
     * @return 最小值，空列表返回 null
     */
    public static BigDecimal min(List<BigDecimal> list) {
        return Optional.ofNullable(list)
                .filter(l -> !l.isEmpty())
                .map(Collections::min)
                .orElse(null);
    }

    /**
     * 计算可变参数最小值
     *
     * @param values 可变参数数组
     * @return 最小值，空数组返回 null
     */
    public static BigDecimal min(BigDecimal... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        return Arrays.stream(values)
                .filter(v -> v != null)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    /**
     * 计算平均值（默认精度 2 位，四舍五入）
     *
     * @param list BigDecimal 列表
     * @return 平均值，空列表返回 null
     */
    public static BigDecimal avg(List<BigDecimal> list) {
        return Optional.ofNullable(list)
                .filter(l -> !l.isEmpty())
                .map(l -> l.stream()
                        .filter(v -> v != null)
                        .reduce(BIG_DECIMAL_ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(l.stream().filter(v -> v != null).count()), DEFAULT_SCALE, DEFAULT_ROUNDING_MODE))
                .orElse(null);
    }

    /**
     * 计算平均值（指定精度和舍入模式）
     *
     * @param scale 保留小数位数
     * @param roundingMode 舍入模式
     * @param list BigDecimal 列表
     * @return 平均值，空列表返回 null
     */
    public static BigDecimal avg(int scale, RoundingMode roundingMode, List<BigDecimal> list) {
        return Optional.ofNullable(list)
                .filter(l -> !l.isEmpty())
                .map(l -> l.stream()
                        .filter(v -> v != null)
                        .reduce(BIG_DECIMAL_ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(l.stream().filter(v -> v != null).count()), scale, roundingMode))
                .orElse(null);
    }

    /**
     * 计算列表总和
     *
     * @param list BigDecimal 列表
     * @return 总和，空列表返回零值
     */
    public static BigDecimal sum(List<BigDecimal> list) {
        return Optional.ofNullable(list)
                .map(l -> l.stream()
                        .filter(v -> v != null)
                        .reduce(BIG_DECIMAL_ZERO, BigDecimal::add))
                .orElse(BIG_DECIMAL_ZERO);
    }

    /**
     * 计算对象列表的 BigDecimal 属性总和
     *
     * @param list 对象列表
     * @param mapper 提取 BigDecimal 属性的函数
     * @param <T> 对象类型
     * @return 总和，空列表返回零值
     */
    public static <T> BigDecimal sum(List<T> list, Function<? super T, BigDecimal> mapper) {
        if (list == null || list.isEmpty()) {
            return BIG_DECIMAL_ZERO;
        }
        return list.stream()
                .map(mapper)
                .filter(v -> v != null)
                .reduce(BIG_DECIMAL_ZERO, BigDecimal::add);
    }

    /**
     * 计算占比百分比（默认精度 2 位）
     * <p>
     * 公式：(part / total) * 100
     *
     * @param part 部分值
     * @param total 总值
     * @return 百分比值，null 或除数为零返回零值
     */
    public static BigDecimal percentage(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.compareTo(BIG_DECIMAL_ZERO) == 0) {
            return BIG_DECIMAL_ZERO;
        }
        return part.multiply(BIG_DECIMAL_HUNDRED).divide(total, DEFAULT_SCALE, DEFAULT_ROUNDING_MODE);
    }

    /**
     * 计算占比百分比（指定精度和舍入模式）
     * <p>
     * 公式：(part / total) * 100
     *
     * @param part 部分值
     * @param total 总值
     * @param scale 保留小数位数
     * @param roundingMode 舍入模式
     * @return 百分比值，null 或除数为零返回零值
     */
    public static BigDecimal percentage(BigDecimal part, BigDecimal total, int scale, RoundingMode roundingMode) {
        if (part == null || total == null || total.compareTo(BIG_DECIMAL_ZERO) == 0) {
            return BIG_DECIMAL_ZERO;
        }
        return part.multiply(BIG_DECIMAL_HUNDRED).divide(total, scale, roundingMode);
    }

    /**
     * 计算百分比值（默认精度 2 位）
     * <p>
     * 公式：value * (percentage / 100)
     *
     * @param value 原值
     * @param percentage 百分比（如 15 表示 15%）
     * @return 百分比对应的值，null 返回零值
     */
    public static BigDecimal percentageOf(BigDecimal value, BigDecimal percentage) {
        if (value == null || percentage == null) {
            return BIG_DECIMAL_ZERO;
        }
        return value.multiply(percentage).divide(BIG_DECIMAL_HUNDRED, DEFAULT_SCALE, DEFAULT_ROUNDING_MODE);
    }

    /**
     * 计算百分比值（指定精度和舍入模式）
     * <p>
     * 公式：value * (percentage / 100)
     *
     * @param value 原值
     * @param percentage 百分比（如 15 表示 15%）
     * @param scale 保留小数位数
     * @param roundingMode 舍入模式
     * @return 百分比对应的值，null 返回零值
     */
    public static BigDecimal percentageOf(BigDecimal value, BigDecimal percentage, int scale, RoundingMode roundingMode) {
        if (value == null || percentage == null) {
            return BIG_DECIMAL_ZERO;
        }
        return value.multiply(percentage).divide(BIG_DECIMAL_HUNDRED, scale, roundingMode);
    }

    /**
     * 比较大小（大于）
     * <p>
     * 使用 compareTo 而非 equals，避免精度陷阱
     *
     * @param v1 值 1
     * @param v2 值 2
     * @return v1 > v2
     */
    public static boolean gt(BigDecimal v1, BigDecimal v2) {
        return v1 != null && v2 != null && v1.compareTo(v2) > 0;
    }

    /**
     * 比较大小（大于等于）
     *
     * @param v1 值 1
     * @param v2 值 2
     * @return v1 >= v2
     */
    public static boolean ge(BigDecimal v1, BigDecimal v2) {
        return v1 != null && v2 != null && v1.compareTo(v2) >= 0;
    }

    /**
     * 比较大小（小于）
     *
     * @param v1 值 1
     * @param v2 值 2
     * @return v1 < v2
     */
    public static boolean lt(BigDecimal v1, BigDecimal v2) {
        return v1 != null && v2 != null && v1.compareTo(v2) < 0;
    }

    /**
     * 比较大小（小于等于）
     *
     * @param v1 值 1
     * @param v2 值 2
     * @return v1 <= v2
     */
    public static boolean le(BigDecimal v1, BigDecimal v2) {
        return v1 != null && v2 != null && v1.compareTo(v2) <= 0;
    }

    /**
     * 比较相等（使用 compareTo，忽略精度差异）
     * <p>
     * new BigDecimal("1.0").eq(new BigDecimal("1")) 返回 true
     *
     * @param v1 值 1
     * @param v2 值 2
     * @return v1 == v2（数值相等）
     */
    public static boolean eq(BigDecimal v1, BigDecimal v2) {
        return (v1 == null && v2 == null) || (v1 != null && v2 != null && v1.compareTo(v2) == 0);
    }

    /**
     * 比较不相等
     *
     * @param v1 值 1
     * @param v2 值 2
     * @return v1 != v2
     */
    public static boolean ne(BigDecimal v1, BigDecimal v2) {
        return !eq(v1, v2);
    }

    /**
     * 判断是否在区间内（闭区间）
     *
     * @param value 待判断值
     * @param min 最小值
     * @param max 最大值
     * @return min <= value <= max
     */
    public static boolean between(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null || min == null || max == null) {
            return false;
        }
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    /**
     * 判断是否在区间内（开区间）
     *
     * @param value 待判断值
     * @param min 最小值
     * @param max 最大值
     * @return min < value < max
     */
    public static boolean betweenExclusive(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null || min == null || max == null) {
            return false;
        }
        return value.compareTo(min) > 0 && value.compareTo(max) < 0;
    }

    /**
     * 空值安全的比较方法
     * <p>
     * null 被视为小于任何非 null 值
     *
     * @param v1 值 1
     * @param v2 值 2
     * @return v1.compareTo(v2)，null 视为最小值
     */
    public static int compareTo(BigDecimal v1, BigDecimal v2) {
        if (v1 == null && v2 == null) {
            return 0;
        }
        if (v1 == null) {
            return -1;
        }
        if (v2 == null) {
            return 1;
        }
        return v1.compareTo(v2);
    }

    /**
     * 取绝对值
     *
     * @param value 原值
     * @return |value|，null 返回零值
     */
    public static BigDecimal abs(BigDecimal value) {
        return value == null ? BIG_DECIMAL_ZERO : value.abs();
    }

    /**
     * 取相反数
     *
     * @param value 原值
     * @return -value，null 返回零值
     */
    public static BigDecimal negate(BigDecimal value) {
        return value == null ? BIG_DECIMAL_ZERO : value.negate();
    }

    /**
     * 幂运算（指数次方）
     *
     * @param base 底数
     * @param exponent 指数
     * @return base^exponent，null 返回零值
     */
    public static BigDecimal pow(BigDecimal base, int exponent) {
        return base == null ? BIG_DECIMAL_ZERO : base.pow(exponent);
    }

    /**
     * 平方根运算
     *
     * @param value 被开方数
     * @param scale 保留小数位数
     * @return √value，null 或负数返回零值
     */
    public static BigDecimal sqrt(BigDecimal value, int scale) {
        if (value == null || value.compareTo(BIG_DECIMAL_ZERO) < 0) {
            return BIG_DECIMAL_ZERO;
        }
        if (value.compareTo(BIG_DECIMAL_ZERO) == 0) {
            return BIG_DECIMAL_ZERO;
        }
        return value.sqrt(new MathContext(scale, DEFAULT_ROUNDING_MODE));
    }

    /**
     * 设置精度（默认四舍五入）
     *
     * @param value 原值
     * @param newScale 新精度
     * @return 设置精度后的值，null 返回零值
     */
    public static BigDecimal scale(BigDecimal value, int newScale) {
        return value == null ? BIG_DECIMAL_ZERO : value.setScale(newScale, DEFAULT_ROUNDING_MODE);
    }

    /**
     * 设置精度（指定舍入模式）
     *
     * @param value 原值
     * @param newScale 新精度
     * @param roundingMode 舍入模式
     * @return 设置精度后的值，null 返回零值
     */
    public static BigDecimal scale(BigDecimal value, int newScale, RoundingMode roundingMode) {
        return value == null ? BIG_DECIMAL_ZERO : value.setScale(newScale, roundingMode);
    }

    /**
     * 去除尾部零（标准化）
     * <p>
     * 例如：1.200 -> 1.2
     *
     * @param value 原值
     * @return 去除尾部零后的值，null 返回零值
     */
    public static BigDecimal stripTrailingZeros(BigDecimal value) {
        return value == null ? BIG_DECIMAL_ZERO : value.stripTrailingZeros();
    }

    /**
     * 标准化（去除尾部零）
     * <p>
     * 用于集合去重场景，确保 1.0 和 1 被视为相同
     *
     * @param value 原值
     * @return 标准化后的值，null 返回零值
     */
    public static BigDecimal normalize(BigDecimal value) {
        return value == null ? BIG_DECIMAL_ZERO : value.stripTrailingZeros();
    }

    /**
     * 转为普通字符串（不使用科学计数法）
     *
     * @param value BigDecimal 值
     * @return 普通十进制字符串，null 返回 "0"
     */
    public static String toPlainString(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    /**
     * 转为工程计数法字符串
     * <p>
     * 例如：1000 -> 1E+3
     *
     * @param value BigDecimal 值
     * @return 工程计数法字符串，null 返回 "0"
     */
    public static String toEngineeringString(BigDecimal value) {
        return value == null ? "0" : value.toEngineeringString();
    }

    /**
     * 转为字符串
     *
     * @param value BigDecimal 值
     * @return 字符串，null 返回 "0"
     */
    public static String toString(BigDecimal value) {
        return value == null ? "0" : value.toString();
    }

    /**
     * 获取符号值
     *
     * @param value BigDecimal 值
     * @return -1（负数），0（零），1（正数），null 返回 0
     */
    public static int signum(BigDecimal value) {
        return value == null ? 0 : value.signum();
    }

    /**
     * 判断是否为正数
     *
     * @param value BigDecimal 值
     * @return value > 0
     */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /**
     * 判断是否为负数
     *
     * @param value BigDecimal 值
     * @return value < 0
     */
    public static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    /**
     * 判断是否为零（null 也视为零）
     *
     * @param value BigDecimal 值
     * @return value == 0 或 value == null
     */
    public static boolean isZero(BigDecimal value) {
        return value == null || value.signum() == 0;
    }

    /**
     * 判断是否非空且为正数
     *
     * @param value BigDecimal 值
     * @return value != null 且 value > 0
     */
    public static boolean isNotNullAndPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /**
     * 确保值为正数，否则返回默认值
     *
     * @param value 原值
     * @param defaultValue 默认值
     * @return 正数值或默认值
     */
    public static BigDecimal ensurePositive(BigDecimal value, BigDecimal defaultValue) {
        return (value != null && value.signum() > 0) ? value : defaultValue;
    }

    /**
     * 确保值不为 null（null 返回零值）
     *
     * @param value 原值
     * @return 非 null 值
     */
    public static BigDecimal ensureNotNull(BigDecimal value) {
        return value == null ? BIG_DECIMAL_ZERO : value;
    }

    /**
     * 确保值不为 null（null 返回指定默认值）
     *
     * @param value 原值
     * @param defaultValue 默认值
     * @return 非 null 值
     */
    public static BigDecimal ensureNotNull(BigDecimal value, BigDecimal defaultValue) {
        return value == null ? defaultValue : value;
    }

    /**
     * 取较小值（空值友好的 min）
     *
     * @param v1 值 1
     * @param v2 值 2
     * @return 较小值，都为 null 返回零值
     */
    public static BigDecimal minIfNull(BigDecimal v1, BigDecimal v2) {
        if (v1 == null) {
            return v2 == null ? BIG_DECIMAL_ZERO : v2;
        }
        if (v2 == null) {
            return v1;
        }
        return v1.min(v2);
    }

    /**
     * 取较大值（空值友好的 max）
     *
     * @param v1 值 1
     * @param v2 值 2
     * @return 较大值，都为 null 返回零值
     */
    public static BigDecimal maxIfNull(BigDecimal v1, BigDecimal v2) {
        if (v1 == null) {
            return v2 == null ? BIG_DECIMAL_ZERO : v2;
        }
        if (v2 == null) {
            return v1;
        }
        return v1.max(v2);
    }

    /**
     * 限制值在指定区间内
     *
     * @param value 原值
     * @param min 最小值
     * @param max 最大值
     * @return 限制后的值，null 返回零值
     */
    public static BigDecimal clamp(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) {
            return BIG_DECIMAL_ZERO;
        }
        if (min != null && value.compareTo(min) < 0) {
            return min;
        }
        if (max != null && value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    /**
     * 小数点左移 n 位
     *
     * @param value 原值
     * @param n 移动位数
     * @return 移动后的值，null 返回零值
     */
    public static BigDecimal movePointLeft(BigDecimal value, int n) {
        return value == null ? BIG_DECIMAL_ZERO : value.movePointLeft(n);
    }

    /**
     * 小数点右移 n 位
     *
     * @param value 原值
     * @param n 移动位数
     * @return 移动后的值，null 返回零值
     */
    public static BigDecimal movePointRight(BigDecimal value, int n) {
        return value == null ? BIG_DECIMAL_ZERO : value.movePointRight(n);
    }

    /**
     * 按 10 的幂缩放（科学计数法调整）
     *
     * @param value 原值
     * @param n 10 的幂次
     * @return 缩放后的值，null 返回零值
     */
    public static BigDecimal scaleByPowerOfTen(BigDecimal value, int n) {
        return value == null ? BIG_DECIMAL_ZERO : value.scaleByPowerOfTen(n);
    }
}
