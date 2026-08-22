package com.njydsz.workflow.server.engine.listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.workflow.server.engine.FlowEventContext;

/**
 * 流程监听器插件执行器
 *
 * <p>从节点 ext JSON 的 {@code listeners} 配置中，筛选匹配当前事件类型的监听器，
 * 按优先级回调对应的 {@link FlowListenerPlugin} Spring Bean。
 *
 * <p>对标 warm-flow 的监听器机制：设计器配置 → 引擎运行时执行，无需硬编码事件处理逻辑。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class FlowListenerPluginExecutor {

  private final Map<String, FlowListenerPlugin> pluginMap;

  /**
   * 构造器注入所有 {@link FlowListenerPlugin} 实现
   *
   * @param plugins Spring 容器中所有监听器插件
   */
  public FlowListenerPluginExecutor(List<FlowListenerPlugin> plugins) {
    this.pluginMap = new java.util.HashMap<>();
    for (FlowListenerPlugin plugin : plugins) {
      String name = plugin.getClass().getSimpleName();
      // 首字母小写作为默认 Bean 名称
      String beanName = Character.toLowerCase(name.charAt(0)) + name.substring(1);
      this.pluginMap.put(beanName, plugin);
      // Also register by class name for flexibility
      this.pluginMap.put(plugin.getClass().getName(), plugin);
    }
    log.info("[FlowListener] 已注册 {} 个监听器插件", plugins.size());
  }

  /**
   * 执行节点上配置的监听器（按事件类型过滤、优先级排序）
   *
   * @param listenerConfigs 节点上配置的监听器列表
   * @param eventType       当前触发的事件类型
   * @param instanceId      流程实例 ID
   * @param taskId          任务 ID（可空）
   * @param nodeCode        节点编码（可空）
   * @param variables       流程变量（可空）
   * @param ctx             事件上下文（可空）
   */
  public void execute(List<FlowListenerConfig> listenerConfigs, FlowListenerEventType eventType,
      String instanceId, String taskId, String nodeCode,
      Map<String, Object> variables, FlowEventContext ctx) {
    if (listenerConfigs == null || listenerConfigs.isEmpty()) {
      return;
    }
    List<FlowListenerConfig> matched = new ArrayList<>();
    for (FlowListenerConfig config : listenerConfigs) {
      if (config == null || !config.isEnabled()) {
        continue;
      }
      if (eventType.getCode().equals(config.getEventType())) {
        matched.add(config);
      }
    }
    if (matched.isEmpty()) {
      return;
    }
    // Sort by priority ascending
    matched.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));

    for (FlowListenerConfig config : matched) {
      FlowListenerPlugin plugin = pluginMap.get(config.getPluginName());
      if (plugin == null) {
        log.warn("[FlowListener] 未找到监听器插件: node={} plugin={}", nodeCode, config.getPluginName());
        continue;
      }
      try {
        dispatch(plugin, eventType, instanceId, taskId, nodeCode, variables, ctx);
      } catch (Exception e) {
        log.warn("[FlowListener] 监听器执行失败: node={} plugin={} err={}",
            nodeCode, config.getPluginName(), e.getMessage());
      }
    }
  }

  /**
   * 分发事件到具体插件的对应回调
   */
  private void dispatch(FlowListenerPlugin plugin, FlowListenerEventType eventType,
      String instanceId, String taskId, String nodeCode,
      Map<String, Object> variables, FlowEventContext ctx) {
    switch (eventType) {
      case TASK_CREATED ->
        plugin.onTaskCreated(instanceId, taskId, nodeCode, variables, ctx);
      case TASK_STARTED ->
        plugin.onTaskStarted(instanceId, taskId, nodeCode, null, variables, ctx);
      case TASK_FINISHED ->
        plugin.onTaskFinished(instanceId, taskId, nodeCode, null, variables, ctx);
      case INSTANCE_STARTED ->
        plugin.onInstanceStarted(instanceId, variables, ctx);
      case INSTANCE_FINISHED ->
        plugin.onInstanceFinished(instanceId, ctx);
      case INSTANCE_REJECTED ->
        plugin.onInstanceRejected(instanceId, null, ctx);
      case INSTANCE_TERMINATED ->
        plugin.onInstanceTerminated(instanceId, null, ctx);
      default -> log.debug("[FlowListener] 未处理的事件类型: {}", eventType);
    }
  }

  /**
   * 获取所有可用的插件名称列表（供设计器 UI 下拉选择）
   *
   * @return 插件名称列表
   */
  public List<String> getAvailablePluginNames() {
    return pluginMap.keySet().stream()
        .filter(name -> !name.contains(".")) // 过滤掉全限定名，只保留短名称
        .sorted()
        .toList();
  }
}
