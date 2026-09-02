package com.njydsz.common.web.auth;

import java.util.Collections;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.njydsz.common.auth.handler.AuthHandler;

/**
 * Web 端认证处理器工厂
 *
 * <p>支持根据服务类型编码动态路由到对应的 {@link AuthHandler} 实现。 通过 Spring 的依赖注入机制自动收集所有 {@link AuthHandler} 实现类， 并按
 * Bean 名称进行路由分发。
 *
 * <p><b>路由规则：</b>
 *
 * <ul>
 *   <li>{@code "appService"} → Bean 名称 {@code appAuthHandler}
 *   <li>其他（{@code "webService"} 等）→ Bean 名称 {@code webAuthHandler}
 *   <li>未匹配 → 回退到 {@code webAuthHandler}，再回退到任意可用实现
 * </ul>
 *
 * @author ydsz-team
 * @see AuthHandler
 * @since 26.09.01
 */
@Component
public class AuthHandlerFactory {

  private static final String WEB_HANDLER_BEAN_NAME = "webAuthHandler";
  private static final String APP_HANDLER_BEAN_NAME = "appAuthHandler";

  private static final String APP_SERVICE_CODE = "appService";

  private final Map<String, AuthHandler> authHandlerMap;

  public AuthHandlerFactory(Map<String, AuthHandler> authHandlerMap) {
    this.authHandlerMap = authHandlerMap == null ? Collections.emptyMap() : authHandlerMap;
  }

  /**
   * 根据服务类型编码获取对应的认证处理器，支持多级回退。
   *
   * <p>路由规则：{@code "appService"} → appAuthHandler，其余 → webAuthHandler； 若指定 Bean 不存在，先回退到
   * webAuthHandler，再回退到容器中任意可用的 {@link AuthHandler}。 全部缺失时抛出 {@link
   * IllegalStateException}，提示未配置任何认证实现。
   *
   * @param serviceTypeCode 服务类型编码
   * @return 可用的认证处理器，不会为 null
   * @throws IllegalStateException 当容器中无任何 AuthHandler 实现时
   */
  public AuthHandler getAuthHandler(String serviceTypeCode) {
    if (authHandlerMap.isEmpty()) {
      throw new IllegalStateException("未找到 AuthHandler 实现类");
    }

    String beanName = resolveBeanName(serviceTypeCode);

    AuthHandler handler = authHandlerMap.get(beanName);
    if (handler == null) {
      handler = authHandlerMap.get(WEB_HANDLER_BEAN_NAME);
    }
    if (handler == null) {
      handler = authHandlerMap.values().iterator().next();
    }
    if (handler == null) {
      throw new IllegalStateException("无法为服务类型 [" + serviceTypeCode + "] 获取可用的 AuthHandler");
    }
    return handler;
  }

  private String resolveBeanName(String serviceTypeCode) {
    if (APP_SERVICE_CODE.equals(serviceTypeCode)) {
      return APP_HANDLER_BEAN_NAME;
    }
    return WEB_HANDLER_BEAN_NAME;
  }
}
