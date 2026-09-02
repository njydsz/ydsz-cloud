package com.njydsz.common.auth.config.AuthFilterProperties;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 鉴权过滤器配置属性
 *
 * <p>绑定前缀 ydsz.auth.filter.* 的白名单与忽略路径配置，
 * 用于控制认证过滤器对接口的 Token 校验行为与权限校验开关。
 *
 * <p>配置模式：
 * <pre>
 * ydsz.auth.filter:
 *   common-ignore-url: /health,/actuator/**
 *   gateway-ignore-url: /api/open/**
 *   custom-ignore-url: /swagger-ui/**
 *   verify-permission: true
 *   only-verify-token: /api/inner/**
 * </pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@ConfigurationProperties(prefix = "ydsz.auth.filter")
public class AuthFilterProperties {

  /** 通用忽略路径（不校验 Token），如 /health,/actuator/health */
  private List<String> commonIgnoreUrl = new ArrayList<>();

  /** 网关转发路径（不校验 Token，仅通过网关放行），如 /api/open/** */
  private List<String> gatewayIgnoreUrl = new ArrayList<>();

  /** 自定义忽略路径（业务方手动配置），优先级高于通用忽略 */
  private List<String> customIgnoreUrl = new ArrayList<>();

  /** 是否启用权限校验（RBAC），默认 true；false 时仅校验 Token 合法性 */
  private Boolean verifyPermission = true;

  /** 仅校验 Token 但不校验权限的路径，如 /api/inner/** */
  private List<String> onlyVerifyToken = new ArrayList<>(4);
}
