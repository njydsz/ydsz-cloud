package com.njydsz.userinfo.server.provision;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.provision.IdentityProvisionConnector;

/**
 * 身份供给连接器注册表（P0-1 Identity Provisioning 管道）。
 *
 * <p>运行时管理所有已注册的 {@link IdentityProvisionConnector} 实例，
 * 支持动态注册/注销。JDBC、LDAP、SCIM等多种连接器通过本注册表统一管理。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>线程安全：使用 {@link ConcurrentHashMap} 保证并发注册/查询安全</li>
 *   <li>连接器类型唯一：重复注册时覆盖（后注册优先）并记录 WARN 日志</li>
 *   <li>Spring 自动注入：所有 {@code IdentityProvisionConnector} 类型的 Bean 自动注册</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@Slf4j
@Component
public class ProvisionConnectorRegistry {

  /** 连接器类型 → 连接器实例映射 */
  private final Map<String, IdentityProvisionConnector> connectors = new ConcurrentHashMap<>();

  /**
   * 注册身份供给连接器。
   *
   * <p>如果该类型已存在连接器，将被覆盖（后注册优先），并记录 WARN 日志。
   *
   * @param connector 连接器实例，不可为 null
   * @throws IllegalArgumentException connector 为 null 或 type 为空时抛出
   */
  public void register(IdentityProvisionConnector connector) {
    if (connector == null) {
      throw new IllegalArgumentException("IdentityProvisionConnector must not be null");
    }
    String type = connector.getConnectorType();
    if (type == null || type.isBlank()) {
      throw new IllegalArgumentException("IdentityProvisionConnector type must not be blank");
    }
    IdentityProvisionConnector previous = connectors.put(type.toUpperCase(), connector);
    if (previous != null) {
      log.warn("ProvisionConnector 被覆盖注册: type={}, 旧={}, 新={}",
          type, previous.getClass().getSimpleName(), connector.getClass().getSimpleName());
    } else {
      log.info("ProvisionConnector 注册成功: type={}, class={}",
          type, connector.getClass().getSimpleName());
    }
  }

  /**
   * 注销指定类型的连接器。
   *
   * @param type 连接器类型
   * @return 被移除的连接器，不存在时返回 null
   */
  public IdentityProvisionConnector unregister(String type) {
    if (type == null || type.isBlank()) {
      return null;
    }
    IdentityProvisionConnector removed = connectors.remove(type.toUpperCase());
    if (removed != null) {
      log.info("ProvisionConnector 注销: type={}", type);
    }
    return removed;
  }

  /**
   * 获取指定类型的连接器。
   *
   * @param type 连接器类型
   * @return 连接器实例，不存在时返回 null
   */
  public IdentityProvisionConnector getConnector(String type) {
    if (type == null || type.isBlank()) {
      return null;
    }
    return connectors.get(type.toUpperCase());
  }

  /**
   * 获取所有已注册的连接器列表。
   *
   * @return 连接器列表（不可修改快照）
   */
  public List<IdentityProvisionConnector> getAllConnectors() {
    return new ArrayList<>(connectors.values());
  }

  /**
   * 获取所有已注册的连接器类型。
   *
   * @return 连接器类型集合
   */
  public List<String> getRegisteredTypes() {
    return connectors.keySet().stream().sorted().collect(Collectors.toList());
  }

  /**
   * 判断指定类型是否已注册连接器。
   *
   * @param type 连接器类型
   * @return true 表示已注册
   */
  public boolean isRegistered(String type) {
    return type != null && connectors.containsKey(type.toUpperCase());
  }

  /**
   * 获取已注册的连接器数量。
   *
   * @return 连接器数量
   */
  public int size() {
    return connectors.size();
  }
}
