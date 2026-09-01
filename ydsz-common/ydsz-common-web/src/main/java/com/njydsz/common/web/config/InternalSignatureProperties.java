package com.njydsz.common.web.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内部签名验签配置项（P0-3）。
 *
 * <p>前缀：{@code ydsz.security.internal-sign}
 *
 * <h3>配置项</h3>
 *
 * <ul>
 *   <li>{@code enabled}：是否启用内部签名校验，默认 {@code false}（灰度上线开关：
 *       密钥经 Nacos 加密配置下发、网关与各服务对齐后再开启）</li>
 *   <li>{@code secret}：HMAC-SHA256 签名密钥，必须与网关 {@code ydsz.gateway.internal-sign-secret}
 *       一致；建议经 jasypt 加密存储</li>
 *   <li>{@code enforcePaths}：强制验签的路径模式（Ant 风格），默认覆盖
 *       {@code /api/internal/**} 与 {@code /feign/**} 两类内部接口</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@ConfigurationProperties(prefix = "ydsz.security.internal-sign")
public class InternalSignatureProperties {

  /** 是否启用内部签名校验（默认关闭，密钥对齐后灰度开启） */
  private boolean enabled = false;

  /** HMAC-SHA256 签名密钥（与网关 ydsz.gateway.internal-sign-secret 一致） */
  private String secret = "";

  /** 强制验签的路径模式（Ant 风格） */
  private List<String> enforcePaths = new ArrayList<>(List.of("/api/internal/**", "/feign/**"));

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public List<String> getEnforcePaths() {
    return enforcePaths;
  }

  public void setEnforcePaths(List<String> enforcePaths) {
    this.enforcePaths = enforcePaths;
  }
}
