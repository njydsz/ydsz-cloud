package com.njydsz.userinfo.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SCIM 2.0 用户供给配置属性。
 *
 * <p>集中管理 SCIM 2.0（RFC 7643/7644）用户供给接口的开关与策略参数，替代分散的 {@code @Value} 硬编码常量。
 *
 * <p><b>配置前缀：</b>{@code ydsz.userinfo.scim}
 *
 * <p><b>配置分组：</b>
 *
 * <ul>
 *   <li><b>基础开关</b>：{@link #enabled}（全局开关）、{@link #basePath}（路径前缀）
 *   <li><b>认证</b>：{@link #authToken}（Bearer Token）
 *   <li><b>操作策略</b>：{@link #allowCreate}、{@link #allowUpdate}、{@link #allowDelete}、{@link #allowPatch}
 * </ul>
 *
 * <p><b>application.yml 示例：</b>
 *
 * <pre>
 * ydsz:
 *   userinfo:
 *     scim:
 *       enabled: true
 *       base-path: /scim/v2
 *       auth-token: ${SCIM_AUTH_TOKEN:change-me-in-production}
 *       allow-create: true
 *       allow-update: true
 *       allow-delete: false
 *       allow-patch: true
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.userinfo.scim")
public class ScimProperties {

  /** 是否启用 SCIM 2.0 用户供给接口。 */
  private boolean enabled = false;

  /** SCIM 接口基础路径。 */
  private String basePath = "/scim/v2";

  /**
   * Bearer Token 认证令牌。
   *
   * <p>SCIM 客户端在请求时需在 Authorization 头中携带此令牌（格式：Bearer &lt;token&gt;）。
   * 建议通过环境变量注入，避免硬编码在配置文件中。
   */
  private String authToken;

  /** 是否允许通过 SCIM 创建用户。 */
  private boolean allowCreate = true;

  /** 是否允许通过 SCIM 更新用户（PUT 全量更新）。 */
  private boolean allowUpdate = true;

  /**
   * 是否允许通过 SCIM 删除用户。
   *
   * <p>删除操作执行逻辑删除（设置 deleted=true），非物理删除。
   */
  private boolean allowDelete = false;

  /** 是否允许通过 SCIM PATCH 进行部分更新（RFC 7644 Section 3.5.2）。 */
  private boolean allowPatch = true;
}
