package com.njydsz.common.cache.support;

import org.springframework.util.Assert;

/**
 * 模块级缓存键构造器抽象基类（P2-2：公共能力下沉）。
 *
 * <p>为各业务模块提供统一的租户感知缓存 key 生成能力，子类仅需声明模块名并提供语义化方法名即可，
 * 无需关注 {@link CacheKeyBuilder} 的静态调用细节。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Component("cacheKeyBuilder")
 * public class ModuleCacheKeyBuilder extends AbstractModuleCacheKeyBuilder {
 *
 *   public ModuleCacheKeyBuilder() {
 *     super("system");
 *   }
 *
 *   public String configValue(String configKey) {
 *     return buildKey("config:value", configKey);
 *   }
 * }
 * }</pre>
 *
 * <p><b>规范合规：</b>
 *
 * <ul>
 *   <li>所有 key 统一通过 {@link CacheKeyBuilder#build(String, String, String)} 构建，
 *       格式为 {@code ydsz:{tenantId}:{module}:{entity}:{id}}</li>
 *   <li>子类不得直接使用 FQN 引用 {@code com.njydsz.common.cache.support.CacheKeyBuilder}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.06
 */
public abstract class AbstractModuleCacheKeyBuilder {

  /** 模块标识（由子类构造器注入） */
  private final String module;

  /**
   * 构造模块级缓存键构造器。
   *
   * @param module 模块名（如 {@code "system"}、{@code "nextwiki"}），不可为空
   */
  protected AbstractModuleCacheKeyBuilder(String module) {
    Assert.hasText(module, "module must not be empty");
    this.module = module;
  }

  /**
   * 构建标准缓存键（带租户隔离）。
   *
   * <p>格式：{@code ydsz:{tenantId}:{module}:{entity}:{id}}
   *
   * @param entity 实体名（如 {@code "config:value"}、{@code "dict:items"}）
   * @param id 实体标识
   * @return 租户隔离的缓存键
   */
  protected String buildKey(String entity, String id) {
    return CacheKeyBuilder.build(module, entity, id);
  }

  /**
   * 构建缓存键（自由拼接模式，适用于复杂 key 组合）。
   *
   * <p>格式：{@code ydsz:{tenantId}:{module}:{entity}:{id1}:{id2}:...}
   *
   * @param entity 实体名
   * @param segments 后续 key 段（如字典类型+项编码）
   * @return 拼接后的缓存键
   */
  protected String buildKeyPattern(String entity, String... segments) {
    String[] fullSegments = new String[segments.length + 2];
    fullSegments[0] = module;
    fullSegments[1] = entity;
    System.arraycopy(segments, 0, fullSegments, 2, segments.length);
    return CacheKeyBuilder.buildPattern(fullSegments);
  }
}
