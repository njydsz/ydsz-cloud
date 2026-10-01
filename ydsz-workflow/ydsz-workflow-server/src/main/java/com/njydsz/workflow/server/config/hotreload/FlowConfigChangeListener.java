package com.njydsz.workflow.server.config.hotreload;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.config.hotreload.ConfigChangeListener;

/**
 * 工作流模块配置变更监听器（P1-B2：接入统一 ConfigChangeBridge）。
 *
 * <p>监听 workflow 模块相关的配置中心变更（{@code ydsz.flow.*}），将 Spring Cloud 配置变更事件桥接到运行时状态感知。
 *
 * <p><b>适用范围：</b>通过 Nacos / Apollo 动态调整 workflow 模块行为参数（如缓存 TTL、归档策略、
 * 催办阈值等），无需重启服务即可感知变更。
 *
 * <p><b>设计说明：</b>本监听器实现 {@link ConfigChangeListener} 接口，由 {@code ydsz-common-config} 的 {@code
 * ConfigChangeBridge} 自动分发配置变更事件。配置属性的热加载由 Spring Cloud 原生 {@code @ConfigurationProperties}
 * 自动处理，本监听器仅负责需要<b>主动响应</b>的变更场景（如日志记录、缓存清理等）。
 *
 * <h3>当前支持的变更响应</h3>
 *
 * <ul>
 *   <li>{@code ydsz.flow.enabled}：模块总开关变更 → 日志感知
 *   <li>{@code ydsz.flow.definitionCache.*}：流程定义缓存配置变更 → 日志感知
 *   <li>{@code ydsz.flow.userCache.*}：用户信息缓存配置变更 → 日志感知
 *   <li>{@code ydsz.flow.formSchemaCache.*}：表单 Schema 缓存配置变更 → 日志感知
 *   <li>{@code ydsz.flow.autoUrge.*}：自动催办配置变更 → 日志感知
 *   <li>{@code ydsz.flow.history.*}：归档配置变更 → 日志感知
 * </ul>
 *
 * <p><b>注意：</b>Workflow 核心缓存（YdszCache 实例）在启动时使用 initial TTL 构建，{@code @ConfigurationProperties}
 * 自动回绑更新了 Java 字段值，但已构建的 YdszCache 实例 TTL 不可动态修改。
 * 后续可增强为触发缓存重建逻辑。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
public class FlowConfigChangeListener implements ConfigChangeListener {

  /** workflow 模块配置属性前缀 */
  private static final String FLOW_CONFIG_PREFIX = "ydsz.flow.";

  /**
   * 接收配置变更回调。
   *
   * <p>仅处理 {@code ydsz.flow.} 前缀的配置项，其他配置变更忽略。
   *
   * @param key 变更的配置键（如 ydsz.flow.definitionCache.ttlMinutes）
   * @param oldValue 变更前的值
   * @param newValue 变更后的值
   */
  @Override
  public void onChange(String key, String oldValue, String newValue) {
    if (key == null || !key.startsWith(FLOW_CONFIG_PREFIX)) {
      return;
    }

    log.info("[Workflow] 配置变更通知: key={}, {} -> {}", key, oldValue, newValue);

    // 模块总开关变更 → 提示需重启
    if ("ydsz.flow.enabled".equals(key)) {
      log.warn("[Workflow] 工作流模块启用状态变更: {} -> {}，需重启生效", oldValue, newValue);
    }

    // 缓存 TTL 变更 → 提示缓存 TTL 修改需重建 YdszCache 实例
    if (key.contains("cache") && key.toLowerCase().contains("ttl")) {
      log.info("[Workflow] 缓存 TTL 配置变更已感知（{}），现有 YdszCache 实例 TTL 不变，后续可触发重建", key);
    }
  }

  /**
   * 获取监听器执行顺序。
   *
   * <p>workflow 配置监听器优先级为 20（普通优先级）。
   *
   * @return 20
   */
  @Override
  public int getOrder() {
    return 20;
  }
}
