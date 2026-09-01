package com.njydsz.workflow.server.engine.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.json.YdszJson;

/**
 * 从节点 ext JSON 中读取监听器配置列表
 *
 * <p>节点 ext JSON 格式示例：
 *
 * <pre>{@code
 * {
 *   "listeners": [
 *     {
 *       "eventType": "TASK_CREATED",
 *       "pluginName": "notifyListenerPlugin",
 *       "enabled": true,
 *       "priority": 10
 *     }
 *   ]
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public final class FlowListenerConfigReader {

  private FlowListenerConfigReader() {
    throw new AssertionError("工具类禁止实例化");
  }

  /**
   * 从节点 ext JSON 中解析监听器配置列表
   *
   * @param nodeExt 节点 ext JSON 字符串
   * @return 监听器配置列表，无配置时返回空列表
   */
  public static List<FlowListenerConfig> readListeners(String nodeExt) {
    if (!StringUtils.hasText(nodeExt)) {
      return Collections.emptyList();
    }
    try {
      Map<String, Object> extMap = YdszJson.parseMap(nodeExt);
      if (extMap == null) {
        return Collections.emptyList();
      }
      Object raw = extMap.get("listeners");
      if (raw instanceof List<?> list) {
        List<FlowListenerConfig> result = new ArrayList<>(list.size());
        for (Object item : list) {
          if (item instanceof Map<?, ?> map) {
            result.add(toConfig(map));
          }
        }
        return result;
      }
    } catch (Exception e) {
      log.warn("[FlowListener] 解析 listeners 配置失败: err={}", e.getMessage());
    }
    return Collections.emptyList();
  }

  /**
   * 将 Map 转换为 {@link FlowListenerConfig}
   *
   * @param map 包含监听器配置的 Map（键：eventType / pluginName / enabled / priority）
   * @return 转换后的监听器配置对象
   */
  private static FlowListenerConfig toConfig(Map<?, ?> map) {
    FlowListenerConfig config = new FlowListenerConfig();
    Object eventType = map.get("eventType");
    config.setEventType(eventType == null ? null : String.valueOf(eventType));
    Object pluginName = map.get("pluginName");
    config.setPluginName(pluginName == null ? null : String.valueOf(pluginName));
    Object enabled = map.get("enabled");
    if (enabled instanceof Boolean b) {
      config.setEnabled(b);
    } else if (enabled != null) {
      config.setEnabled(Boolean.parseBoolean(String.valueOf(enabled)));
    }
    Object priority = map.get("priority");
    if (priority instanceof Number n) {
      config.setPriority(n.intValue());
    }
    return config;
  }
}
