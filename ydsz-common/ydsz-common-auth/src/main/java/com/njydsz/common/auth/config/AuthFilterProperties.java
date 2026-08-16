package com.njydsz.common.auth.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证过滤器配置属性
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.auth.filter")
public class AuthFilterProperties {

  private List<String> commonIgnoreUrl = new ArrayList<>();

  private List<String> gatewayIgnoreUrl = new ArrayList<>();

  private List<String> customIgnoreUrl = new ArrayList<>();

  private Boolean verifyPermission = true;

  private List<String> onlyVerifyToken = new ArrayList<>();
}
