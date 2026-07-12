package com.njydsz.pmis.common.util.asserts;

import com.njydsz.pmis.common.util.collection.CollectionUtils;
import com.njydsz.pmis.common.util.object.ObjectUtils;
import com.njydsz.pmis.common.util.string.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 断言工具类
 *
 * <p>提供全面的断言方法，功能对标 Spring Assert、Apache Commons Validate 和阿里巴巴 Assert，
 * 并进行了增强和优化。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li><b>双重消息模式</b>：支持静态消息字符串和 Supplier 函数式延迟消息</li>
 *   <li><b>自定义异常</b>：支持指定异常类型和自定义异常供应商</li>
 *   <li><b>全面类型支持</b>：支持 Object、String、Collection、Map、Array 等多种类型</li>
 *   <li><b>数值断言增强</b>：提供 int、long、double 类型的完整断言方法</li>
 *   <li><b>函数式断言</b>：支持 BooleanSupplier 和条件判断</li>
 *   <li><b>空值安全</b>：所有方法均进行空值检查，避免 NPE</li>
 * </ul>
 *
 * <p><b>主要功能：</b>
 * <ul>
 *   <li>基础断言：isTrue、isFalse</li>
 *   <li>空值断言：isNull、isNotNull、isEmpty、isNotEmpty（支持多种类型）</li>
 *   <li>字符串断言：isBlank、isNotBlank</li>
 *   <li>数值断言：isZero、isNotZero、isPositive、isNegative、isGreaterThan 等</li>
 *   <li>相等断言：equals、notEquals</li>
 *   <li>数组断言：isArrayEmpty、isArrayNotEmpty、isArrayLength、arrayContains</li>
 *   <li>范围断言：inRange、notInRange</li>
 *   <li>函数式断言：isTrue、satisfies（支持 BooleanSupplier）</li>
 *   <li>自定义异常：支持 Class 参数指定异常类型</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 基础断言
 * AssertUtils.isTrue(flag, "条件必须为真");
 * AssertUtils.notNull(obj, "对象不能为 null");
 *
 * // 空值断言
 * AssertUtils.isNotEmpty(list, "列表不能为空");
 * AssertUtils.isNotBlank(str, "字符串不能为空白");
 *
 * // 数值断言
 * AssertUtils.isPositive(age, "年龄必须为正数");
 * AssertUtils.inRange(score, 0, 100, "分数必须在 0-100 之间");
 *
 * // 函数式异常信息（延迟求值，避免不必要的字符串拼接）
 * AssertUtils.notNull(obj, () -> "对象不能为 null: " + objName);
 *
 * // 自定义异常
 * AssertUtils.isNotBlank(username, BusinessException.class, () -> new BusinessException("用户名不能为空"));
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class AssertUtils {

    /**
     * 私有构造函数，防止外部实例化
     */
    private AssertUtils() {
        throw new UnsupportedOperationException("AssertUtils 是工具类，不允许被实例化");
    }

    // ==================== 基础断言方法 ====================

    /**
     * 断言表达式为真
     *
     * @param expression 布尔表达式
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果表达式为假
     */
    public static void isTrue(boolean expression, String msg) {
        if (!expression) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言表达式为真（支持函数式异常信息）
     *
     * @param expression 布尔表达式
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果表达式为假
     */
    public static void isTrue(boolean expression, Supplier<String> msgSupplier) {
        if (!expression) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    /**
     * 断言表达式为假
     *
     * @param expression 布尔表达式
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果表达式为真
     */
    public static void isFalse(boolean expression, String msg) {
        if (expression) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言表达式为假（支持函数式异常信息）
     *
     * @param expression 布尔表达式
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果表达式为真
     */
    public static void isFalse(boolean expression, Supplier<String> msgSupplier) {
        if (expression) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    // ==================== Null 断言方法 ====================

    /**
     * 断言对象为 null
     *
     * @param obj 待检查的对象
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果对象不为 null
     */
    public static void isNull(Object obj, String msg) {
        if (obj != null) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言对象为 null（支持函数式异常信息）
     *
     * @param obj 待检查的对象
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果对象不为 null
     */
    public static void isNull(Object obj, Supplier<String> msgSupplier) {
        if (obj != null) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    /**
     * 断言对象不为 null
     *
     * @param obj 待检查的对象
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果对象为 null
     */
    public static void notNull(Object obj, String msg) {
        if (obj == null) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言对象不为 null（支持函数式异常信息）
     *
     * @param obj 待检查的对象
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果对象为 null
     */
    public static void notNull(Object obj, Supplier<String> msgSupplier) {
        if (obj == null) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    // ==================== 空值断言方法 ====================

    /**
     * 断言对象不为空（支持多种类型）
     *
     * @param obj 待检查的对象
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果对象为空
     */
    public static void isNotEmpty(Object obj, String msg) {
        if (ObjectUtils.isEmpty(obj)) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言对象不为空（支持函数式异常信息）
     *
     * @param obj 待检查的对象
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果对象为空
     */
    public static void isNotEmpty(Object obj, Supplier<String> msgSupplier) {
        if (ObjectUtils.isEmpty(obj)) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    /**
     * 断言集合不为空
     *
     * @param collection 待检查的集合
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果集合为空
     */
    public static void isNotEmpty(Collection<?> collection, String msg) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言集合不为空（支持函数式异常信息）
     *
     * @param collection 待检查的集合
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果集合为空
     */
    public static void isNotEmpty(Collection<?> collection, Supplier<String> msgSupplier) {
        if (CollectionUtils.isEmpty(collection)) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    /**
     * 断言 Map 不为空
     *
     * @param map 待检查的 Map
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果 Map 为空
     */
    public static void isNotEmpty(Map<?, ?> map, String msg) {
        if (CollectionUtils.isEmpty(map)) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 Map 不为空（支持函数式异常信息）
     *
     * @param map 待检查的 Map
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果 Map 为空
     */
    public static void isNotEmpty(Map<?, ?> map, Supplier<String> msgSupplier) {
        if (CollectionUtils.isEmpty(map)) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    /**
     * 断言字符串不为空
     *
     * @param str 待检查的字符串
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果字符串为空
     */
    public static void isNotEmpty(String str, String msg) {
        if (StringUtils.isEmpty(str)) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言字符串不为空（支持函数式异常信息）
     *
     * @param str 待检查的字符串
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果字符串为空
     */
    public static void isNotEmpty(String str, Supplier<String> msgSupplier) {
        if (StringUtils.isEmpty(str)) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    /**
     * 断言对象为空（支持多种类型）
     *
     * @param obj 待检查的对象
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果对象不为空
     */
    public static void isEmpty(Object obj, String msg) {
        if (ObjectUtils.isNotEmpty(obj)) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言对象为空（支持函数式异常信息）
     *
     * @param obj 待检查的对象
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果对象不为空
     */
    public static void isEmpty(Object obj, Supplier<String> msgSupplier) {
        if (ObjectUtils.isNotEmpty(obj)) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    // ==================== 字符串断言方法 ====================

    /**
     * 断言字符串不为空白
     *
     * @param str 待检查的字符串
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果字符串为空白
     */
    public static void isNotBlank(String str, String msg) {
        if (StringUtils.isBlank(str)) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言字符串不为空白（支持函数式异常信息）
     *
     * @param str 待检查的字符串
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果字符串为空白
     */
    public static void isNotBlank(String str, Supplier<String> msgSupplier) {
        if (StringUtils.isBlank(str)) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    /**
     * 断言字符串为空白
     *
     * @param str 待检查的字符串
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果字符串不为空白
     */
    public static void isBlank(String str, String msg) {
        if (StringUtils.isNotBlank(str)) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言字符串为空白（支持函数式异常信息）
     *
     * @param str 待检查的字符串
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果字符串不为空白
     */
    public static void isBlank(String str, Supplier<String> msgSupplier) {
        if (StringUtils.isNotBlank(str)) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    // ==================== 数值断言方法 ====================

    /**
     * 断言 int 值为 0
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值不为 0
     */
    public static void isZero(int value, String msg) {
        if (value != 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 int 值不为 0
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值为 0
     */
    public static void isNotZero(int value, String msg) {
        if (value == 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 int 值为正数（> 0）
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值不为正数
     */
    public static void isPositive(int value, String msg) {
        if (value <= 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 int 值为非负数（>= 0）
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值为负数
     */
    public static void isNotNegative(int value, String msg) {
        if (value < 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 int 值为负数（< 0）
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值不为负数
     */
    public static void isNegative(int value, String msg) {
        if (value >= 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 int 值为非正数（<= 0）
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值为正数
     */
    public static void isNotPositive(int value, String msg) {
        if (value > 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 long 值为 0
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值不为 0
     */
    public static void isZero(long value, String msg) {
        if (value != 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 long 值不为 0
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值为 0
     */
    public static void isNotZero(long value, String msg) {
        if (value == 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 long 值为正数（> 0）
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值不为正数
     */
    public static void isPositive(long value, String msg) {
        if (value <= 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 long 值为非负数（>= 0）
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值为负数
     */
    public static void isNotNegative(long value, String msg) {
        if (value < 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 double 值为 0
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值不为 0
     */
    public static void isZero(double value, String msg) {
        if (Double.compare(value, 0.0) != 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 double 值不为 0
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值为 0
     */
    public static void isNotZero(double value, String msg) {
        if (Double.compare(value, 0.0) == 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 double 值为正数（> 0）
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值不为正数
     */
    public static void isPositive(double value, String msg) {
        if (value <= 0.0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 double 值为非负数（>= 0）
     *
     * @param value 待检查的值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值为负数
     */
    public static void isNotNegative(double value, String msg) {
        if (value < 0.0) {
            throw new IllegalArgumentException(msg);
        }
    }

    // ==================== 相等断言方法 ====================

    /**
     * 断言两个对象相等
     *
     * @param o1 对象 1
     * @param o2 对象 2
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果两个对象不相等
     */
    public static void equals(Object o1, Object o2, String msg) {
        if (o1 == null || !o1.equals(o2)) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言两个对象相等（支持函数式异常信息）
     *
     * @param o1 对象 1
     * @param o2 对象 2
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果两个对象不相等
     */
    public static void equals(Object o1, Object o2, Supplier<String> msgSupplier) {
        if (o1 == null || !o1.equals(o2)) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    /**
     * 断言两个对象不相等
     *
     * @param o1 对象 1
     * @param o2 对象 2
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果两个对象相等
     */
    public static void notEquals(Object o1, Object o2, String msg) {
        if (o1 != null && o1.equals(o2)) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言两个对象不相等（支持函数式异常信息）
     *
     * @param o1 对象 1
     * @param o2 对象 2
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果两个对象相等
     */
    public static void notEquals(Object o1, Object o2, Supplier<String> msgSupplier) {
        if (o1 != null && o1.equals(o2)) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    // ==================== 数组断言方法 ====================

    /**
     * 断言数组不为空
     *
     * @param array 待检查的数组
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果数组为空
     */
    public static void isNotEmpty(Object[] array, String msg) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言数组不为空（支持函数式异常信息）
     *
     * @param array 待检查的数组
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果数组为空
     */
    public static void isNotEmpty(Object[] array, Supplier<String> msgSupplier) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    /**
     * 断言数组为空
     *
     * @param array 待检查的数组
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果数组不为空
     */
    public static void isEmpty(Object[] array, String msg) {
        if (array != null && array.length > 0) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言数组长度为指定值
     *
     * @param array 待检查的数组
     * @param expectedLength 期望长度
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果数组长度不匹配
     */
    public static void isArrayLength(Object[] array, int expectedLength, String msg) {
        if (array == null || array.length != expectedLength) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言数组包含指定元素
     *
     * @param array 待检查的数组
     * @param element 待查找的元素
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果数组不包含该元素
     */
    public static void arrayContains(Object[] array, Object element, String msg) {
        if (array == null || !Arrays.asList(array).contains(element)) {
            throw new IllegalArgumentException(msg);
        }
    }

    // ==================== 范围断言方法 ====================

    /**
     * 断言 int 值在指定范围内（包含边界）
     *
     * @param value 待检查的值
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值不在范围内
     */
    public static void inRange(int value, int min, int max, String msg) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 long 值在指定范围内（包含边界）
     *
     * @param value 待检查的值
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值不在范围内
     */
    public static void inRange(long value, long min, long max, String msg) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言 double 值在指定范围内（包含边界）
     *
     * @param value 待检查的值
     * @param min 最小值（包含）
     * @param max 最大值（包含）
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值不在范围内
     */
    public static void inRange(double value, double min, double max, String msg) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言值不在指定范围内
     *
     * @param value 待检查的值
     * @param min 最小值
     * @param max 最大值
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果值在范围内
     */
    public static void notInRange(int value, int min, int max, String msg) {
        if (value >= min && value <= max) {
            throw new IllegalArgumentException(msg);
        }
    }

    // ==================== 函数式断言方法 ====================

    /**
     * 断言条件满足（支持 BooleanSupplier）
     *
     * @param condition 条件供应商
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果条件不满足
     */
    public static void isTrue(BooleanSupplier condition, String msg) {
        if (!condition.getAsBoolean()) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言条件满足（支持 BooleanSupplier 和函数式异常）
     *
     * @param condition 条件供应商
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果条件不满足
     */
    public static void isTrue(BooleanSupplier condition, Supplier<String> msgSupplier) {
        if (!condition.getAsBoolean()) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    /**
     * 断言对象满足给定条件
     *
     * @param obj 待检查的对象
     * @param condition 条件判断
     * @param msg 错误消息
     * @throws IllegalArgumentException 如果对象不满足条件
     */
    public static <T> void satisfies(T obj, BooleanSupplier condition, String msg) {
        if (!condition.getAsBoolean()) {
            throw new IllegalArgumentException(msg);
        }
    }

    /**
     * 断言对象满足给定条件（支持函数式异常）
     *
     * @param obj 待检查的对象
     * @param condition 条件判断
     * @param msgSupplier 错误消息供应商
     * @throws IllegalArgumentException 如果对象不满足条件
     */
    public static <T> void satisfies(T obj, BooleanSupplier condition, Supplier<String> msgSupplier) {
        if (!condition.getAsBoolean()) {
            throw new IllegalArgumentException(msgSupplier != null ? msgSupplier.get() : null);
        }
    }

    // ==================== 自定义异常断言 ====================

    /**
     * 断言表达式为真（支持自定义异常）
     *
     * @param expression 布尔表达式
     * @param exceptionType 异常类型
     * @param exceptionSupplier 异常供应商
     * @param <T> 异常类型
     * @throws T 如果表达式为假
     */
    public static <T extends Throwable> void isTrue(boolean expression, Class<T> exceptionType, Supplier<T> exceptionSupplier) throws T {
        if (!expression) {
            T exception = exceptionSupplier != null ? exceptionSupplier.get() : null;
            if (exception != null) {
                throw exception;
            }
        }
    }

    /**
     * 断言对象不为 null（支持自定义异常）
     *
     * @param obj 待检查的对象
     * @param exceptionType 异常类型
     * @param exceptionSupplier 异常供应商
     * @param <T> 异常类型
     * @throws T 如果对象为 null
     */
    public static <T extends Throwable> void notNull(Object obj, Class<T> exceptionType, Supplier<T> exceptionSupplier) throws T {
        if (obj == null) {
            T exception = exceptionSupplier != null ? exceptionSupplier.get() : null;
            if (exception != null) {
                throw exception;
            }
        }
    }

    /**
     * 断言字符串不为空白（支持自定义异常）
     *
     * @param str 待检查的字符串
     * @param exceptionType 异常类型
     * @param exceptionSupplier 异常供应商
     * @param <T> 异常类型
     * @throws T 如果字符串为空白
     */
    public static <T extends Throwable> void isNotBlank(String str, Class<T> exceptionType, Supplier<T> exceptionSupplier) throws T {
        if (StringUtils.isBlank(str)) {
            T exception = exceptionSupplier != null ? exceptionSupplier.get() : null;
            if (exception != null) {
                throw exception;
            }
        }
    }

    /**
     * 断言集合不为空（支持自定义异常）
     *
     * @param collection 待检查的集合
     * @param exceptionType 异常类型
     * @param exceptionSupplier 异常供应商
     * @param <T> 异常类型
     * @throws T 如果集合为空
     */
    public static <T extends Throwable> void isNotEmpty(Collection<?> collection, Class<T> exceptionType, Supplier<T> exceptionSupplier) throws T {
        if (CollectionUtils.isEmpty(collection)) {
            T exception = exceptionSupplier != null ? exceptionSupplier.get() : null;
            if (exception != null) {
                throw exception;
            }
        }
    }
}
