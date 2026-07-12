package com.njydsz.pmis.common.util.spring;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.env.Environment;

/**
 * Spring 环境属性工具类
 *
 * 提供便捷的方法来获取 application.properties 或 application.yml 中的配置属性
 *
 * 使用示例:
 * <pre>
 * // 获取字符串属性
 * String port = SpringPropertyUtils.getString("server.port");
 *
 * // 获取带默认值的属性
 * String appName = SpringPropertyUtils.getString("spring.application.name", "default-app");
 *
 * // 获取其他类型属性
 * Integer port = SpringPropertyUtils.getInteger("server.port");
 * Boolean debug = SpringPropertyUtils.getBoolean("debug", false);
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class SpringPropertyUtils {

    /**
     * Spring Environment 对象
     */
    private static volatile Environment environment;

    /**
     * 设置 Spring Environment
     *
     * 该方法应在 Spring 容器启动时调用
     *
     * @param env Spring Environment 实例
     */
    public static void setEnvironment(Environment env) {
        environment = env;
    }

    /**
     * 获取字符串类型的属性值
     *
     * @param key 属性键
     * @return 属性值，如果不存在则返回 null
     */
    public static String getString(String key) {
        validateEnvironment();
        return environment.getProperty(key);
    }

    /**
     * 获取字符串类型的属性值，支持默认值
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值，如果不存在则返回默认值
     */
    public static String getString(String key, String defaultValue) {
        validateEnvironment();
        return environment.getProperty(key, defaultValue);
    }

    /**
     * 获取 Integer 类型的属性值
     *
     * @param key 属性键
     * @return 属性值，如果不存在或转换失败则返回 null
     */
    public static Integer getInteger(String key) {
        validateEnvironment();
        return environment.getProperty(key, Integer.class);
    }

    /**
     * 获取 Integer 类型的属性值，支持默认值
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值，如果不存在或转换失败则返回默认值
     */
    public static Integer getInteger(String key, Integer defaultValue) {
        validateEnvironment();
        return environment.getProperty(key, Integer.class, defaultValue);
    }

    /**
     * 获取 Boolean 类型的属性值
     *
     * @param key 属性键
     * @return 属性值，如果不存在或转换失败则返回 null
     */
    public static Boolean getBoolean(String key) {
        validateEnvironment();
        return environment.getProperty(key, Boolean.class);
    }

    /**
     * 获取 Boolean 类型的属性值，支持默认值
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值，如果不存在或转换失败则返回默认值
     */
    public static Boolean getBoolean(String key, Boolean defaultValue) {
        validateEnvironment();
        return environment.getProperty(key, Boolean.class, defaultValue);
    }

    /**
     * 获取 Long 类型的属性值
     *
     * @param key 属性键
     * @return 属性值，如果不存在或转换失败则返回 null
     */
    public static Long getLong(String key) {
        validateEnvironment();
        return environment.getProperty(key, Long.class);
    }

    /**
     * 获取 Long 类型的属性值，支持默认值
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值，如果不存在或转换失败则返回默认值
     */
    public static Long getLong(String key, Long defaultValue) {
        validateEnvironment();
        return environment.getProperty(key, Long.class, defaultValue);
    }

    /**
     * 获取 Double 类型的属性值
     *
     * @param key 属性键
     * @return 属性值，如果不存在或转换失败则返回 null
     */
    public static Double getDouble(String key) {
        validateEnvironment();
        return environment.getProperty(key, Double.class);
    }

    /**
     * 获取 Double 类型的属性值，支持默认值
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值，如果不存在或转换失败则返回默认值
     */
    public static Double getDouble(String key, Double defaultValue) {
        validateEnvironment();
        return environment.getProperty(key, Double.class, defaultValue);
    }

    /**
     * 获取 Float 类型的属性值
     *
     * @param key 属性键
     * @return 属性值，如果不存在或转换失败则返回 null
     */
    public static Float getFloat(String key) {
        validateEnvironment();
        return environment.getProperty(key, Float.class);
    }

    /**
     * 获取 Float 类型的属性值，支持默认值
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值，如果不存在或转换失败则返回默认值
     */
    public static Float getFloat(String key, Float defaultValue) {
        validateEnvironment();
        return environment.getProperty(key, Float.class, defaultValue);
    }

    /**
     * 获取字符串数组类型的属性值
     *
     * @param key 属性键
     * @param delimiter 分隔符，默认为逗号
     * @return 属性值数组，如果不存在则返回空数组
     */
    public static String[] getStringArray(String key, String delimiter) {
        validateEnvironment();
        String value = environment.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return new String[0];
        }
        return value.split(delimiter != null ? delimiter : ",");
    }

    /**
     * 获取字符串数组类型的属性值，默认以逗号分隔
     *
     * @param key 属性键
     * @return 属性值数组，如果不存在则返回空数组
     */
    public static String[] getStringArray(String key) {
        return getStringArray(key, ",");
    }

    /**
     * 获取字符串列表类型的属性值
     *
     * @param key 属性键
     * @param delimiter 分隔符，默认为逗号
     * @return 属性值列表，如果不存在则返回空列表
     */
    public static List<String> getStringList(String key, String delimiter) {
        return Arrays.asList(getStringArray(key, delimiter));
    }

    /**
     * 获取字符串列表类型的属性值，默认以逗号分隔
     *
     * @param key 属性键
     * @return 属性值列表，如果不存在则返回空列表
     */
    public static List<String> getStringList(String key) {
        return getStringList(key, ",");
    }

    /**
     * 获取 Integer 列表类型的属性值
     *
     * @param key 属性键
     * @param delimiter 分隔符，默认为逗号
     * @return 属性值列表，如果不存在则返回空列表
     */
    public static List<Integer> getIntegerList(String key, String delimiter) {
        validateEnvironment();
        String[] stringArray = getStringArray(key, delimiter);
        return Arrays.stream(stringArray)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * 获取 Integer 列表类型的属性值，默认以逗号分隔
     *
     * @param key 属性键
     * @return 属性值列表，如果不存在则返回空列表
     */
    public static List<Integer> getIntegerList(String key) {
        return getIntegerList(key, ",");
    }

    /**
     * 检查属性是否存在
     *
     * @param key 属性键
     * @return 如果属性存在返回 true，否则返回 false
     */
    public static boolean containsProperty(String key) {
        validateEnvironment();
        return environment.containsProperty(key);
    }

    /**
     * 获取属性值，不进行类型转换
     *
     * @param key 属性键
     * @return 属性值，如果不存在则返回 null
     */
    public static String getProperty(String key) {
        validateEnvironment();
        return environment.getProperty(key);
    }

    /**
     * 获取属性值，支持默认值，不进行类型转换
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 属性值，如果不存在则返回默认值
     */
    public static String getProperty(String key, String defaultValue) {
        validateEnvironment();
        return environment.getProperty(key, defaultValue);
    }

    /**
     * 获取属性值，并进行占位符解析
     *
     * @param key 属性键
     * @return 解析后的属性值，如果不存在则返回 null
     */
    public static String resolvePlaceholders(String key) {
        validateEnvironment();
        return environment.resolvePlaceholders(key);
    }

    /**
     * 获取属性值，进行占位符解析并支持默认值
     *
     * @param key 属性键
     * @param defaultValue 默认值
     * @return 解析后的属性值，如果不存在则返回默认值
     */
    public static String resolveRequiredPlaceholders(String key, String defaultValue) {
        validateEnvironment();
        try {
            return environment.resolveRequiredPlaceholders(key);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    /**
     * 验证环境是否已初始化
     */
    private static void validateEnvironment() {
        if (environment == null) {
            throw new IllegalStateException("SpringPropertyUtils 未初始化，请确保 Environment 已设置");
        }
    }
}
