package com.njydsz.userinfo.server.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.sentry.adapter.SentryMetricsAdapter;

/**
 * Userinfo module Micrometer metrics.
 *
 * <p>继承 {@link SentryMetricsAdapter}，统一指标前缀 {@code ydsz_userinfo_}， 消除手动 Counter/Timer/Gauge
 * 创建样板代码。
 *
 * <p>Metric naming follows Micrometer convention (dots converted to underscores by Prometheus):
 *
 * <ul>
 *   <li>{@code ydsz_userinfo_logins_total{result=success|fail}} — 登录成功/失败计数
 *   <li>{@code ydsz_userinfo_auth_duration_ms} — 认证耗时分布（P50/P90/P99）
 *   <li>{@code ydsz_userinfo_online_sessions} — 在线会话数（Gauge，读自 Redis 计数器）
 * </ul>
 *
 * <p><b>在线会话计数策略（P1-1）：</b>
 *
 * <p>使用 Redis 原子计数器 {@code userinfo:session:total} 维护全局活跃会话总数，支持多实例 部署场景下的准确统计。登录成功时 INCR，登出/驱逐时
 * DECR。Gauge 读取该计数器值， 消除单节点 {@code AtomicLong} 无法跨实例聚合的问题。
 *
 * <p><b>1.0.0 变更</b>：删除 MeterRegistry 构造参数，改为继承 SentryMetricsAdapter 通过 MetricsCollector SPI
 * 注册指标，符合《云顶编码规范》第 27.2.1 节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnClass(MeterRegistry.class)
public class UserInfoMetrics extends SentryMetricsAdapter {

  /** Redis 在线会话总数计数器 Key */
  private static final String SESSION_TOTAL_KEY = "userinfo:session:total";

  /**
   * P0-8: 会话计数器防下溢 Lua 脚本。
   *
   * <p>登出时仅在计数器大于 0 时 DECR，避免并发登出导致计数器变为负数，破坏在线会话 Gauge 的准确性。
   */
  private static final String DECR_IF_POSITIVE_LUA =
      "local v = tonumber(redis.call('GET', KEYS[1]) or '0') "
          + "if v > 0 then return redis.call('DECR', KEYS[1]) else return 0 end";

  private final RedisStringOps redisStringOps;

  public UserInfoMetrics(RedisStringOps redisStringOps) {
    super("ydsz_userinfo_");
    this.redisStringOps = redisStringOps;
    log.info("[UserInfoMetrics] Micrometer 指标初始化完成");
  }

  /**
   * 从 Redis 计数器读取当前在线会话总数。
   *
   * <p>读取 {@code userinfo:session:total} 计数器值，读取失败时返回 0（不影响监控链路）。
   *
   * @return 当前在线会话总数，Redis 不可用时返回 0
   */
  private double getOnlineSessionsFromRedis() {
    try {
      String value = redisStringOps.get(SESSION_TOTAL_KEY, String.class);
      if (value == null || value.isBlank()) {
        return 0.0;
      }
      return Double.parseDouble(value);
    } catch (Exception e) {
      log.warn("Failed to read online sessions from Redis, error={}", e.getMessage());
      return 0.0;
    }
  }

  /**
   * 记录一次登录成功。
   *
   * <p>累加 {@code ydsz_userinfo_logins_total{result=success}} 计数器，并将在线会话 Redis 计数器 {@code
   * userinfo:session:total} INCR +1。应在登录链路「鉴权通过且会话已建立」 之后调用；与 {@link #recordLogout()} 配对使用。
   */
  public void recordLoginSuccess() {
    incrementCounter("logins_total", "result", "success");
    try {
      redisStringOps.incr(SESSION_TOTAL_KEY, 1L);
    } catch (Exception e) {
      log.warn("Failed to increment online session counter, error={}", e.getMessage());
    }
  }

  /**
   * 记录一次登录失败（鉴权不通过 / 账户锁定 / 风控拦截等）。
   *
   * <p>仅累加 {@code ydsz_userinfo_logins_total{result=fail}} 计数器；<b>不</b>改变在线 会话 Gauge ——
   * 失败意味着未建立会话，故不应与 {@link #recordLogout()} 配对。
   */
  public void recordLoginFail() {
    incrementCounter("logins_total", "result", "fail");
  }

  /**
   * 记录一次登出，将在线会话 Redis 计数器 DECR -1。
   *
   * <p>应在登出成功路径调用，并与一次成功登录配对。 P0-8: 使用 Lua 脚本防下溢（计数器为 0 时不再递减）；
   * P2-3: 同时累加 {@code ydsz_userinfo_logouts_total{result=success}} 计数器，用于登出行为分析。
   */
  public void recordLogout() {
    incrementCounter("logouts_total", "result", "success");
    try {
      redisStringOps.executeScriptWithShaCache(
          DECR_IF_POSITIVE_LUA,
          Long.class,
          java.util.Collections.singletonList(SESSION_TOTAL_KEY));
    } catch (Exception e) {
      log.warn("Failed to decrement online session counter, error={}", e.getMessage());
    }
  }

  /**
   * 开始一次认证耗时采样。
   *
   * <p>返回 {@link Timer.Sample} 句柄，需在认证逻辑结束处交给 {@link #stopTimer(Timer.Sample)} 关闭，从而记录 {@code
   * ydsz_userinfo_auth_duration_ms} 的 P50/P90/P99 分布。每次调用 新建独立采样，线程安全、无共享状态；采样句柄不可跨线程复用后再 stop。
   *
   * @return 认证耗时采样句柄（非 null），须交由 {@link #stopTimer} 关闭
   */
  public Timer.Sample startTimer() {
    return Timer.start();
  }

  /**
   * 结束一次认证耗时采样并记录到 {@code ydsz_userinfo_auth_duration_ms}。
   *
   * <p>由 {@link #startTimer()} 返回的 {@code sample} 必须来自同一次请求且<b>未</b>已被 stop， 重复 stop 会触发 Micrometer
   * 重复记录告警。采样本身线程安全，但同一个 {@code sample} 不应被并发 stop。
   *
   * @param sample 来自 {@link #startTimer()} 的采样句柄，不可为 null（为 null 将抛 NPE）
   */
  public void stopTimer(Timer.Sample sample) {
    sample.stop(timer("auth_duration_ms"));
  }

  /**
   * 更新在线会话数 Gauge。
   *
   * <p>由定时任务或登录/登出事件触发，将当前在线会话数更新到 Prometheus Gauge。
   */
  public void updateOnlineSessionsGauge() {
    gauge("online_sessions", getOnlineSessionsFromRedis());
  }

  /**
   * 记录缓存命中/失败次数（通用方法，供外部调用）。
   *
   * @param name 指标名称（不含前缀）
   * @param result 结果标识（如 hit/miss/success/fail）
   */
  public void recordCacheResult(String name, String result) {
    incrementCounter(name, "result", result);
  }

  /**
   * 记录 HTTP 请求计数（通用方法，供外部调用）。
   *
   * @param name 指标名称（不含前缀）
   * @param tags 标签键值对（偶数个参数：key1, value1, key2, value2...）
   */
  public void recordHttpCount(String name, String... tags) {
    incrementCounter(name, tags);
  }

  /**
   * 记录耗时（通用方法，供外部调用）。
   *
   * @param name 指标名称（不含前缀）
   * @param durationMs 耗时毫秒数
   * @param tags 标签键值对
   */
  public void recordTimer(String name, long durationMs, String... tags) {
    super.recordTimer(name, durationMs, tags);
  }

  // ==================== SSO 协议指标 ====================

  /**
   * 记录 SSO 登录事件（按协议分类）。
   *
   * <p>支持协议：{@code cas} / {@code oauth2} / {@code saml} / {@code oidc}。
   *
   * @param protocol SSO 协议标识
   * @param result 结果：success / fail
   */
  public void recordSsoLogin(String protocol, String result) {
    incrementCounter("sso_logins_total", "protocol", protocol, "result", result);
  }

  /**
   * 记录 CAS TGT 签发。
   *
   * <p>累加 {@code ydsz_userinfo_cas_tgt_issued_total} 计数器。
   */
  public void recordCasTgtIssued() {
    incrementCounter("cas_tgt_issued_total");
  }

  /**
   * 记录 CAS ST 签发。
   *
   * <p>累加 {@code ydsz_userinfo_cas_st_issued_total} 计数器。
   */
  public void recordCasStIssued() {
    incrementCounter("cas_st_issued_total");
  }

  /**
   * 记录 CAS ST 校验。
   *
   * @param result 结果：success / fail
   */
  public void recordCasStValidation(String result) {
    incrementCounter("cas_st_validations_total", "result", result);
  }

  /**
   * 记录 OAuth2 Token 签发。
   *
   * @param grantType 授权类型：authorization_code / refresh_token / client_credentials
   */
  public void recordOAuth2TokenIssued(String grantType) {
    incrementCounter("oauth2_tokens_issued_total", "grant_type", grantType);
  }

  /**
   * 记录 SAML 认证事件。
   *
   * @param result 结果：success / fail
   */
  public void recordSamlAuth(String result) {
    incrementCounter("saml_auth_total", "result", result);
  }

  /**
   * 记录 OIDC ID Token 签发。
   *
   * <p>累加 {@code ydsz_userinfo_oidc_id_tokens_issued_total} 计数器。
   */
  public void recordOidcIdTokenIssued() {
    incrementCounter("oidc_id_tokens_issued_total");
  }

  /**
   * 记录社交登录事件。
   *
   * @param platform 平台标识：wechat / dingtalk / feishu / github
   * @param result 结果：success / fail
   */
  public void recordSocialLogin(String platform, String result) {
    incrementCounter("social_logins_total", "platform", platform, "result", result);
  }

  /**
   * 记录 WebAuthn 认证事件。
   *
   * @param result 结果：success / fail
   */
  public void recordWebAuthnAuth(String result) {
    incrementCounter("webauthn_auth_total", "result", result);
  }

  /**
   * 更新 CAS 活跃 TGT 数 Gauge。
   *
   * <p>从 Redis 读取当前活跃的 TGT 数量，用于监控 CAS 会话规模。
   *
   * @param count 活跃 TGT 数量
   */
  public void updateCasActiveTgtGauge(long count) {
    gauge("cas_active_tgt", count);
  }

  /**
   * 更新 OAuth2 活跃 Token 数 Gauge。
   *
   * @param count 活跃 Token 数量
   */
  public void updateOAuth2ActiveTokenGauge(long count) {
    gauge("oauth2_active_tokens", count);
  }
}
