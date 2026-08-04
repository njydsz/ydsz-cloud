package com.remisoft.common.web.auth;

import java.util.Collections;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.remisoft.common.auth.handler.AuthHandler;
import com.remisoft.common.domain.enums.ServiceType;

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
 * @author remi-team
 * @see AuthHandler
 * @see ServiceType
 * @since 1.0.0
 */
@Component
public class AuthHandlerFactory {

    private static final String WEB_HANDLER_BEAN_NAME = "webAuthHandler";
    private static final String APP_HANDLER_BEAN_NAME = "appAuthHandler";

    private final Map<String, AuthHandler> authHandlerMap;

    public AuthHandlerFactory(Map<String, AuthHandler> authHandlerMap) {
        this.authHandlerMap = authHandlerMap == null ? Collections.emptyMap() : authHandlerMap;
    }

    /**
     * 根据服务类型获取对应的认证处理器，支持多级回退。
     *
     * <p>路由规则：{@code APP_SERVICE} → appAuthHandler，其余 → webAuthHandler；
     * 若指定 Bean 不存在，先回退到 webAuthHandler，再回退到容器中任意可用的 {@link AuthHandler}。
     * 全部缺失时抛出 {@link IllegalStateException}，提示未配置任何认证实现。
     *
     * @param serviceType 服务类型（WEB/APP）
     * @return 可用的认证处理器，不会为 null
     * @throws IllegalStateException 当容器中无任何 AuthHandler 实现时
     */
    public AuthHandler getAuthHandler(ServiceType serviceType) {
        if (authHandlerMap.isEmpty()) {
            throw new IllegalStateException("未找到 AuthHandler 实现类");
        }

        String beanName = resolveBeanName(serviceType);

        AuthHandler handler = authHandlerMap.get(beanName);
        if (handler == null) {
            handler = authHandlerMap.get(WEB_HANDLER_BEAN_NAME);
        }
        if (handler == null) {
            handler = authHandlerMap.values().iterator().next();
        }
        if (handler == null) {
            throw new IllegalStateException(
                    "无法为服务类型 [" + serviceType + "] 获取可用的 AuthHandler");
        }
        return handler;
    }

    private String resolveBeanName(ServiceType serviceType) {
        if (serviceType == ServiceType.APP_SERVICE) {
            return APP_HANDLER_BEAN_NAME;
        }
        return WEB_HANDLER_BEAN_NAME;
    }
}
