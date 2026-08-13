package com.njydsz.common.util.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * Spring 上下文持有者
 *
 * <p>提供全局静态方法访问 Spring ApplicationContext，支持通过类型或名称获取 Bean。
 * 通过实现 {@link ApplicationContextAware} 自动初始化。
 *
 * <p>本类不标注 {@code @Component}，统一在 {@link com.njydsz.common.util.config.UtilAutoConfiguration}
 * 中以 {@code @Bean} 注册，避免组件扫描与 AutoConfiguration 双重注册冲突。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * // 根据类型获取 Bean
 * MessageSource messageSource = SpringContextHolder.getBean(MessageSource.class);
 *
 * // 根据名称和类型获取 Bean
 * MyService service = SpringContextHolder.getBean("myService", MyService.class);
 *
 * // 检查上下文是否已初始化
 * if (SpringContextHolder.isInitialized()) {
 *     // 安全地调用 getBean 方法
 * }
 * }</pre>
 *
 * <p><b>生命周期：</b>实现 {@link DisposableBean}，容器销毁时清理静态引用。
 * 额外注册 JVM {@link Runtime#addShutdownHook(Thread) ShutdownHook} 兜底清理，
 * 防止多 ClassLoader 热部署场景下 {@code destroy()} 未触发导致旧上下文泄漏。
 *
 * <p><b>反模式警告：</b>本类使用静态持有 ApplicationContext 的方式提供便捷访问，
 * 但这属于服务定位器反模式。静态持有会导致：测试时需要完整容器上下文、
 * 隐藏的类加载顺序依赖、掩盖组件之间的真实依赖关系。
 *
 * <p><b>推荐替代方案：</b>
 * <ul>
 *   <li>Spring Bean 场景：使用构造器注入或 {@code @Autowired} 直接注入目标 Bean</li>
 *   <li>需要 ApplicationContext 本身：注入 {@link ApplicationContext} 而非使用本类</li>
 *   <li>工厂类场景：使用 {@code ObjectProvider<T>} 或 {@code ApplicationContext#getBean} 注入</li>
 * </ul>
 *
 * @deprecated 自 4.0.0 起标记为过时，推荐使用依赖注入替代。
 *             保留本类为保证向后兼容，未来版本将移除。
 * @author ydsz-team
 * @since 1.0.0
 */
@Deprecated(since = "4.0.0", forRemoval = true)
public class SpringContextHolder implements ApplicationContextAware, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(SpringContextHolder.class);

    /** Spring 应用上下文（volatile 保证可见性，无需额外锁） */
    private static volatile ApplicationContext applicationContext;

    // ==================== ApplicationContextAware / DisposableBean 实现 ====================

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // 校验多上下文场景：若已存在不同上下文实例，抛异常避免静默覆盖导致死上下文调用。
        ApplicationContext existing = SpringContextHolder.applicationContext;
        if (existing != null && existing != applicationContext) {
            throw new IllegalStateException(
                    "ApplicationContext 已被不同实例初始化，原上下文: " + existing
                            + "，新上下文: " + applicationContext);
        }
        SpringContextHolder.applicationContext = applicationContext;

        // ShutdownHook 兜底：多 ClassLoader 热部署场景下，若 destroy() 未触发，
        // JVM 退出时清理静态引用，避免 ClassLoader 泄漏。
        // 使用 SpringContextHolder 类的 ClassLoader 加载该线程，不持有外部 ClassLoader。
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (SpringContextHolder.applicationContext != null) {
                    logger.warn("ShutdownHook 兜底清理 SpringContextHolder 静态 applicationContext 引用"
                            + "（可能因 DisposableBean.destroy() 未触发）");
                    SpringContextHolder.applicationContext = null;
                }
            }, "spring-context-holder-shutdown-hook"));
        } catch (IllegalStateException e) {
            // JVM 已在关闭中，忽略
            logger.debug("JVM 已在关闭中，无法注册 ShutdownHook: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() {
        logger.debug("DisposableBean 清理 SpringContextHolder 静态 applicationContext 引用");
        applicationContext = null;
    }

    // ==================== 静态方法 ====================

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
}
