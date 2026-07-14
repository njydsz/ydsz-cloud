package com.njydsz.pmis.common.util.spring;

import java.util.Map;
import java.util.Objects;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文持有者
 *
 * <p>提供全局静态方法和实例方法访问 Spring ApplicationContext，
 * 支持通过实现 ApplicationContextAware 自动初始化。
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
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
@Component
@Order(Integer.MIN_VALUE)
public class SpringContextHolder implements ApplicationContextAware {

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringBeanUtils.setApplicationContext(applicationContext);
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
        return SpringBeanUtils.getBean(name);
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
        return SpringBeanUtils.getBean(clazz);
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
        return SpringBeanUtils.getBean(name, clazz);
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
        return SpringBeanUtils.getBeansOfType(clazz);
    }

    // ==================== 静态方法（向后兼容） ====================

    /**
     * 根据名称获取 Bean
     *
     * @param name Bean 名称
     * @return Bean 实例
     * @throws NullPointerException     如果 Bean 名称为空
     * @throws IllegalStateException    如果 ApplicationContext 未初始化
     */
    public static Object getBean(String name) {
        Objects.requireNonNull(name, "Bean 名称不能为空");
        return SpringBeanUtils.getBean(name);
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
    public static <T> T getBean(Class<T> clazz) {
        Objects.requireNonNull(clazz, "Bean 类型不能为空");
        return SpringBeanUtils.getBean(clazz);
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
    public static <T> T getBean(String name, Class<T> clazz) {
        Objects.requireNonNull(name, "Bean 名称不能为空");
        Objects.requireNonNull(clazz, "Bean 类型不能为空");
        return SpringBeanUtils.getBean(name, clazz);
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
    public static <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        Objects.requireNonNull(clazz, "Bean 类型不能为空");
        return SpringBeanUtils.getBeansOfType(clazz);
    }

    /**
     * 检查 Spring ApplicationContext 是否已初始化
     *
     * @return 如果已初始化返回 true，否则返回 false
     */
    public static boolean isInitialized() {
        return SpringBeanUtils.getApplicationContextInternal() != null;
    }
}
