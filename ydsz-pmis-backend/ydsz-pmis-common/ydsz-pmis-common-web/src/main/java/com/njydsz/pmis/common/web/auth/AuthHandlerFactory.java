package com.njydsz.pmis.common.web.auth;

import java.util.Collections;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.njydsz.pmis.common.auth.handler.AuthHandler;
import com.njydsz.pmis.common.core.enums.ServiceType;

/**
 * Web 端认证处理器工厂
 *
 * <p>支持根据 {@link ServiceType} 动态路由到对应的 {@link AuthHandler} 实现。
 * 通过 Spring 的依赖注入机制自动收集所有 {@link AuthHandler} 实现类，
 * 并按 Bean 名称进行路由分发。
 *
 * <p><b>路由规则：</b>
 * <ul>
 *   <li>{@code WEB_SERVICE} → Bean 名称 {@code webAuthHandler}</li>
 *   <li>{@code APP_SERVICE} → Bean 名称 {@code appAuthHandler}</li>
 *   <li>未匹配 → 回退到 {@code webAuthHandler}，再回退到任意可用实现</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see AuthHandler
 * @see ServiceType
 */
@Component
public class AuthHandlerFactory {

    /** 所有已注册的 AuthHandler 实现，key 为 Bean 名称 */
    private final Map<String, AuthHandler> authHandlerMap;

    /**
     * 构造认证处理器工厂
     *
     * @param authHandlerMap Spring 注入的 AuthHandler Bean 映射
     */
    public AuthHandlerFactory(Map<String, AuthHandler> authHandlerMap) {
        this.authHandlerMap = authHandlerMap == null ? Collections.emptyMap() : authHandlerMap;
    }

    /**
     * 根据服务类型获取对应的 AuthHandler
     *
     * @param serviceType 服务类型枚举
     * @return 对应的 AuthHandler 实例
     * @throws IllegalStateException 未找到可用的 AuthHandler 时抛出
     */
    public AuthHandler getAuthHandler(ServiceType serviceType) {
        if (authHandlerMap == null || authHandlerMap.isEmpty()) {
            throw new IllegalStateException("未找到 AuthHandler 实现类");
        }

        String beanName = resolveBeanName(serviceType);

        AuthHandler handler = authHandlerMap.get(beanName);
        if (handler == null) {
            handler = authHandlerMap.get("webAuthHandler");
        }
        if (handler == null && !authHandlerMap.isEmpty()) {
            handler = authHandlerMap.values().iterator().next();
        }
        if (handler == null) {
            throw new IllegalStateException(
                    "无法为服务类型 [" + serviceType + "] 获取可用的 AuthHandler");
        }
        return handler;
    }

    /**
     * 根据服务类型解析对应的 Bean 名称
     *
     * @param serviceType 服务类型
     * @return Bean 名称
     */
    private String resolveBeanName(ServiceType serviceType) {
        if (serviceType == ServiceType.WEB_SERVICE) {
            return "webAuthHandler";
        }
        if (serviceType == ServiceType.APP_SERVICE) {
            return "appAuthHandler";
        }
        return "webAuthHandler";
    }
}
