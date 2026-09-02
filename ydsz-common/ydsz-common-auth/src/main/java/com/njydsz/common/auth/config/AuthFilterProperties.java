package com.njydsz.common.auth.config;.config
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
  private List<String> commonIgnoreUrl = new ArrayList<>(4);