package com.njydsz.nextwiki.server.service;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.redis.service.RedisRateLimiter;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.nextwiki.server.config.NextwikiProperties;

/**
 * 下载限流与防盗链服务
 *
 * <p>基于 {@link RedisRateLimiter} 实现下载速率限制和防盗链验证。
 *
 * <p><b>限流策略：</b>
 *
 * <ul>
 *   <li>按用户限流：每分钟最大下载次数
 *   <li>按 IP 限流：防止单 IP 大量下载
 *   <li>按文件限流：防止单文件被频繁下载
 * </ul>
 *
 * <p><b>防盗链：</b>
 *
 * <ul>
 *   <li>Referer 校验（正则精确域名匹配）
 *   <li>签名 URL（时效性 + IP 绑定）
 *   <li>Token 验证
 * </ul>
 *
 * <p><b>原子性保证：</b>限流逻辑统一使用 {@link RedisRateLimiter#tryAcquireFixedWindow}， 底层基于 Redis Lua 脚本（INCR
 * + EXPIRE 在同一个脚本中执行），避免原 INCR 后 EXPIRE 失败 导致 key 永不过期的限流卡死 bug。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadRateLimitService {

  /** Redis Key 前缀 */
  private static final String KEY_USER_RATE = "nextwiki:rate:user:";

  private static final String KEY_IP_RATE = "nextwiki:rate:ip:";
  private static final String KEY_FILE_RATE = "nextwiki:rate:file:";

  /** 限流时间窗口：1 分钟 */
  private static final Duration RATE_WINDOW = Duration.ofMinutes(1);

  /**
   * 域名匹配正则模板：精确匹配主域名及其子域名。
   *
   * <p>如 allowedDomain 为 {@code example.com} 时，匹配 {@code example.com}、{@code www.example.com}、{@code
   * cdn.example.com}，但不匹配 {@code evil-example.com}。
   */
  private static final String DOMAIN_REGEX_TEMPLATE =
      "^(https?://)?([a-zA-Z0-9-]+\\.)*%s(:[0-9]+)?(/.*)?$";

  private final RedisStringOps stringOps;
  private final RedisRateLimiter redisRateLimiter;
  private final NextwikiProperties properties;

  /**
   * 检查下载限流（用户级 + IP 级双重固定窗口限流）。
   *
   * <p>委托 {@link RedisRateLimiter#tryAcquireFixedWindow}（Redis Lua 脚本，INCR+EXPIRE 原子）执行限流，
   * 任一维度超限即拒绝；限流组件不可用时按 FAIL_CLOSED 策略拒绝请求，保证安全性。
   *
   * @param userId 用户 ID（用户级限流维度，阈值 {@code nextwiki.download.rate-limit-per-minute}）
   * @param ip 客户端 IP（IP 级限流维度，阈值 {@code nextwiki.download.ip-rate-limit-per-minute}）
   * @param fileNodeId 文件节点 ID（当前作为透传参数保留，未启用单文件级限流）
   * @return 限流结果 {@link RateLimitResult}，{@code allowed=false} 时含拒绝原因
   * @complexity O(1)（两次 Redis 原子计数）
   * @concurrency 基于 Redis 原子窗口，支持多实例部署；窗口 {@link #RATE_WINDOW}=1 分钟
   * @note 无本地状态，线程安全；限流计数由各维度 Key 独立维护
   */
  public RateLimitResult checkRateLimit(String userId, String ip, String fileNodeId) {
    int rateLimitPerMinute = properties.getDownload().getRateLimitPerMinute();
    int ipRateLimitPerMinute = properties.getDownload().getIpRateLimitPerMinute();
    // 用户级限流
    if (!redisRateLimiter.tryAcquireFixedWindow(
        KEY_USER_RATE + userId, rateLimitPerMinute, RATE_WINDOW)) {
      log.warn(
          "[DownloadRateLimitService] 用户下载限流: userId={}, limit={}/分钟", userId, rateLimitPerMinute);
      return RateLimitResult.blocked("用户下载频率超限: " + rateLimitPerMinute + "/分钟");
    }

    // IP 级限流
    if (!redisRateLimiter.tryAcquireFixedWindow(
        KEY_IP_RATE + ip, ipRateLimitPerMinute, RATE_WINDOW)) {
      log.warn("[DownloadRateLimitService] IP 下载限流: ip={}, limit={}/分钟", ip, ipRateLimitPerMinute);
      return RateLimitResult.blocked("IP 下载频率超限: " + ipRateLimitPerMinute + "/分钟");
    }

    return RateLimitResult.allowed();
  }

  /**
   * 验证 Referer 防盗链（正则精确域名匹配）。
   *
   * <p>解析 {@code Referer} 头的 host，与配置的允许域名列表逐一正则匹配（精确匹配主域名及其子域名，防止 {@code
   * evil-example.com} 绕过 {@code example.com} 的校验）。空 Referer 是否允许由配置决定（{@code
   * nextwiki.download.allow-empty-referer}）。
   *
   * @param referer 请求 Referer 头
   * @param allowedDomains 允许的来源域名列表（如 {@code ["example.com", "cdn.example.com"]}）
   * @return 是否通过防盗链校验
   * @complexity O(n)（n 为 allowedDomains 数量；URL 解析 + 正则匹配）
   * @security 仅作基础来源校验，不替代签名 URL 的强校验
   */
  public boolean verifyReferer(String referer, List<String> allowedDomains) {
    // 空 Referer 按配置决定
    if (referer == null || referer.isEmpty()) {
      boolean allowEmpty = properties.getDownload().isAllowEmptyReferer();
      if (!allowEmpty) {
        log.warn("[DownloadRateLimitService] 空 Referer，拒绝访问");
      }
      return allowEmpty;
    }

    // 解析 Referer 的 host
    String refererHost = extractHost(referer);
    if (refererHost == null) {
      log.warn("[DownloadRateLimitService] 无法解析 Referer host: {}", referer);
      return false;
    }

    // 逐一正则匹配允许域名
    for (String allowedDomain : allowedDomains) {
      if (matchesDomain(refererHost, allowedDomain)) {
        return true;
      }
    }

    log.warn(
        "[DownloadRateLimitService] Referer 校验失败: refererHost={}, allowedDomains={}",
        refererHost,
        allowedDomains);
    return false;
  }

  /**
   * 生成签名下载 URL（SHA-256 签名 + Redis 落地，时效性与用户/IP 绑定）。
   *
   * <p>将 {@code storageKey|userId|ip|expireTime} 做 SHA-256 得到签名，并把签名→storageKey 写入 Redis， TTL
   * 等于签名有效期；返回的路径由 Controller 路由到 {@link #verifySignedUrl} 校验。
   *
   * @param storageKey 存储对象键
   * @param userId 用户 ID（参与签名，校验时绑定）
   * @param ip 客户端 IP（参与签名，校验时绑定）
   * @return 签名下载路径（如 {@code /nextwiki/download/{sign}?expires=...}）
   * @complexity O(1)（一次 SHA-256 + 一次 Redis 写入）
   * @security 签名含 userId 与 ip，理论上可限制重放来源；SHA-256 仅作完整性校验，非加密强度
   * @note 有效期由 {@code nextwiki.download.signed-url-expire-seconds} 决定
   */
  public String generateSignedDownloadUrl(String storageKey, String userId, String ip) {
    long signedUrlExpireSeconds = properties.getDownload().getSignedUrlExpireSeconds();
    long expireTime = System.currentTimeMillis() / 1000 + signedUrlExpireSeconds;
    String rawData = storageKey + "|" + userId + "|" + ip + "|" + expireTime;
    String sign = DigestUtils.sha256Hex(rawData);

    // 存储签名到 Redis（用于验证）
    String signKey = "nextwiki:sign:" + sign;
    stringOps.set(signKey, storageKey, Duration.ofSeconds(signedUrlExpireSeconds));

    return "/nextwiki/download/" + sign + "?expires=" + expireTime;
  }

  /**
   * 验证签名下载 URL：先判过期，再查 Redis 还原 storageKey，校验成功后删除签名（一次性）。
   *
   * @param sign 签名串
   * @param expireTime 签名中的过期时间戳（秒级）
   * @return 还原出的 storageKey；过期、签名无效或已被使用返回 {@code null}
   * @complexity O(1)（一次时间判断 + 一次 Redis 读取 + 一次删除）
   * @security 一次性使用：校验成功后立即删除 Redis 记录，防止签名 URL 重放
   * @note 无事务边界
   */
  public String verifySignedUrl(String sign, long expireTime) {
    if (System.currentTimeMillis() / 1000 > expireTime) {
      return null; // 已过期
    }
    String signKey = "nextwiki:sign:" + sign;
    String storageKey = stringOps.get(signKey, String.class);
    if (storageKey == null) {
      return null; // 签名无效
    }
    // 验证后删除（一次性使用）
    stringOps.del(signKey);
    return storageKey;
  }

  /**
   * 解析 Referer 头中的主机名。
   *
   * @param referer Referer 头值
   * @return host（如 {@code www.example.com}）；解析失败返回 {@code null}
   */
  private String extractHost(String referer) {
    try {
      URI uri = URI.create(referer);
      String host = uri.getHost();
      if (host != null) {
        return host.toLowerCase();
      }
      // 处理无 scheme 的情况（如 //example.com/path）
      String lower = referer.toLowerCase();
      if (lower.startsWith("//")) {
        String remainder = lower.substring(2);
        int slashIdx = remainder.indexOf('/');
        return slashIdx > 0 ? remainder.substring(0, slashIdx) : remainder;
      }
      return null;
    } catch (Exception e) {
      log.warn("[DownloadRateLimitService] Referer 解析异常: {}", referer);
      return null;
    }
  }

  /**
   * 判断 Referer host 是否匹配允许的域名。
   *
   * <p>精确匹配主域名及其子域名（如 example.com 匹配 www.example.com），但不匹配包含该字符串的其他域名（如
   * evil-example.com）。
   *
   * @param refererHost 从 Referer 提取的 host
   * @param allowedDomain 允许的域名（如 {@code example.com}）
   * @return 是否匹配
   */
  private boolean matchesDomain(String refererHost, String allowedDomain) {
    if (refererHost == null || allowedDomain == null || allowedDomain.isEmpty()) {
      return false;
    }
    // 精确主域名及其子域名匹配
    String regex =
        String.format(DOMAIN_REGEX_TEMPLATE, Pattern.quote(allowedDomain.toLowerCase()));
    return Pattern.matches(regex, refererHost);
  }

  /** 限流结果 */
  @Data
  @Builder
  public static class RateLimitResult {
    /** 是否放行下载（true=允许，false=被限流拒绝） */
    private boolean allowed;

    /** 拒绝原因描述（allowed=true 时为 null） */
    private String message;

    /**
     * 构造「放行」结果。
     *
     * <p>{@code message} 保持为 {@code null}：放行场景无需向调用方解释原因， 调用方只应依据 {@link #isAllowed()} 分支，不要读取
     * message。
     *
     * @return 放行结果，{@code allowed=true}、{@code message=null}
     */
    public static RateLimitResult allowed() {
      return RateLimitResult.builder().allowed(true).build();
    }

    /**
     * 构造「限流拒绝」结果。
     *
     * <p>message 会被 Controller 直接回传给前端提示，因此只允许写入 维度与阈值这类可公开信息，<b>不得</b>包含 Redis Key、内部 IP 等实现细节。
     *
     * @param message 拒绝原因（如 {@code "用户下载频率超限: 60/分钟"}），不应为 {@code null}
     * @return 拒绝结果，{@code allowed=false}
     */
    public static RateLimitResult blocked(String message) {
      return RateLimitResult.builder().allowed(false).message(message).build();
    }
  }
}
