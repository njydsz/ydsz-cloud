package com.njydsz.pmis.common.util.spring;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.context.ApplicationContext;

/**
 * Spring 容器工具类
 *
 * 提供全局静态方法访问 Spring ApplicationContext 和获取 Bean
 *
 * 使用示例:
 * <pre>
 * // 获取 Bean
 * MyService service = SpringBeanUtils.getBean(MyService.class);
 *
 * // 获取带名称的 Bean
 * MyService service = SpringBeanUtils.getBean("myService", MyService.class);
 *
 * // 获取所有指定类型的 Bean
 * Map<String, MyService> services = SpringBeanUtils.getBeansOfType(MyService.class);
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public class SpringBeanUtils {

    /**
     * Spring 应用上下文
     */
    private static volatile ApplicationContext applicationContext;

    /**
     * 线程锁
     */
    private static final Lock LOCK = new ReentrantLock();

    /**
     * 设置 Spring ApplicationContext
     *
     * 该方法应在 Spring 容器启动时调用，用于初始化 ApplicationContext
     *
     * @param context Spring ApplicationContext 实例
     */
    public static void setApplicationContext(ApplicationContext context) {
        if (applicationContext == null) {
            LOCK.lock();
            try {
                if (applicationContext == null) {
                    applicationContext = context;
                }
            } finally {
                LOCK.unlock();
            }
        }
    }

    /**
     * 获取 Spring ApplicationContext（内部使用，不抛异常）
     *
     * @return ApplicationContext 实例，未初始化时返回 null
     */
    static ApplicationContext getApplicationContextInternal() {
        return applicationContext;
    }

    /**
     * 获取 Spring ApplicationContext
     *
     * @return ApplicationContext 实例
     * @throws IllegalStateException 如果 ApplicationContext 未初始化
     */
    public static ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext 未初始化，请在 Spring 容器中初始化");
        }
        return applicationContext;
    }

    /**
     * 根据名称获取 Bean
     *
     * @param name Bean 名称
     * @return Bean 实例
     * @throws IllegalArgumentException 如果 Bean 名称为空
     * @throws IllegalStateException 如果 ApplicationContext 未初始化
     */
    public static Object getBean(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Bean 名称不能为空");
        }
        ApplicationContext ctx = applicationContext;
        if (ctx == null) {
            throw new IllegalStateException("ApplicationContext 未初始化");
        }
        return ctx.getBean(name);
    }

    /**
     * 根据类型获取 Bean
     *
     * @param clazz Bean 类型
     * @param <T> Bean 类型
     * @return Bean 实例
     * @throws IllegalArgumentException 如果 Bean 类型为空
     * @throws IllegalStateException 如果 ApplicationContext 未初始化
     */
    public static <T> T getBean(Class<T> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Bean 类型不能为空");
        }
        ApplicationContext ctx = applicationContext;
        if (ctx == null) {
            throw new IllegalStateException("ApplicationContext 未初始化");
        }
        return ctx.getBean(clazz);
    }

    /**
     * 根据名称和类型获取 Bean
     *
     * @param name Bean 名称
     * @param clazz Bean 类型
     * @param <T> Bean 类型
     * @return Bean 实例
     * @throws IllegalArgumentException 如果 Bean 名称或类型为空
     * @throws IllegalStateException 如果 ApplicationContext 未初始化
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Bean 名称不能为空");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("Bean 类型不能为空");
        }
        ApplicationContext ctx = applicationContext;
        if (ctx == null) {
            throw new IllegalStateException("ApplicationContext 未初始化");
        }
        return ctx.getBean(name, clazz);
    }

    /**
     * 获取所有指定类型的 Bean
     *
     * @param clazz Bean 类型
     * @param <T> Bean 类型
     * @return Bean 映射，key 为 Bean 名称，value 为 Bean 实例
     * @throws IllegalArgumentException 如果 Bean 类型为空
     * @throws IllegalStateException 如果 ApplicationContext 未初始化
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Bean 类型不能为空");
        }
        ApplicationContext ctx = applicationContext;
        if (ctx == null) {
            throw new IllegalStateException("ApplicationContext 未初始化");
        }
        return ctx.getBeansOfType(clazz);
    }

    /**
     * 获取所有带有指定注解的 Bean
     *
     * @param annotationType 注解类型
     * @return Bean 映射，key 为 Bean 名称，value 为 Bean 实例
     * @throws IllegalArgumentException 如果注解类型为空
     * @throws IllegalStateException 如果 ApplicationContext 未初始化
     */
    public static Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) {
        if (annotationType == null) {
            throw new IllegalArgumentException("注解类型不能为空");
        }
        ApplicationContext ctx = applicationContext;
        if (ctx == null) {
            throw new IllegalStateException("ApplicationContext 未初始化");
        }
        return ctx.getBeansWithAnnotation(annotationType);
    }

    /**
     * 检查容器中是否包含指定名称的 Bean
     *
     * @param name Bean 名称
     * @return 如果包含返回 true，否则返回 false
     */
    public static boolean containsBean(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        ApplicationContext ctx = applicationContext;
        if (ctx == null) {
            return false;
        }
        return ctx.containsBean(name);
    }

    /**
     * 检查容器中是否包含指定类型的 Bean
     *
     * @param clazz Bean 类型
     * @param <T> Bean 类型
     * @return 如果包含返回 true，否则返回 false
     */
    public static <T> boolean containsBean(Class<T> clazz) {
        if (clazz == null) {
            return false;
        }
        try {
            getBean(clazz);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取指定名称的 Bean 的类型
     *
     * @param name Bean 名称
     * @return Bean 类型
     * @throws IllegalArgumentException 如果 Bean 名称为空
     * @throws IllegalStateException 如果 ApplicationContext 未初始化
     */
    public static Class<?> getType(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Bean 名称不能为空");
        }
        ApplicationContext ctx = applicationContext;
        if (ctx == null) {
            throw new IllegalStateException("ApplicationContext 未初始化");
        }
        return ctx.getType(name);
    }
}
