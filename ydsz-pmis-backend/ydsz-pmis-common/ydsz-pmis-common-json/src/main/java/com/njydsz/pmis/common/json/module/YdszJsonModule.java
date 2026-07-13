package com.njydsz.pmis.common.json.module;

/**
 * YdszJson 模块接口
 *
 * <p>参考 Jackson Module 设计，提供可插拔的序列化/反序列化扩展机制。
 *
 * <p><b>功能特性：</b>
 * <ul>
 *   <li>模块化注册 - 类似 Jackson Module，可自由组合</li>
 *   <li>类型绑定 - 为指定类型注册序列化器和反序列化器</li>
 *   <li>自动发现 - Spring Boot 环境下自动发现并注册模块</li>
 *   <li>优先级控制 - 支持设置模块优先级，优先级高的先注册</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>
 * // 1. 创建自定义模块
 * public class UserModule implements YdszJsonModule {
 *     {@code @Override}
 *     public String getModuleName() {
 *         return "userModule";
 *     }
 *
 *     {@code @Override}
 *     public void setSerializers(ModuleSerializerRegistry registry) {
 *         registry.register(User.class, new UserSerializer());
 *     }
 *
 *     {@code @Override}
 *     public void setDeserializers(ModuleDeserializerRegistry registry) {
 *         registry.register(User.class, new UserDeserializer());
 *     }
 * }
 *
 * // 2. Spring Boot 自动注册（实现 SpringFactory 接口）
 * {@code @Component}
 * public class UserModule implements YdszJsonModule, YdszJsonModule.SpringFactory {
 *     // ...
 * }
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface YdszJsonModule {

    /**
     * 获取模块名称
     *
     * @return 模块名称
     */
    String getModuleName();

    /**
     * 设置序列化器
     *
     * <p>在此方法中注册该模块提供的所有自定义序列化器</p>
     *
     * @param registry 序列化器注册表
     */
    default void setSerializers(ModuleSerializerRegistry registry) {
    }

    /**
     * 设置反序列化器
     *
     * <p>在此方法中注册该模块提供的所有自定义反序列化器</p>
     *
     * @param registry 反序列化器注册表
     */
    default void setDeserializers(ModuleDeserializerRegistry registry) {
    }

    /**
     * 获取模块版本
     *
     * @return 模块版本
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * 获取模块优先级
     *
     * <p>值越大优先级越高，优先级高的模块先注册
     *
     * @return 模块优先级，默认 0
     */
    default int getPriority() {
        return 0;
    }

    /**
     * 是否在所有类型注册完成后调用
     *
     <p>如果返回 true，则在所有模块注册完成后调用 {@link #onRegisterComplete()}
     *
     * @return 是否在所有类型注册完成后调用
     */
    default boolean needsCompleteRegistration() {
        return false;
    }

    /**
     * 所有类型注册完成后的回调
     *
     * <p>当所有模块都完成注册后调用，可用于模块间的依赖处理</p>
     */
    default void onRegisterComplete() {
    }

    /**
     * Spring 工厂接口
     *
     * <p>实现此接口的模块会在 Spring Boot 环境下自动注册</p>
     */
    interface SpringFactory {
    }
}
