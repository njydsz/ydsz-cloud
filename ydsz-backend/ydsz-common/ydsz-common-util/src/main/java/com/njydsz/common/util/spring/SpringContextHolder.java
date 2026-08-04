package com.njydsz.common.util.spring;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.Order;

/**
 * Spring 上下文持有者
 *
 * <p>提供全局静态方法和实例方法访问 Spring ApplicationContext，
 * 支持通过实现 {@link ApplicationContextAware} 自动初始化。
 *
 * <p>本类不标注 {@code @Component}，统一在 {@link com.njydsz.common.util.config.UtilAutoConfiguration}
 * 中以 {@code @Bean} 注册，避免组件扫描与 AutoConfiguration 双重注册冲突。
 *
 * <p><b>使用方式一：静态方法（向后兼容）</b>
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
 * <p><b>使用方式二：注入实例方法</b>
 * <pre>{@code
 * @Service
 * public class MyService {
 *     private final SpringContextHolder contextHolder;
 *
 *     public MyService(SpringContextHolder contextHolder) {
 *         this.contextHolder = contextHolder;
 *     }
 *
 *     public void doSomething() {
 *         MessageSource messageSource = contextHolder.getBeanInstance(MessageSource.class);
 *     }
 * }
 * }</pre>
 *
 * <p><b>检查上下文是否已初始化：</b>
 * <pre>{@code
 * if (SpringContextHolder.isInitialized()) {
 *     // 安全地调用 getBean 方法
 * }
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

    // ==================== 实例方法（支持注入使用） ====================

    /**
     * 根据名称获取 Bean
     *
     * @param name Bean 名称
     * @return Bean 实例
     * @throws NullPointerException     如果 Bean 名称为空
     * @throws IllegalStateException    如果 ApplicationContext 未初始化
     */
    public Object getBeanInstance(String name) {
        Objects.requireNonNull(name, "Bean 名称不能为空");
        return getBean(name);
    }

    /**
     * 根据类型获取 Bean
     *
     * @param clazz Bean 类型
     * @param <T>   Bean 类型
     * @return Bean 实例
     * @throws NullPointerException     如果 Bean 类型为空
     * @throws IllegalStateException    如果 ApplicationContext 未初始化
     */
    public <T> T getBeanInstance(Class<T> clazz) {
        Objects.requireNonNull(clazz, "Bean 类型不能为空");
        return getBean(clazz);
    }

    /**
     * 根据名称和类型获取 Bean
     *
     * @param name  Bean 名称
     * @param clazz Bean 类型
     * @param <T>   Bean 类型
     * @return Bean 实例
     * @throws NullPointerException     如果 Bean 名称或类型为空
     * @throws IllegalStateException    如果 ApplicationContext 未初始化
     */
    public <T> T getBeanInstance(String name, Class<T> clazz) {
        Objects.requireNonNull(name, "Bean 名称不能为空");
        Objects.requireNonNull(clazz, "Bean 类型不能为空");
        return getBean(name, clazz);
    }

    /**
     * 获取所有指定类型的 Bean
     *
     * @param clazz Bean 类型
     * @param <T>   Bean 类型
     * @return Bean 映射，key 为 Bean 名称，value 为 Bean 实例
     * @throws NullPointerException     如果 Bean 类型为空
     * @throws IllegalStateException    如果 ApplicationContext 未初始化
     */
    public <T> Map<String, T> getBeansOfTypeInstance(Class<T> clazz) {
        Objects.requireNonNull(clazz, "Bean 类型不能为空");
        return getBeansOfType(clazz);
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
