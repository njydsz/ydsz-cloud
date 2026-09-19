package com.njydsz.common.auth.service;

import java.io.Serializable;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.redis.service.ops.RedisStringOps;

/**
 * 登录防护服务。
 *
 * <p>防止暴力破解、密码喷洒、撞库攻击，三层防护：
 *
 * <ol>
 *   <li><b>账号级锁定：</b>连续失败达到阈值后锁定账号 N 分钟（默认 15）
 *   <li><b>IP 级限流：</b>单 IP 在滑动窗口内限制最大尝试次数（默认 100 次/分钟）
 *   <li><b>验证码阈值：</b>连续失败达到阈值后，强制后续登录需校验图形验证码
 * </ol>
 *
 * <p>Redis Key 设计：
 *
 * <ul>
 *   <li>失败计数：{@code ydsz:auth:login-def:user:{username}}，TTL = failCountExpireSeconds
 *   <li>账号锁定：{@code ydsz:auth:login-def:lock:{username}}，TTL = lockoutMinutes * 60
 *   <li>IP 限流计数：{@code ydsz:auth:login-def:ip:{ip}}，TTL = ipRateWindowSeconds
 * </ul>
 *
 * <p>返回 {@link LoginCheckResult} 告知调用方是否允许登录及额外要求（如需验证码）。
 *
 * @author ydsz-team
 * @since 26.09.18
 */
public class LoginDefenseService {

  private static final Logger LOG = LoggerFactory.getLogger(LoginDefenseService.class);

  /** 登录校验通过。 */
  public static final String RESULT_ALLOWED = "ALLOWED";

  /** 账号被锁定。 */
  public static final String RESULT_LOCKED = "LOCKED";

  /** IP 被限流。 */
  public static final String RESULT_IP_LIMITED = "IP_LIMITED";

  /** 需要验证码。 */
  public static final String RESULT_CAPTCHA_REQUIRED = "CAPTCHA_REQUIRED";

  /** Redis Key 前缀 */
  private static final String KEY_PREFIX = "ydsz:auth:login-def";

  private static final String KEY_USER_COUNT = KEY_PREFIX + ":user:{}";

  private static final String KEY_USER_LOCK = KEY_PREFIX + ":lock:{}";

  private static final String KEY_IP_COUNT = KEY_PREFIX + ":ip:{}";

  private final RedisStringOps redisStringOps;

  private final AuthProperties authProperties;

  /** 从配置读取的运行时参数 */
  private int maxFailAttempts;

  private int lockoutMinutes;

  private int ipRateLimit;

  private int ipRateWindowSeconds;

  private int captchaThreshold;

  private int failCountExpireSeconds;

  private boolean enabled;

  public LoginDefenseService(RedisStringOps redisStringOps, AuthProperties authProperties) {
    this.redisStringOps = redisStringOps;
    this.authProperties = authProperties;
    loadConfig();
  }

  /**
   * 从配置加载运行时参数（可支持运行时动态刷新）。
   */
  @PostConstruct
  public void loadConfig() {
    AuthProperties.LoginDefenseProperties config = authProperties.getLoginDefense();
    this.enabled = config.isEnabled();
    this.maxFailAttempts = config.getMaxFailAttempts();
    this.lockoutMinutes = config.getLockoutMinutes();
    this.ipRateLimit = config.getIpRateLimit();
    this.ipRateWindowSeconds = config.getIpRateWindowSeconds();
    this.captchaThreshold = config.getCaptchaThreshold();
    this.failCountExpireSeconds = config.getFailCountExpireSeconds();
    LOG.info(
        "[LoginDefenseService] 登录防护初始化完成: enabled={}, maxFail={}, lockoutMin={}, ipRateLimit={}/{}s",
        enabled, maxFailAttempts, lockoutMinutes, ipRateLimit, ipRateWindowSeconds);
  }

