package com.njydsz.workflow.server.timer;

import com.njydsz.workflow.domain.enums.FlowTimeoutStrategy;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;

/**
 * 超时处理器
 *
 * <p>负责执行任务超时后的具体策略逻辑（自动通过/转交管理员/转交上级/催办）。
 * 被 {@link FlowTimeoutJob} 调用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowTimeoutHandler {

  /**
   * 处理单个超时任务
   *
   * @param task 超时任务
   * @param strategy 超时策略
   * @return 处理结果描述
   */
  String handleTimeout(FlowRunTaskVO task, FlowTimeoutStrategy strategy);
}
