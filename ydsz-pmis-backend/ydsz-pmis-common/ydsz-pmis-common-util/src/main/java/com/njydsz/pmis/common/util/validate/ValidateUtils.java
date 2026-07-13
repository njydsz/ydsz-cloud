package com.njydsz.pmis.common.util.validate;

import java.util.*;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * 校验工具类（hibernate-validator 快捷方法封装）
 *
 * <p>本工具类定位为 <b>hibernate-validator 的快捷方法封装</b>，而非独立校验框架。
 * 提供两类能力：
 * <ul>
 *   <li><b>快捷校验</b>：notNull、notBlank、isTrue 等常用单值校验，无需注解即可使用</li>
 *   <li><b>Bean 校验</b>：委托 hibernate-validator 执行基于 Jakarta Validation 注解的全量校验</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 快捷校验
 * ValidateUtils.notNull(param, "param cannot be null");
 * ValidateUtils.notBlank(name, "name cannot be blank");
 *
 * // Bean 校验（委托 hibernate-validator）
 * Set&lt;ConstraintViolation&lt;User&gt;&gt; violations = ValidateUtils.validateBean(user);
 * if (!violations.isEmpty()) {
 *     // 处理校验错误
 * }
 *
 * // 校验并抛出异常
 * ValidateUtils.validateBeanThrow(user);
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@SuppressWarnings("unchecked")
public class ValidateUtils {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^1[3-9]\\d{9}$"
    );

    private static final Pattern ID_CARD_PATTERN = Pattern.compile(
        "^[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]$"
    );

    private static final Pattern URL_PATTERN = Pattern.compile(
        "^(https?|ftp)://[^\\s/$.?#].[^\\s]*$"
    );

    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    /**
     * 懒加载的 hibernate-validator 实例
     */
    private static class ValidatorHolder {
        static final Validator VALIDATOR;
        static {
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                VALIDATOR = factory.getValidator();
            }
        }
    }

    private ValidateUtils() {
        throw new UnsupportedOperationException("ValidateUtils is a utility class and cannot be instantiated");
    }

    // ==================== Hibernate Validator 委托方法 ====================

    /**
     * 使用 hibernate-validator 校验 Bean
     *
     * <p>基于 Bean 上的 Jakarta Validation 注解（如 @NotNull、@Size 等）进行全量校验。
     *
     * @param bean 要校验的对象
     * @param <T>  Bean 类型
     * @return 约束违反集合，为空表示校验通过
     */
    public static <T> Set<ConstraintViolation<T>> validateBean(T bean) {
        return ValidatorHolder.VALIDATOR.validate(bean);
    }

    /**
     * 使用 hibernate-validator 校验 Bean 的指定属性
     *
     * @param bean        要校验的对象
     * @param propertyName 属性名称
     * @param value       属性值
     * @param <T>         Bean 类型
     * @param <V>         属性值类型
     * @return 约束违反集合，为空表示校验通过
     */
    public static <T, V> Set<ConstraintViolation<T>> validateProperty(T bean, String propertyName, V value) {
        return ValidatorHolder.VALIDATOR.validateProperty(bean, propertyName);
    }

    /**
     * 使用 hibernate-validator 校验 Bean，校验失败时抛出异常
     *
     * @param bean 要校验的对象
     * @throws ValidationException 当存在约束违反时抛出
     */
    public static void validateBeanThrow(Object bean) {
        Set<ConstraintViolation<Object>> violations = validateBean(bean);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .reduce((m1, m2) -> m1 + "; " + m2)
                    .orElse("validation failed");
            throw new ValidationException(message);
        }
    }

    /**
     * 获取 hibernate-validator 实例，供高级用法
     *
     * @return Validator 实例
     */
    public static Validator getValidator() {
        return ValidatorHolder.VALIDATOR;
    }

    // ==================== 快捷校验方法 ====================

    /**
     * 校验不为 null
     */
    public static <T> T notNull(T obj, String message) {
        if (obj == null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    /**
     * 校验为 null
     */
    public static <T> T isNull(T obj, String message) {
        if (obj != null) {
            throw new IllegalArgumentException(message);
        }
        return obj;
    }

    /**
     * 校验字符串不为空
     */
    public static String notEmpty(String str, String message) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * 校验字符串不为空白
     */
    public static String notBlank(String str, String message) {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * 校验字符串为空
     */
    public static String isEmpty(String str, String message) {
        if (str != null && !str.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * 校验集合不为空
     */
    public static <T> Collection<T> notEmpty(Collection<T> collection, String message) {
        if (collection == null || collection.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * 校验数组不为空
     */
    public static <T> T[] notEmpty(T[] array, String message) {
        if (array == null || array.length == 0) {
            throw new IllegalArgumentException(message);
        }
        return array;
    }

    /**
     * 校验 Map 不为空
     */
    public static <K, V> Map<K, V> notEmpty(Map<K, V> map, String message) {
        if (map == null || map.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return map;
    }

    /**
     * 校验数字为正数
     */
    public static <T extends Number & Comparable<T>> T isPositive(T number, String message) {
        if (number == null || number.doubleValue() <= 0) {
            throw new IllegalArgumentException(message);
        }
        return number;
    }

    /**
     * 校验数字为负数
     */
    public static <T extends Number & Comparable<T>> T isNegative(T number, String message) {
        if (number == null || number.doubleValue() >= 0) {
            throw new IllegalArgumentException(message);
        }
        return number;
    }

    /**
     * 校验数字为零
     */
    public static <T extends Number & Comparable<T>> T isZero(T number, String message) {
        if (number == null || number.doubleValue() != 0) {
            throw new IllegalArgumentException(message);
        }
        return number;
    }

    /**
     * 校验数字在范围内
     */
    public static <T extends Number & Comparable<T>> T inRange(T number, T min, T max, String message) {
        if (number == null || number.compareTo(min) < 0 || number.compareTo(max) > 0) {
            throw new IllegalArgumentException(message);
        }
        return number;
    }

    /**
     * 校验字符串长度
     */
    public static String lengthBetween(String str, int min, int max, String message) {
        if (str == null) {
            throw new IllegalArgumentException(message);
        }
        int length = str.length();
        if (length < min || length > max) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * 校验最小长度
     */
    public static String minLength(String str, int min, String message) {
        if (str == null || str.length() < min) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * 校验最大长度
     */
    public static String maxLength(String str, int max, String message) {
        if (str == null || str.length() > max) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * 校验邮箱格式
     */
    public static boolean isEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * 校验邮箱格式（带错误消息）
     */
    public static String isEmail(String email, String message) {
        if (!isEmail(email)) {
            throw new IllegalArgumentException(message);
        }
        return email;
    }

    /**
     * 校验手机号格式
     */
    public static boolean isPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone).matches();
    }

    /**
     * 校验手机号格式（带错误消息）
     */
    public static String isPhone(String phone, String message) {
        if (!isPhone(phone)) {
            throw new IllegalArgumentException(message);
        }
        return phone;
    }

    /**
     * 校验身份证号码
     */
    public static boolean isIdCard(String idCard) {
        return idCard != null && ID_CARD_PATTERN.matcher(idCard).matches();
    }

    /**
     * 校验身份证号码（带错误消息）
     */
    public static String isIdCard(String idCard, String message) {
        if (!isIdCard(idCard)) {
            throw new IllegalArgumentException(message);
        }
        return idCard;
    }

    /**
     * 校验 URL 格式
     */
    public static boolean isUrl(String url) {
        return url != null && URL_PATTERN.matcher(url).matches();
    }

    /**
     * 校验 URL 格式（带错误消息）
     */
    public static String isUrl(String url, String message) {
        if (!isUrl(url)) {
            throw new IllegalArgumentException(message);
        }
        return url;
    }

    /**
     * 校验 IP 地址格式
     */
    public static boolean isIp(String ip) {
        return ip != null && IP_PATTERN.matcher(ip).matches();
    }

    /**
     * 校验 IP 地址格式（带错误消息）
     */
    public static String isIp(String ip, String message) {
        if (!isIp(ip)) {
            throw new IllegalArgumentException(message);
        }
        return ip;
    }

    /**
     * 校验正则表达式
     */
    public static boolean isMatch(String str, String regex) {
        return str != null && Pattern.matches(regex, str);
    }

    /**
     * 校验正则表达式（带错误消息）
     */
    public static String isMatch(String str, String regex, String message) {
        if (!isMatch(str, regex)) {
            throw new IllegalArgumentException(message);
        }
        return str;
    }

    /**
     * 校验布尔值
     */
    public static void isTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验布尔值为假
     */
    public static void isFalse(boolean condition, String message) {
        if (condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验对象相等
     */
    public static <T> T isEqualTo(T actual, T expected, String message) {
        if (!Objects.equals(actual, expected)) {
            throw new IllegalArgumentException(message);
        }
        return actual;
    }

    /**
     * 校验对象不相等
     */
    public static <T> T isNotEqualTo(T actual, T expected, String message) {
        if (Objects.equals(actual, expected)) {
            throw new IllegalArgumentException(message);
        }
        return actual;
    }

    /**
     * 校验实例类型
     */
    public static <T> T isInstanceOf(Object obj, Class<T> type, String message) {
        if (!type.isInstance(obj)) {
            throw new IllegalArgumentException(message);
        }
        return type.cast(obj);
    }

    /**
     * 校验不是实例类型
     */
    
    public static <T> T isNotInstanceOf(Object obj, Class<T> type, String message) {
        if (type.isInstance(obj)) {
            throw new IllegalArgumentException(message);
        }
        return (T) obj;
    }

    /**
     * 校验集合包含元素
     */
    public static <T> Collection<T> contains(Collection<T> collection, Object element, String message) {
        if (collection == null || !collection.contains(element)) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * 校验集合不包含元素
     */
    public static <T> Collection<T> notContains(Collection<T> collection, Object element, String message) {
        if (collection != null && collection.contains(element)) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * 校验集合大小
     */
    public static <T> Collection<T> hasSize(Collection<T> collection, int size, String message) {
        if (collection == null || collection.size() != size) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * 校验集合最小大小
     */
    public static <T> Collection<T> minSize(Collection<T> collection, int minSize, String message) {
        if (collection == null || collection.size() < minSize) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * 校验集合最大大小
     */
    public static <T> Collection<T> maxSize(Collection<T> collection, int maxSize, String message) {
        if (collection == null || collection.size() > maxSize) {
            throw new IllegalArgumentException(message);
        }
        return collection;
    }

    /**
     * 创建校验器
     */
    public static Validator validate() {
        return new Validator();
    }

    /**
     * 创建带对象的校验器
     */
    public static Validator validate(Object obj) {
        return new Validator().target(obj);
    }

    /**
     * 校验器（链式 API）
     */
    public static class Validator {
        private final List<String> errors = new ArrayList<>();

        /**
         * 设置校验目标
         */
        public Validator target(Object target) {
            return this;
        }

        /**
         * 设置字段名
         */
        public Validator field(String fieldName) {
            return this;
        }

        /**
         * 校验不为 null
         */
        public Validator notNull(Object obj, String fieldName) {
            if (obj == null) {
                errors.add(fieldName + " cannot be null");
            }
            return this;
        }

        /**
         * 校验不为空
         */
        public Validator notEmpty(String str, String fieldName) {
            if (str == null || str.isEmpty()) {
                errors.add(fieldName + " cannot be empty");
            }
            return this;
        }

        /**
         * 校验不为空白
         */
        public Validator notBlank(String str, String fieldName) {
            if (str == null || str.trim().isEmpty()) {
                errors.add(fieldName + " cannot be blank");
            }
            return this;
        }

        /**
         * 校验邮箱
         */
        public Validator isEmail(String email, String fieldName) {
            if (!ValidateUtils.isEmail(email)) {
                errors.add(fieldName + " is not a valid email");
            }
            return this;
        }

        /**
         * 校验手机号
         */
        public Validator isPhone(String phone, String fieldName) {
            if (!ValidateUtils.isPhone(phone)) {
                errors.add(fieldName + " is not a valid phone number");
            }
            return this;
        }

        /**
         * 校验身份证
         */
        public Validator isIdCard(String idCard, String fieldName) {
            if (!ValidateUtils.isIdCard(idCard)) {
                errors.add(fieldName + " is not a valid ID card");
            }
            return this;
        }

        /**
         * 校验 URL
         */
        public Validator isUrl(String url, String fieldName) {
            if (!ValidateUtils.isUrl(url)) {
                errors.add(fieldName + " is not a valid URL");
            }
            return this;
        }

        /**
         * 添加自定义错误
         */
        public Validator addError(String error) {
            errors.add(error);
            return this;
        }

        /**
         * 添加条件校验
         */
        public Validator when(boolean condition, String error) {
            if (!condition) {
                errors.add(error);
            }
            return this;
        }

        /**
         * 判断是否有错误
         */
        public boolean hasErrors() {
            return !errors.isEmpty();
        }

        /**
         * 获取所有错误
         */
        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        /**
         * 如果有错误则抛出异常
         */
        public void throwIfInvalid() {
            if (hasErrors()) {
                throw new ValidationException(String.join("; ", errors));
            }
        }

        /**
         * 校验通过
         */
        public boolean isValid() {
            return !hasErrors();
        }
    }

    /**
     * 校验异常
     */
    public static class ValidationException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        
        public ValidationException(String message) {
            super(message);
        }

        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
