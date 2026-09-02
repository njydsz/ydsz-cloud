package com.njydsz.workflow.server.engine;

import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.cache.YdszCache;
import com.njydsz.common.cache.api.Cache;
import com.njydsz.common.cache.builder.CacheType;
import com.njydsz.workflow.server.config.FlowProperties;
import com.njydsz.workflow.server.form.FlowFormSchema;
import com.njydsz.workflow.server.form.FlowFormValidator;

/**
 * 表单 Schema 本地缓存服务。
 *
 * <p>P1: 缓存已解析的表单 Schema 对象，避免每次表单校验时重复解析 JSON。
 * 使用 YdszCache 本地缓存（TinyLFU 算法），TTL 可配置（默认 60 分钟）。
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>缓存 key：{@code formSchema:{nodeExtHash}}（节点 ext JSON 的 hashCode）
 *   <li>TTL：{@code ydsz.flow.form-schema-cache.ttl-minutes}（默认 60 分钟）
 *   <li>容量：{@code ydsz.flow.form-schema-cache.max-size}（默认 500 条）
 *   <li>失效：被动过期 + 随流程定义缓存一起 evict（部署新版本时）
 * </ul>
 *
 * <p><b>版本校验：</b>节点 ext JSON 不变时 hashCode 不变，缓存自然命中；
 * ext JSON 变更后 hashCode 变化，旧缓存自然失效（LRU 淘汰）。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowFormSchemaCacheService {

  /** 表单 Schema 缓存 key 前缀 */
  private static final String KEY_FORM_SCHEMA = "formSchema:";

  private final FlowFormValidator formValidator;

  /** 表单 Schema 缓存：nodeExtHashCode → FlowFormSchema */
  private final Cache<String, FlowFormSchema> schemaCache;

  public FlowFormSchemaCacheService(FlowFormValidator formValidator, FlowProperties properties) {
    this.formValidator = formValidator;
    this.schemaCache =
        YdszCache.<String, FlowFormSchema>newBuilder()
            .type(CacheType.TINYLFU)
            .name("flow:form-schema")
            .expireAfterWrite(properties.getFormSchemaCache().getTtlMinutes(), TimeUnit.MINUTES)
            .maximumSize(properties.getFormSchemaCache().getMaxSize())
            .build();
  }

  /**
   * 获取表单 Schema（带缓存）。
   *
   * <p>先查缓存，未命中时解析 JSON 并回填缓存。解析失败返回 null。
   *
   * @param nodeExt 节点 ext JSON 字符串
   * @return 表单 Schema，无配置或解析失败返回 null
   */
  public FlowFormSchema getFormSchema(String nodeExt) {
    if (nodeExt == null || nodeExt.isBlank()) {
      return null;
    }
    String cacheKey = buildCacheKey(nodeExt);
    try {
      return schemaCache.get(cacheKey, key -> {
        String schemaJson = FlowNodeExt.getFormSchemaJson(nodeExt);
        if (schemaJson == null || schemaJson.isBlank()) {
          return null;
        }
        return formValidator.parseSchema(schemaJson);
      });
    } catch (Exception e) {
      log.warn("[FlowFormSchemaCache] 解析表单 Schema 失败: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 清除指定节点 ext 的表单 Schema 缓存。
   *
   * <p>在流程定义部署/编辑时调用。
   *
   * @param nodeExt 节点 ext JSON 字符串
   */
  public void evictFormSchema(String nodeExt) {
    if (nodeExt == null || nodeExt.isBlank()) {
      return;
    }
    schemaCache.invalidate(buildCacheKey(nodeExt));
    log.debug("[FlowFormSchemaCache] evict nodeExtHash={}", nodeExt.hashCode());
  }

  /**
   * 清除全部表单 Schema 缓存。
   *
   * <p>在流程定义全量重载时调用。
   */
  public void evictAll() {
    schemaCache.invalidateAll();
    log.info("[FlowFormSchemaCache] evictAll");
  }

  private String buildCacheKey(String nodeExt) {
    return KEY_FORM_SCHEMA + nodeExt.hashCode();
  }
}