  /**
   * 登录前校验：判断当前请求是否允许登录。
   *
   * <p>应在用户名密码校验前调用，若返回结果非 {@value #RESULT_ALLOWED} 则应拒绝登录流程。
   *
   * @param username 用户标识（登录名）
   * @param clientIp 客户端 IPv4 地址
   * @return 校验结果（含是否需要验证码、锁定剩余时间等扩展信息）
   */
  public LoginCheckResult checkLoginAllowed(String username, String clientIp) {
    if (!enabled) {
      return LoginCheckResult.allowed();
    }
    // 1. 账号锁定检查
    String lockKey = KEY_USER_LOCK.replace("{}", normalize(username));
    if (redisStringOps.hasKey(lockKey)) {
      LOG.warn("[LoginDefenseService] 账号已锁定: username={}, clientIp={}", username, clientIp);
      return LoginCheckResult.locked(lockoutMinutes);
    }
    // 2. IP 限流检查
    String ipKey = KEY_IP_COUNT.replace("{}", clientIp);
    Object countObj = redisStringOps.get(ipKey);
    long ipCount = parseLong(countObj);
    if (ipCount >= ipRateLimit) {
      LOG.warn("[LoginDefenseService] IP 登录频率超限: clientIp={}, count={}", clientIp, ipCount);
      return LoginCheckResult.ipLimited();
    }
    // 3. 验证码阈值检查
    String userKey = KEY_USER_COUNT.replace("{}", normalize(username));
    Object userCountObj = redisStringOps.get(userKey);
    long userFailCount = parseLong(userCountObj);
    boolean captchaRequired = captchaThreshold > 0 && userFailCount >= captchaThreshold;
    if (captchaRequired) {
      LOG.debug("[LoginDefenseService] 触发验证码策略: username={}, failCount={}", username, userFailCount);
      return LoginCheckResult.captchaRequired(userFailCount);
    }
    return LoginCheckResult.allowed();
  }

  /**
   * 记录登录失败。
   *
   * <p>应在用户名密码校验失败后调用。累计失败次数达到阈值时自动锁定账号。
   *
   * @param username 登录名
   * @param clientIp 客户端 IP
   */
  public void recordFailedAttempt(String username, String clientIp) {
    if (!enabled) {
      return;
    }
    // 1. IP 限流计数（窗口内自增）
    String ipKey = KEY_IP_COUNT.replace("{}", clientIp);
    redisStringOps.setIfAbsent(ipKey, 0, ipRateWindowSeconds);
    redisStringOps.incr(ipKey, 1);
    // 2. 用户失败计数（自增并设置 TTL）
    String userKey = KEY_USER_COUNT.replace("{}", normalize(username));
    redisStringOps.setIfAbsent(userKey, 0, failCountExpireSeconds);
    long newCount = redisStringOps.incr(userKey, 1);
    LOG.info("[LoginDefenseService] 登录失败记录: username={}, clientIp={}, failCount={}", username, clientIp, newCount);
    // 3. 检查是否需锁定账号
    if (newCount >= maxFailAttempts) {
      String lockKey = KEY_USER_LOCK.replace("{}", normalize(username));
      // 锁定 key 存在即表示锁定，设置过期时间作为自动解锁
      redisStringOps.setIfAbsent(lockKey, System.currentTimeMillis(), lockoutMinutes * 60);
      LOG.warn("[LoginDefenseService] 账号已自动锁定: username={}, failCount={}", username, newCount);
    }
  }

  /**
   * 记录登录成功（清除失败计数）。
   *
   * <p>应在用户名密码校验成功后调用，清除该用户的累计失败计数。
   *
   * @param username 登录名
   */
  public void recordSuccess(String username) {
    if (!enabled) {
      return;
    }
    String userKey = KEY_USER_COUNT.replace("{}", normalize(username));
    redisStringOps.del(userKey);
    LOG.debug("[LoginDefenseService] 登录成功，清除失败计数: username={}", username);
  }

