package com.njydsz.message.server.service.config;

import java.time.Duration;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;
import com.njydsz.message.domain.repository.MsgTenantConfigRepository;
import com.njydsz.message.domain.vo.MsgTenantConfigVO;

/**
 * 多租户消息配置服务 — 提供租户级配额查询与通道覆盖决议（P2-A5）。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li><b>配置缓存</b>：Redis 缓存租户配置（TTL 5 分钟），缓存未命中时查 DB 并回填
 *   <li><b>通道开关覆盖决议</b>：{@link #isChannelEnabled} — 优先租户级覆盖，无覆盖则回退全局默认值
 *   <li><b>通道映射覆盖决议</b>：{@link #resolveProvider} — 优先租户级 provider，无覆盖则回退全局配置
 *   <li><b>配额决议</b>：{@link #getDailyLimit} / {@link #getHourlyLimit} — 租户级限额优先，未配置则回退全局默认值
 * </ul>
 *
 * <p><b>多租户硬隔离（P2-A5）：</b>本服务与 ydsz-common-tenant 的逻辑隔离互补，
 * 在业务层实现租户级配额硬隔离和通道映射覆盖，确保不同租户的发送策略互不影响。
 *
 * <p><b>JSON 解析：</b>使用 {@link YdszJson} 解析 channelOverrides / providerOverrides JSON Map，
 * 不引入 Jackson 依赖。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantConfigService {

  private final MsgTenantConfigRepository msgTenantConfigRepository;

  private final RedisTemplate<String, String> redisTemplate;

  /** Redis 缓存 key 前缀 */
  private static final String CACHE_PREFIX = "msg:tenant:config:";

  /** 缓存 TTL（分钟） */
  private static final long CACHE_TTL_MINUTES = 5;

  /**
   * 获取租户配置（带缓存）。
   *
   * <p>查询路径：Redis 缓存 → DB → 回填缓存。缓存未命中或 Redis 异常时直接查 DB，
   * 保证可用性（fail-open）。
   *
   * @param tenantId 租户 ID
   * @return 租户配置 VO；未找到返回 null
   */
  public MsgTenantConfigVO getConfig(String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      return null;
    }
    String cacheKey = CACHE_PREFIX + tenantId;

    // 1. 查缓存
    try {
      String cached = redisTemplate.opsForValue().get(cacheKey);
      if (cached != null && !cached.isBlank()) {
        log.debug("[TenantConfig] 缓存命中: tenant={}", tenantId);
        return YdszJson.fromJson(cached, MsgTenantConfigVO.class);
      }
    } catch (Exception e) {
      log.warn("[TenantConfig] 缓存读取异常(fail-open): tenant={} err={}", tenantId, e.getMessage(), e);
    }

    // 2. 缓存未命中查 DB
    MsgTenantConfigVO config = msgTenantConfigRepository.findByTenantId(tenantId)
        .orElse(null);
    if (config == null) {
      log.debug("[TenantConfig] 配置不存在: tenant={}", tenantId);
      return null;
    }

    // 3. 写入缓存
    try {
      redisTemplate
          .opsForValue()
          .set(cacheKey, YdszJson.toJson(config), Duration.ofMinutes(CACHE_TTL_MINUTES));
    } catch (Exception e) {
      log.warn("[TenantConfig] 缓存写入异常(忽略): tenant={} err={}", tenantId, e.getMessage(), e);
    }

    return config;
  }

  /**
   * 判断租户是否启用某通道。
   *
   * <p>优先查询租户级 channelOverrides 覆盖，无覆盖则返回全局默认值。
   *
   * @param tenantId 租户 ID
   * @param channel 通道名称（大写，如 SMS/EMAIL）
   * @param globalDefault 全局默认开关值
   * @return true 表示该租户启用了此通道
   */
  public boolean isChannelEnabled(String tenantId, String channel, boolean globalDefault) {
    if (tenantId == null || tenantId.isBlank() || channel == null || channel.isBlank()) {
      return globalDefault;
    }
    MsgTenantConfigVO config = getConfig(tenantId);
    if (config == null || config.getChannelOverrides() == null || config.getChannelOverrides().isBlank()) {
      return globalDefault;
    }
    try {
      Map<String, Boolean> overrides =
          YdszJson.fromJsonToMap(config.getChannelOverrides(), String.class, Boolean.class);
      if (overrides != null && overrides.containsKey(channel)) {
        Boolean enabled = overrides.get(channel);
        log.debug("[TenantConfig] 通道覆盖: tenant={} channel={} enabled={}", tenantId, channel, enabled);
        return enabled != null ? enabled : globalDefault;
      }
    } catch (Exception e) {
      log.warn("[TenantConfig] channelOverrides 解析异常(fail-open): tenant={} err={}", tenantId, e.getMessage(), e);
    }
    return globalDefault;
  }

  /**
   * 获取租户级 provider（租户级覆盖 > 全局配置）。
   *
   * <p>优先查询租户级 providerOverrides，无覆盖则返回全局 provider。
   *
   * @param tenantId 租户 ID
   * @param channel 通道名称（大写，如 SMS/EMAIL）
   * @param globalProvider 全局 provider 配置
   * @return 最终使用的 provider 名称
   */
  public String resolveProvider(String tenantId, String channel, String globalProvider) {
    if (tenantId == null || tenantId.isBlank() || channel == null || channel.isBlank()) {
      return globalProvider;
    }
    MsgTenantConfigVO config = getConfig(tenantId);
    if (config == null || config.getProviderOverrides() == null || config.getProviderOverrides().isBlank()) {
      return globalProvider;
    }
    try {
      Map<String, String> overrides =
          YdszJson.fromJsonToMap(config.getProviderOverrides(), String.class, String.class);
      if (overrides != null && overrides.containsKey(channel)) {
        String provider = overrides.get(channel);
        log.debug("[TenantConfig] provider覆盖: tenant={} channel={} provider={}", tenantId, channel, provider);
        return provider != null ? provider : globalProvider;
      }
    } catch (Exception e) {
      log.warn("[TenantConfig] providerOverrides 解析异常(fail-open): tenant={} err={}", tenantId, e.getMessage(), e);
    }
    return globalProvider;
  }

  /**
   * 获取租户级每日发送上限。
   *
   * <p>租户级 dailyLimit 优先，未配置（null）则返回全局默认值。
   *
   * @param tenantId 租户 ID
   * @param globalDefault 全局默认每日上限
   * @return 最终使用的每日上限值
   */
  public Long getDailyLimit(String tenantId, long globalDefault) {
    if (tenantId == null || tenantId.isBlank()) {
      return globalDefault;
    }
    MsgTenantConfigVO config = getConfig(tenantId);
    if (config == null || config.getDailyLimit() == null) {
      return globalDefault;
    }
    log.debug("[TenantConfig] 每日限额: tenant={} dailyLimit={}", tenantId, config.getDailyLimit());
    return config.getDailyLimit();
  }

  /**
   * 获取租户级每小时发送上限。
   *
   * <p>租户级 hourlyLimit 优先，未配置（null）则返回全局默认值。
   *
   * @param tenantId 租户 ID
   * @param globalDefault 全局默认每小时上限
   * @return 最终使用的每小时上限值
   */
  public Long getHourlyLimit(String tenantId, long globalDefault) {
    if (tenantId == null || tenantId.isBlank()) {
      return globalDefault;
    }
    MsgTenantConfigVO config = getConfig(tenantId);
    if (config == null || config.getHourlyLimit() == null) {
      return globalDefault;
    }
    log.debug("[TenantConfig] 每小时限额: tenant={} hourlyLimit={}", tenantId, config.getHourlyLimit());
    return config.getHourlyLimit();
  }
}
