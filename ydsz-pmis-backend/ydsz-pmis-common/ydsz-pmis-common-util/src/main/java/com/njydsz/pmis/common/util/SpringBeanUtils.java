package com.njydsz.pmis.common.util;

import org.springframework.context.ApplicationContext;

/**
 * Spring Bean 工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class SpringBeanUtils {

    private SpringBeanUtils() {
    }

    private static ApplicationContext applicationContext;

    /**
     * 设置 ApplicationContext
     *
     * @param context ApplicationContext
     */
    public static void setApplicationContext(ApplicationContext context) {
        SpringBeanUtils.applicationContext = context;
    }

    /**
     * 获取 ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            return SpringContextHolder.getApplicationContext();
        }
        return applicationContext;
    }

    /**
     * 根据 Class 获取 Bean
     */
    public static <T> T getBean(Class<T> clazz) {
        return getApplicationContext().getBean(clazz);
    }

    /**
     * 根据名称获取 Bean
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        return (T) getApplicationContext().getBean(name);
    }

    /**
     * 根据名称和 Class 获取 Bean
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return getApplicationContext().getBean(name, clazz);
    }

    /**
     * 判断是否包含 Bean
     */
    public static boolean containsBean(String name) {
        return getApplicationContext().containsBean(name);
    }

    /**
     * 判断 Bean 是否为单例
     */
    public static boolean isSingleton(String name) {
        return getApplicationContext().isSingleton(name);
    }

    /**
     * 获取 Bean 的类型
     */
    public static Class<?> getType(String name) {
        return getApplicationContext().getType(name);
    }

    /**
     * 获取指定类型的所有 Bean 名称
     */
    public static String[] getBeanNamesForType(Class<?> type) {
        return getApplicationContext().getBeanNamesForType(type);
    }
}
