package com.njydsz.userinfo.web.controller;

import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * SSO 指标控制器。
 *
 * <p>为 Prometheus/Grafana 提供 SSO 相关指标数据，用于构建 SSO 监控仪表盘。
 *
 * <p><b>接口路径：</b>{@code /api/v1/sso/metrics}
 *
 * <p><b>指标说明：</b>
 *
 * <ul>
 *   <li>CAS TGT/ST 签发数</li>
 *   <li>OAuth2 Token 签发数</li>
 *   <li>SAML/OIDC 认证次数</li>
 *   <li>社交登录次数（按平台）</li>
 *   <li>WebAuthn 认证次数</li>
 *   <li>各协议活跃会话数</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.6.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sso/metrics")
@RequiredArgsConstructor
public class SsoMetricsController {

  /** Redis 中 CAS TGT 活跃数 Key */
  private static final String CAS_ACTIVE_TGT_KEY = "userinfo:cas:tgt:active:count";

  /** Redis 中 CAS ST 活跃数 Key */
  private static final String CAS_ACTIVE_ST_KEY = "userinfo:cas:st:active:count";

  /** Redis 中 OAuth2 Token 活跃数 Key */
  private static final String OAUTH2_ACTIVE_TOKENS_KEY = "userinfo:oauth2:token:active:count";

  private final RedisStringOps redisStringOps;

  /**
   * 获取 SSO 指标总览。
   *
   * <p>返回各协议的活跃会话数，用于 Grafana 仪表盘展示。
   *
   * @return SSO 指标数据
   */
  @GetMapping("/overview")
  public YdszResponse<SsoMetricsVO> getOverview() {
    SsoMetricsVO metrics = new SsoMetricsVO();

    // CAS 活跃会话
    metrics.setCasActiveTgt(getLongFromRedis(CAS_ACTIVE_TGT_KEY));
    metrics.setCasActiveSt(getLongFromRedis(CAS_ACTIVE_ST_KEY));

    // OAuth2 活跃 Token
    metrics.setOAuth2ActiveTokens(getLongFromRedis(OAUTH2_ACTIVE_TOKENS_KEY));

    // 协议分布
    Map<String, Long> protocolDistribution = new HashMap<>();
    protocolDistribution.put("cas", getLongFromRedis(CAS_ACTIVE_TGT_KEY));
    protocolDistribution.put("oauth2", getLongFromRedis(OAUTH2_ACTIVE_TOKENS_KEY));
    metrics.setProtocolDistribution(protocolDistribution);

    return YdszResponse.success(metrics);
  }

  /**
   * 从 Redis 读取长整型值。
   *
   * @param key Redis Key
   * @return 整型值，不存在返回 0
   */
  private long getLongFromRedis(String key) {
    try {
      String value = redisStringOps.get(key, String.class);
      if (value != null && !value.isBlank()) {
        return Long.parseLong(value);
      }
    } catch (Exception e) {
      log.warn("Failed to read metric from Redis: key={}, error={}", key, e.getMessage());
    }
    return 0;
  }

  /**
   * SSO 指标值对象。
   *
   * @author ydsz-team
   * @since 1.6.0
   */
  @lombok.Data
  public static class SsoMetricsVO {

    /** CAS 活跃 TGT 数 */
    private long casActiveTgt;

    /** CAS 活跃 ST 数 */
    private long casActiveSt;

    /** OAuth2 活跃 Token 数 */
    private long oauth2ActiveTokens;

    /** 协议分布（协议标识 → 活跃会话数） */
    private Map<String, Long> protocolDistribution;
  }
}
