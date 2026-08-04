package com.njydsz.common.util.spring;

import java.lang.annotation.Annotation;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Spring 上下文持有者
 *
 * <p>提供全局静态方法访问 Spring ApplicationContext，
 * 支持通过实现 {@link ApplicationContextAware} 自动初始化。
 *
 * <p>本类不标注 {@code @Component}，统一在 {@link com.njydsz.common.util.config.UtilAutoConfiguration}
 * 中以 {@code @Bean} 注册，避免组件扫描与 AutoConfiguration 双重注册冲突。
 *
 * <p><b>使用方式：静态方法</b>
 * <pre>{@code
 * // 根据类型获取 Bean
 * MessageSource messageSource = SpringContextHolder.getBean(MessageSource.class);
 *
 * // 根据名称获取 Bean
 * Object bean = SpringContextHolder.getBean("myBean");
 *
 * // 根据名称和类型获取 Bean
 * MyService service = SpringContextHolder.getBean("myService", MyService.class);
 *
 * // 获取所有指定类型的 Bean
 * Map<String, MyService> services = SpringContextHolder.getBeansOfType(MyService.class);
 * }</pre>
 *
 * <p><b>检查上下文是否已初始化：</b>
 * <pre>{@code
 * if (SpringContextHolder.isInitialized()) {
 *     // 安全地调用 getBean 方法
 * }
 * }</pre>
 *
 * <p><b>生命周期：</b>实现 {@link DisposableBean}，容器销毁时清理静态引用，
 * 避免 ClassLoader 泄漏与死上下文调用。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class SpringContextHolder implements ApplicationContextAware, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(SpringContextHolder.class);

    /** Spring 应用上下文（volatile 保证可见性，无需额外锁） */
    private static volatile ApplicationContext applicationContext;

    // ==================== ApplicationContextAware / DisposableBean 实现 ====================

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 校验多上下文场景：若已存在不同上下文实例，抛异常避免静默覆盖导致死上下文调用。
        // Spring 正常流程下同一 Bean 的 setApplicationContext 仅调用一次；
        // 出现二次调用且上下文不同，通常是多上下文误用，应快速失败。
        ApplicationContext existing = SpringContextHolder.applicationContext;
        if (existing != null && existing != applicationContext) {
            throw new IllegalStateException(
                    "ApplicationContext 已被不同实例初始化，原上下文: " + existing
                            + "，新上下文: " + applicationContext);
        }
        SpringContextHolder.applicationContext = applicationContext;
    }

    @Override
    public void destroy() {
        // 容器销毁时清理静态引用，避免 ClassLoader 泄漏和死上下文调用
        logger.debug("清理 SpringContextHolder 静态 applicationContext 引用");
        applicationContext = null;
    }

    // ==================== 静态方法 ====================

    /**
     * 获取 Spring ApplicationContext
     *
     * <p>注意：直接暴露 ApplicationContext 属于泄漏抽象，建议优先使用 {@link #getBean(Class)} 等
     * 类型安全方法。本方法保留仅为兼容既有调用方。
     *
     * @return ApplicationContext 实例
     * @throws IllegalStateException 如果 ApplicationContext 未初始化
     * @deprecated 优先使用 {@link #getBean(Class)} / {@link #getBean(String, Class)} 等类型安全方法，
     *             避免直接持有 ApplicationContext 引用
     */
    @Deprecated
    public static ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("ApplicationContext 未初始化，请在 Spring 容器中初始化");
        }
        return applicationContext;
    }

    /**
     * 检查 Spring ApplicationContext 是否已初始化
     *
     * @return 如果已初始化返回 true，否则返回 false
     */
    public static boolean isInitialized() {
        return applicationContext != null;
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
     * <p>使用 {@link ApplicationContext#getBeanNamesForType(Class)} 查询，
     * 避免通过 {@code getBean} 抛出异常来判断是否存在（异常开销大）。
     *
     * @param clazz Bean 类型
     * @param <T> Bean 类型
     * @return 如果包含返回 true，否则返回 false
     */
    public static <T> boolean containsBean(Class<T> clazz) {
        if (clazz == null) {
            return false;
        }
        ApplicationContext ctx = applicationContext;
        if (ctx == null) {
            return false;
        }
        String[] names = ctx.getBeanNamesForType(clazz);
        return names != null && names.length > 0;
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
