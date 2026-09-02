package com.njydsz.workflow.server.engine.listener;

import lombok.extern.slf4j.Slf4j;

/**
 * 监听器插件基类，提供日志和异常兜底
 *
 * <p>业务方继承此类实现 {@link FlowListenerPlugin}，只需覆写关心的方法。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public abstract class AbstractFlowListenerPlugin implements FlowListenerPlugin {

  /**
   * 异常处理：默认 log.warn 并吞掉异常。子类可覆写以自定义行为。
   *
   * @param eventType 事件类型
   * @param nodeId    节点编码
   * @param e         异常
   */
  protected void handleException(FlowListenerEventType eventType, String nodeId, Exception e) {
    log.warn("[FlowListener][{}] 执行失败: node={} err={}", pluginName(), nodeId, e.getMessage());
  }

  /**
   * 插件名称（默认取类名首字母小写，子类可覆写）
   *
   * @return 设计器下拉显示名称
   */
  public String pluginName() {
    String simpleName = getClass().getSimpleName();
    return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
  }

  /**
   * 插件描述（供设计器 tooltip 提示）
   *
   * @return 描述文案
   */
  public String description() {
    return getClass().getSimpleName();
  }
}
