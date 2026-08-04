package com.njydsz.common.util.spring;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.Order;

/**
 * Spring 上下文持有者
 *
 * <p>提供全局静态方法访问 Spring ApplicationContext，
 * 支持通过实现 {@link ApplicationContextAware} 自动初始化。
 *
 * <p>本类不标注 {@code @Component}，统一在 {@link com.njydsz.common.util.config.UtilAutoConfiguration}
 * 中以 {@code @Bean} 注册，避免组件扫描与 AutoConfiguration 双重注册冲突。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 根据名称获取 Bean
 * Object bean = SpringContextHolder.getBean("myBean");
 *
 * // 根据类型获取 Bean
 * MessageSource messageSource = SpringContextHolder.getBean(MessageSource.class);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@Order(Integer.MIN_VALUE)
public class SpringContextHolder implements ApplicationContextAware {

    /** Spring 应用上下文（volatile 保证可见性，无需额外锁） */
    private static volatile ApplicationContext applicationContext;

    // ==================== ApplicationContextAware 实现 ====================

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // Spring 保证每个 Bean 的 setApplicationContext 仅调用一次；
        // volatile 写足以保证可见性，无需 DCL + ReentrantLock。
        SpringContextHolder.applicationContext = applicationContext;
    }

    // ==================== 静态方法 ====================

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
}
