package com.njydsz.gateway.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import lombok.Data;

/**
 * P3-7: 网关层粗粒度鉴权配置属性（RBAC）
 *
 * <p>支持基于路径的角色访问控制，在网关层拦截无权限请求，减少下游无效请求。
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     authorization:
 *       enabled: true
 *       # 路径 → 所需角色映射（Ant 风格路径匹配）
 *       path-roles:
 *         "/api/admin/**":
 *           - ROLE_ADMIN
 *         "/api/manager/**":
 *           - ROLE_ADMIN
 *           - ROLE_MANAGER
 *         "/api/user/**":
 *           - ROLE_ADMIN
 *           - ROLE_MANAGER
 *           - ROLE_USER
 *       # 是否允许拥有多个角色中的任意一个即可（默认 true）
 *       any-role-match: true
 *       # 是否记录鉴权失败日志
 *       log-failures: true
 * </pre>
 *
 * @since 3.7.0
 * @author ydsz-team
 */
@Data
@RefreshScope
@ConfigurationProperties(prefix = "ydsz.gateway.authorization")
public class AuthorizationProperties {

  /** 是否启用网关层鉴权 */
  private boolean enabled = false;

  /** 路径 → 所需角色列表映射（Ant 风格路径） */
  private Map<String, List<String>> pathRoles = Map.of();

  /** 是否允许拥有多个角色中的任意一个即可（true=OR，false=AND） */
  private boolean anyRoleMatch = true;

  /** 是否记录鉴权失败日志 */
  private boolean logFailures = true;
}
