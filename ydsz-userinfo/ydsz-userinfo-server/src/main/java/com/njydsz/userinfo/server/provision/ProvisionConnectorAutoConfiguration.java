package com.njydsz.userinfo.server.provision;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Configuration;

import com.njydsz.userinfo.domain.provision.IdentityProvisionConnector;

/**
 * 身份供给连接器自动注册配置（P0-1 Identity Provisioning 管道）。
 *
 * <p>在 Spring 容器初始化完成后，扫描所有 {@link IdentityProvisionConnector} 类型的 Bean，
 * 自动注册到 {@link ProvisionConnectorRegistry} 中。
 *
 * <p>实现 {@link SmartInitializingSingleton} 而非 {@code @PostConstruct}，
 * 确保所有 Singleton Bean 都已实例化后再执行注册（包括延迟初始化的连接器 Bean）。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Configuration
public class ProvisionConnectorAutoConfiguration implements SmartInitializingSingleton {

  private final ProvisionConnectorRegistry registry;
  private final Map<String, IdentityProvisionConnector> connectorBeans;

  /**
   * 构造自动注册配置。
   *
   * <p>Spring 自动注入所有 {@code IdentityProvisionConnector} 实现 Bean。
   *
   * @param registry 连接器注册表
   * @param connectorBeans Spring 容器中所有的连接器 Bean
   */
  public ProvisionConnectorAutoConfiguration(
      ProvisionConnectorRegistry registry,
      Map<String, IdentityProvisionConnector> connectorBeans) {
    this.registry = registry;
    this.connectorBeans = connectorBeans;
  }

  /**
   * 在所有 Singleton Bean 实例化完成后执行，注册所有连接器到注册表。
   */
  @Override
  public void afterSingletonsInstantiated() {
    if (connectorBeans.isEmpty()) {
      log.info("未发现任何 IdentityProvisionConnector Bean");
      return;
    }
    for (IdentityProvisionConnector connector : connectorBeans.values()) {
      try {
        registry.register(connector);
      } catch (Exception e) {
        log.error("注册 ProvisionConnector 异常: type={}, error={}",
            connector.getConnectorType(), e.getMessage(), e);
      }
    }
    log.info("ProvisionConnector 自动注册完成: count={}, types={}",
        registry.size(), registry.getRegisteredTypes());
  }
}
