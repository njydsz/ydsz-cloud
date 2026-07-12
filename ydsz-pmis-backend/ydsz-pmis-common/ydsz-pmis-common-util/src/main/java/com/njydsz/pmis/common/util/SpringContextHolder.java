package com.njydsz.pmis.common.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文持有器
 *
 * <p>提供静态访问 Spring ApplicationContext 的能力，用于非 Spring 管理的对象获取 Bean。
 * 对标 remi-comm SpringContextHolder。
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.applicationContext = applicationContext;
    }

    /**
     * 获取 ApplicationContext
     *
     * @return ApplicationContext
     */
    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    /**
     * 根据 Bean 名称获取 Bean
     *
     * @param name Bean 名称
     * @param <T>  Bean 类型
     * @return Bean 实例
     */
    @SuppressWarnings("unchecked")
    public static <T> T getBean(String name) {
        return (T) applicationContext.getBean(name);
    }

    /**
     * 根据 Bean 类型获取 Bean
     *
     * @param clazz Bean 类型
     * @param <T>   Bean 类型
     * @return Bean 实例
     */
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }

    /**
     * 根据 Bean 名称和类型获取 Bean
     *
     * @param name  Bean 名称
     * @param clazz Bean 类型
     * @param <T>   Bean 类型
     * @return Bean 实例
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return applicationContext.getBean(name, clazz);
    }

    /**
     * 获取指定类型的所有 Bean
     *
     * @param clazz Bean 类型
     * @param <T>   Bean 类型
     * @return Bean 名称到实例的 Map
     */
    public static <T> java.util.Map<String, T> getBeansOfType(Class<T> clazz) {
        return applicationContext.getBeansOfType(clazz);
    }

    /**
     * 获取当前激活的 Profile
     *
     * @return Profile 数组
     */
    public static String[] getActiveProfiles() {
        return applicationContext.getEnvironment().getActiveProfiles();
    }

    /**
     * 获取当前激活的 Profile（第一个）
     *
     * @return Profile 名称，无激活 Profile 时返回 null
     */
    public static String getActiveProfile() {
        String[] profiles = getActiveProfiles();
        return profiles.length > 0 ? profiles[0] : null;
    }

    /**
     * 获取属性值
     *
     * @param key 属性键
     * @return 属性值
     */
    public static String getProperty(String key) {
        return applicationContext.getEnvironment().getProperty(key);
    }

    /**
     * 获取属性值（带默认值）
     *
     * @param key          属性键
     * @param defaultValue 默认值
     * @return 属性值
     */
    public static String getProperty(String key, String defaultValue) {
        return applicationContext.getEnvironment().getProperty(key, defaultValue);
    }

    /**
     * 发布事件
     *
     * @param event 事件
     */
    public static void publishEvent(Object event) {
        if (applicationContext != null) {
            applicationContext.publishEvent(event);
        }
    }
}
