package com.njydsz.system.server.sse;

import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.json.YdszJson;

/**
 * 配置变更 SSE 推送器
 *
 * <p>在配置变更时，通过 SSE 向当前租户的在线用户推送变更通知。 前端收到通知后可主动刷新配置或提示用户。
 *
 * <p><b>推送时机：</b>
 *
 * <ul>
 *   <li>配置创建 / 更新 / 删除
 *   <li>配置回滚
 *   <li>配置批量导入
 * </ul>
 *
 * <p><b>推送内容：</b>
 *
 * <pre>{@code
 * {
 *   "eventType": "CONFIG_CHANGED",
 *   "configKey": "ydsz.workflow.sla-default-hours",
 *   "configGroup": "ydsz.workflow",
 *   "action": "更新配置",
 *   "timestamp": 1700000000000
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConfigChangeSsePusher {

  /** SSE 连接管理器 */
  private final SseEmitterManager sseEmitterManager;

  /**
   * 推送配置变更事件。
   *
   * @param configKey 配置键
   * @param configGroup 配置分组
   * @param action 操作描述（如「创建配置」「更新配置」「删除配置」）
   */
  public void pushConfigChanged(String configKey, String configGroup, String action) {
    String tenantId = TenantContextHolder.getTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      log.debug("[ConfigChangeSsePusher] 无租户上下文，跳过推送");
      return;
    }

    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("eventType", "CONFIG_CHANGED");
      payload.put("configKey", configKey);
      payload.put("configGroup", configGroup);
      payload.put("action", action);
      payload.put("timestamp", System.currentTimeMillis());

      String jsonPayload = YdszJson.toJson(payload);
      sseEmitterManager.pushToTenant(tenantId, "configChanged", jsonPayload);

      log.debug("[ConfigChangeSsePusher] 推送配置变更: tenant={}, key={}, action={}",
          tenantId, configKey, action);
    } catch (Exception e) {
      // SSE 推送失败不影响主流程
      log.warn("[ConfigChangeSsePusher] 推送失败: {}", e.getMessage());
    }
  }

  /**
   * 推送变量变更事件。
   *
   * @param variableKey 变量键
   * @param action 操作描述
   */
  public void pushVariableChanged(String variableKey, String action) {
    String tenantId = TenantContextHolder.getTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      return;
    }

    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("eventType", "VARIABLE_CHANGED");
      payload.put("variableKey", variableKey);
      payload.put("action", action);
      payload.put("timestamp", System.currentTimeMillis());

      String jsonPayload = YdszJson.toJson(payload);
      sseEmitterManager.pushToTenant(tenantId, "variableChanged", jsonPayload);

      log.debug("[ConfigChangeSsePusher] 推送变量变更: tenant={}, key={}, action={}",
          tenantId, variableKey, action);
    } catch (Exception e) {
      log.warn("[ConfigChangeSsePusher] 推送失败: {}", e.getMessage());
    }
  }

  /**
   * 推送字典变更事件。
   *
   * @param typeCode 字典类型编码
   * @param action 操作描述
   */
  public void pushDictChanged(String typeCode, String action) {
    String tenantId = TenantContextHolder.getTenantId();
    if (tenantId == null || tenantId.isBlank()) {
      return;
    }

    try {
      Map<String, Object> payload = new HashMap<>();
      payload.put("eventType", "DICT_CHANGED");
      payload.put("typeCode", typeCode);
      payload.put("action", action);
      payload.put("timestamp", System.currentTimeMillis());

      String jsonPayload = YdszJson.toJson(payload);
      sseEmitterManager.pushToTenant(tenantId, "dictChanged", jsonPayload);

      log.debug("[ConfigChangeSsePusher] 推送字典变更: tenant={}, typeCode={}, action={}",
          tenantId, typeCode, action);
    } catch (Exception e) {
      log.warn("[ConfigChangeSsePusher] 推送失败: {}", e.getMessage());
    }
  }
}
