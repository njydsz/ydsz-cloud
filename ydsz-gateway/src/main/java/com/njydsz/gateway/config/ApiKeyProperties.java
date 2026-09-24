package com.njydsz.gateway.config;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关 API Key 认证配置属性（{@code ydsz.gateway.api-key.*}，B3 优化）。
 *
 * <p>替代 GatewayApiKeyAuthFilter 中分散的 {@code @Value} 注入，提供类型安全的配置绑定与 IDE 自动补全。
 *
 * <h3>热更新机制</h3>
 *
 * <p>通过 Nacos 配置 {@code ydsz.gateway.api-key.keys} 修改后，需配合 Nacos 监听器或 Spring Cloud
 * {@code @RefreshScope}（本模块未引入 refresh scope，建议通过 Nacos 路由配置变更事件联动）。
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     api-key:
 *       enabled: true
 *       keys: "key1,key2,key3"
 *       protected-paths: "/api/project/**,/api/workflow/**"
 * </pre>
 *
 * @since 26.09.23
 * @author ydsz-team
 * @see com.njydsz.gateway.filter.GatewayApiKeyAuthFilter
 */
@ConfigurationProperties(prefix = "ydsz.gateway.api-key")
public class ApiKeyProperties {

  /** 是否启用 API Key 认证过滤器。默认 false（JWT 为主）。 */
  private boolean enabled;

  /** API Key 白名单列表（逗号分隔配置文件自动绑定）。运行期仅持有该列表引用，实际校验使用摘要集合。 */
  private List<String> keys = Collections.emptyList();

  /** 需要 API Key 校验的路径模式列表（逗号分隔）。 */
  private List<String> protectedPaths = Collections.emptyList();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public List<String> getKeys() {
    return keys;
  }

  public void setKeys(List<String> keys) {
    this.keys = keys != null ? Collections.unmodifiableList(keys) : Collections.emptyList();
  }

  public List<String> getProtectedPaths() {
    return protectedPaths;
  }

  public void setProtectedPaths(List<String> protectedPaths) {
    this.protectedPaths = protectedPaths != null
        ? Collections.unmodifiableList(protectedPaths)
        : Collections.emptyList();
  }

  /**
   * 返回有效的 API Key 集合（非空、非空白），剔除 null 和空白字符串。
   *
   * @return 处理后的 API Key 列表
   */
  public List<String> getValidKeys() {
    return keys.stream()
        .filter(key -> key != null && !key.isBlank())
        .map(String::trim)
        .toList();
  }

  /**
   * 返回有效的路径模式集合。
   *
   * @return 处理后的路径模式列表
   */
  public Set<String> getValidPathsAsSet() {
    return protectedPaths.stream()
        .filter(path -> path != null && !path.isBlank())
        .map(String::trim)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * 检查 API Key 认证是否可用（启用 + 至少配置一个 Key）。
   *
   * @return true=可正常执行 API Key 认证
   */
  public boolean isAvailable() {
    return enabled && !getValidKeys().isEmpty();
  }
}
