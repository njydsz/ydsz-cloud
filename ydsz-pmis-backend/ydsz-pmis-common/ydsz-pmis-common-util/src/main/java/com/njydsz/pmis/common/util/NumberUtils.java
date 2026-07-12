package com.njydsz.pmis.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 数字工具类
 *
 * <p>提供数字操作的工具方法，包括安全转换、比较、格式化等。
 * 对标 remi-comm NumberUtils。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class NumberUtils {

    private NumberUtils() {
    }

    /**
     * 安全转换为 int
     *
     * @param obj 对象
     * @return int 值，null 或异常返回 0
     */
    public static int toInt(Object obj) {
        return toInt(obj, 0);
    }

    /**
     * 安全转换为 int（带默认值）
     *
     * @param obj          对象
     * @param defaultValue 默认值
     * @return int 值
     */
    public static int toInt(Object obj, int defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        try {
            if (obj instanceof Number) {
                return ((Number) obj).intValue();
            }
            return Integer.parseInt(obj.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 安全转换为 long
     *
     * @param obj 对象
     * @return long 值
     */
    public static long toLong(Object obj) {
        return toLong(obj, 0L);
    }

    /**
     * 安全转换为 long（带默认值）
     *
     * @param obj          对象
     * @param defaultValue 默认值
     * @return long 值
     */
    public static long toLong(Object obj, long defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        try {
            if (obj instanceof Number) {
                return ((Number) obj).longValue();
            }
            return Long.parseLong(obj.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 安全转换为 double
     *
     * @param obj 对象
     * @return double 值
     */
    public static double toDouble(Object obj) {
        return toDouble(obj, 0.0);
    }

    /**
     * 安全转换为 double（带默认值）
     *
     * @param obj          对象
     * @param defaultValue 默认值
     * @return double 值
     */
    public static double toDouble(Object obj, double defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        try {
            if (obj instanceof Number) {
                return ((Number) obj).doubleValue();
            }
            return Double.parseDouble(obj.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 安全转换为 BigDecimal
     *
     * @param obj 对象
     * @return BigDecimal 值，null 返回 BigDecimal.ZERO
     */
    public static BigDecimal toBigDecimal(Object obj) {
        if (obj == null) {
            return BigDecimal.ZERO;
        }
        if (obj instanceof BigDecimal) {
            return (BigDecimal) obj;
        }
        try {
            return new BigDecimal(obj.toString().trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 格式化 BigDecimal 到指定小数位
     *
     * @param value 值
     * @param scale 小数位
     * @return 格式化后的 BigDecimal
     */
    public static BigDecimal format(BigDecimal value, int scale) {
        return format(value, scale, RoundingMode.HALF_UP);
    }

    /**
     * 格式化 BigDecimal 到指定小数位
     *
     * @param value        值
     * @param scale        小数位
     * @param roundingMode 舍入模式
     * @return 格式化后的 BigDecimal
     */
    public static BigDecimal format(BigDecimal value, int scale, RoundingMode roundingMode) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(scale, roundingMode);
        }
        return value.setScale(scale, roundingMode);
    }

    /**
     * 判断是否为正数
     *
     * @param value 值
     * @return true 如果值大于 0
     */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 判断是否为非负数
     *
     * @param value 值
     * @return true 如果值大于等于 0
     */
    public static boolean isNonNegative(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) >= 0;
    }

    /**
     * 判断两个 BigDecimal 是否相等
     *
     * @param a 值 a
     * @param b 值 b
     * @return true 如果值相等
     */
    public static boolean equals(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.compareTo(b) == 0;
    }

    /**
     * 生成指定范围的随机整数
     *
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @return 随机整数
     */
    public static int random(int min, int max) {
        if (min > max) {
            int temp = min;
            min = max;
            max = temp;
        }
        return min + (int) (Math.random() * (max - min + 1));
    }
}
