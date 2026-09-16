package com.njydsz.literule.server.core;

import org.springframework.stereotype.Component;

import com.njydsz.common.cache.support.AbstractModuleCacheKeyBuilder;

/**
 * 规则引擎模块缓存键构造器（P1-4 整改：继承公共基类 {@link AbstractModuleCacheKeyBuilder}）。
 *
 * <p>替代原来自建 SHA-256 + TreeMap 排序的键构建方案，统一使用
 * {@code ydsz:{tenantId}:{module}:{entity}:{id}} 标准格式，与 sibling 模块（nextwiki/system）保持一致。
 *
 * <p><b>规则评估结果缓存键：</b>{@code ydsz:{tenantId}:literule:eval:{sha256Hex}}
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
public class CacheKeyBuilder extends AbstractModuleCacheKeyBuilder {

  /** 模块标识：规则引擎。 */
  private static final String MODULE = "literule";

  /** 构造规则引擎模块缓存键构造器。 */
  public CacheKeyBuilder() {
    super(MODULE);
  }

  /**
   * 构建规则评估结果缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:literule:eval:{scenario}}
   *
   * @param scenario 规则场景编码
   * @return 租户隔离的缓存键
   */
  public String evalResult(String scenario) {
    return buildKey("eval", scenario);
  }

  /**
   * 构建规则配置缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:literule:config:{ruleCode}}
   *
   * @param ruleCode 规则编码
   * @return 租户隔离的缓存键
   */
  public String ruleConfig(String ruleCode) {
    return buildKey("config", ruleCode);
  }
}