  /**
   * 查询账号剩余锁定等待时间（秒）。
   *
   * @param username 登录名
   * @return 剩余锁定秒数；未锁定时返回 0
   */
  public long getRemainingLockSeconds(String username) {
    if (!enabled) {
      return 0;
    }
    String lockKey = KEY_USER_LOCK.replace("{}", normalize(username));
    if (!redisStringOps.hasKey(lockKey)) {
      return 0;
    }
    long expire = redisStringOps.getExpire(lockKey);
    return Math.max(0, expire);
  }

  /**
   * 手动解锁账号（管理员工具调用）。
   *
   * @param username 登录名
   * @return 是否成功解锁
   */
  public boolean unlock(String username) {
    if (!enabled) {
      return false;
    }
    String lockKey = KEY_USER_LOCK.replace("{}", normalize(username));
    String userKey = KEY_USER_COUNT.replace("{}", normalize(username));
    redisStringOps.del(lockKey, userKey);
    LOG.info("[LoginDefenseService] 管理员手动解锁账号: username={}", username);
    return true;
  }

  /**
   * 查询当前失败次数。
   *
   * @param username 登录名
   * @return 当前失败次数；未记录时返回 0
   */
  public long getFailCount(String username) {
    if (!enabled) {
      return 0;
    }
    String userKey = KEY_USER_COUNT.replace("{}", normalize(username));
    Object countObj = redisStringOps.get(userKey);
    return parseLong(countObj);
  }

  /**
   * 登录前校验结果。
   *
   * <p>封装 {@link LoginDefenseService#checkLoginAllowed} 的返回值，携带是否允许登录、失败原因、失败次数、锁定剩余时间等
   * 上下文。
   */
  public static class LoginCheckResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否允许继续登录流程 */
    private final boolean allowed;

    /** 结果类型：ALLOWED / LOCKED / IP_LIMITED / CAPTCHA_REQUIRED */
    private final String result;

    /** 当前累计失败次数 */
    private final long failCount;

    /** 账号锁定剩余秒数（未锁定时为 0） */
    private final long lockRemainingSeconds;

    private LoginCheckResult(boolean allowed, String result, long failCount, long lockRemainingSeconds) {
      this.allowed = allowed;
      this.result = result;
      this.failCount = failCount;
      this.lockRemainingSeconds = lockRemainingSeconds;
    }

    /**
     * 允许登录。
     *
     * @return 允许登录的结果实例
     */
    public static LoginCheckResult allowed() {
      return new LoginCheckResult(true, RESULT_ALLOWED, 0, 0);
    }

    /**
     * 需要验证图形验证码后继续。
     *
     * @param failCount 当前失败次数
     * @return 需验证码的结果实例
     */
    public static LoginCheckResult captchaRequired(long failCount) {
      return new LoginCheckResult(true, RESULT_CAPTCHA_REQUIRED, failCount, 0);
    }

    /**
     * 账号已锁定。
     *
     * @param lockMinutes 锁定分钟数（仅用于日志和错误信息展示）
     * @return 锁定结果实例
     */
    public static LoginCheckResult locked(long lockMinutes) {
      return new LoginCheckResult(false, RESULT_LOCKED, 0, lockMinutes * 60);
    }

    /**
     * IP 被限流。
     *
     * @return 限流结果实例
     */
    public static LoginCheckResult ipLimited() {
      return new LoginCheckResult(false, RESULT_IP_LIMITED, 0, 0);
    }

    public boolean isAllowed() {
      return allowed;
    }

    public String getResult() {
      return result;
    }

    public long getFailCount() {
      return failCount;
    }

    public long getLockRemainingSeconds() {
      return lockRemainingSeconds;
    }
  }

  // ---- private helpers ----

  /** 用户名标准化：null-safe、小写、去除首尾空白。 */
  private String normalize(String username) {
    return username == null ? "" : username.trim().toLowerCase();
  }

  /** 安全解析 Object 为 long（容错：null 和非数字均返回 0）。 */
  private long parseLong(Object obj) {
    if (obj == null) {
      return 0;
    }
    if (obj instanceof Number) {
      return ((Number) obj).longValue();
    }
    try {
      return Long.parseLong(obj.toString());
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
