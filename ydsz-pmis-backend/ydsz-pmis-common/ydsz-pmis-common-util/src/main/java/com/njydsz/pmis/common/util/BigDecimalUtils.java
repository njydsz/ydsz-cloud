package com.njydsz.pmis.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BigDecimal 工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class BigDecimalUtils {

    private BigDecimalUtils() {
    }

    /** 默认精度 */
    private static final int DEFAULT_SCALE = 2;

    /**
     * 加法
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.add(b);
    }

    /**
     * 减法
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.subtract(b);
    }

    /**
     * 乘法
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return BigDecimal.ZERO;
        return a.multiply(b);
    }

    /**
     * 除法（四舍五入，默认2位小数）
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        return divide(a, b, DEFAULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 除法
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b, int scale, RoundingMode mode) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null || b.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return a.divide(b, scale, mode);
    }

    /**
     * 四舍五入到2位小数
     */
    public static BigDecimal round(BigDecimal value) {
        return round(value, DEFAULT_SCALE);
    }

    /**
     * 四舍五入到指定位数
     */
    public static BigDecimal round(BigDecimal value, int scale) {
        if (value == null) return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        return value.setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * 百分比计算
     */
    public static BigDecimal percentage(BigDecimal value, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return divide(value, total, 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(DEFAULT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 比较大小（a > b）
     */
    public static boolean greaterThan(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.compareTo(b) > 0;
    }

    /**
     * 比较大小（a >= b）
     */
    public static boolean greaterOrEqual(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.compareTo(b) >= 0;
    }

    /**
     * 比较大小（a < b）
     */
    public static boolean lessThan(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.compareTo(b) < 0;
    }

    /**
     * 比较大小（a <= b）
     */
    public static boolean lessOrEqual(BigDecimal a, BigDecimal b) {
        if (a == null) a = BigDecimal.ZERO;
        if (b == null) b = BigDecimal.ZERO;
        return a.compareTo(b) <= 0;
    }

    /**
     * 是否为零
     */
    public static boolean isZero(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 是否为正数
     */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 是否为负数
     */
    public static boolean isNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) < 0;
    }
}
