package com.njydsz.workflow.server.engine.listener;

import java.io.Serial;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程监听器配置（节点 ext JSON 中的单条监听器绑定）
 *
 * <p>存储在节点 ext JSON 的 {@code listeners} 数组中，由设计器 UI 配置。
 * 例：
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
 * @since 26.09.01
 * @see FlowListenerPluginExecutor 执行器，读取此配置并分发给对应插件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowListenerConfig implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 事件类型编码（对应 {@link FlowListenerEventType#getCode()}） */
  private String eventType;

  /** 监听器插件 Spring Bean 名称 */
  private String pluginName;

  /** 是否启用 */
  private boolean enabled = true;

  /** 优先级（数值越小越先执行，默认 100） */
  private int priority = 100;
}
