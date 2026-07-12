package com.njydsz.pmis.common.app.auth;

import com.njydsz.pmis.common.core.enums.ServiceType;
import com.njydsz.pmis.common.auth.handler.AuthHandler;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * 认证处理器工厂
 *
 * <p>根据服务类型 {@link ServiceType} 获取对应的认证处理器 {@link AuthHandler} 实例。
 *
 * <p>该工厂类通过 Spring 依赖注入获取所有 {@link AuthHandler} 实现，
 * 根据传入的服务类型返回对应的处理器。
 * 若未找到对应类型的处理器，则按以下兜底策略返回：
 * <ol>
 *   <li>若服务类型为 {@link ServiceType#APP_SERVICE}，尝试获取名为 {@code appAuthHandler} 的处理器</li>
 *   <li>若未找到，返回第一个可用的处理器</li>
 *   <li>若没有任何处理器可用，抛出 {@link IllegalStateException}</li>
 * </ol>
 *
 * <p>设计理念：采用策略模式与简单工厂模式结合，
 * 通过服务类型作为 key 动态选择认证处理策略。
 *
 * <p><b>线程安全性：</b>无状态 Bean，线程安全。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 * @see AuthHandler
 * @see ServiceType
 * @see AppAuthHandler
 */
@Component
public class AuthHandlerFactory {

    /** 默认兜底 App 认证处理器 Bean 名称 */
    private static final String APP_AUTH_HANDLER_BEAN_NAME = "appAuthHandler";

    /** Spring 注入的所有 {@link AuthHandler} 实例映射，Key 为 Bean 名称 */
    private final Map<String, AuthHandler> authHandlerMap;

    /**
     * 构造方法。
     *
     * <p>注意：Spring 注入的 beanMap 通常为 {@link java.util.concurrent.ConcurrentHashMap}，
     * 其迭代顺序不保证插入顺序。因此 {@link #getFirstAvailableHandler()} 返回的"第一个"处理器
     * 在不同 JVM 启动中可能不同。如有确定性需求，建议使用 {@link java.util.LinkedHashMap} 或
     * 通过 {@code @Order} 注解显式指定优先级。
     *
     * @param authHandlerMap Spring 注入的 AuthHandler 映射，允许为空
     */
    public AuthHandlerFactory(Map<String, AuthHandler> authHandlerMap) {
        this.authHandlerMap = Optional.ofNullable(authHandlerMap).orElse(Map.of());
    }

    /**
     * 根据服务类型获取对应的认证处理器
     *
     * @param serviceType 服务类型枚举
     * @return 对应的认证处理器实例
     * @throws IllegalStateException 当没有可用的认证处理器时抛出
     */
    public AuthHandler getAuthHandler(ServiceType serviceType) {
        if (authHandlerMap.isEmpty()) {
            throw new IllegalStateException("认证处理器映射为空，请检查 AuthHandler 实现类的注册情况");
        }

        if (serviceType == ServiceType.APP_SERVICE) {
            return Optional.ofNullable(authHandlerMap.get(APP_AUTH_HANDLER_BEAN_NAME))
                    .orElseGet(this::getFirstAvailableHandler);
        }

        return getFirstAvailableHandler();
    }

    /**
     * 获取第一个可用的认证处理器
     *
     * @return 第一个处理器实例
     * @throws IllegalStateException 当没有任何处理器可用时抛出
     */
    private AuthHandler getFirstAvailableHandler() {
        return authHandlerMap.values().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到任何可用的 AuthHandler 实现"));
    }
}
