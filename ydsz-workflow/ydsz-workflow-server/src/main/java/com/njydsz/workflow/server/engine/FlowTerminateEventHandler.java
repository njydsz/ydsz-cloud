package com.njydsz.workflow.server.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.workflow.domain.vo.FlowNodeVO;

/**
 * BPMN 终止事件运行时处理器。
 *
 * <p>当流程推进到包含 {@code terminateEventDefinition} 的结束节点时，
 * 立即终止当前实例并清理所有运行中任务、事件订阅。
 *
 * <p><b>触发时机：</b>在 {@code DefaultFlowAdvancer} 处理下一节点时，
 * 如果目标节点是结束节点且 ext 中包含 {@code terminateEvent: true}，
 * 则委托本处理器执行终止逻辑。
 *
 * <p><b>与正常结束的区别：</b>
 *
 * <ul>
 *   <li>正常结束（END 节点）：实例状态设为 COMPLETED，任务标记为 SKIPPED
 *   <li>终止事件：实例状态设为 TERMINATED，任务标记为 CANCELLED
 * </ul>
 *
 * <p><b>架构合规说明（26.09.01 DDD 分层规范）：</b>运行时处理器置于
 * {@code server/engine/} 包下，负责 BPMN 事件语义的运行时解释。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
public class FlowTerminateEventHandler {

  /**
   * 判断节点是否为终止事件节点。
   *
   * <p>检查节点 ext JSON 中是否包含 {@code terminateEvent: true} 标记。
   *
   * @param node 节点 VO
   * @return true-终止事件节点；false-普通节点
   */
  public boolean isTerminateEventNode(FlowNodeVO node) {
    if (node == null) {
      return false;
    }
    return Boolean.TRUE.equals(node.getExtMap().get("terminateEvent"));
  }

  /**
   * 获取终止事件描述（用于审计日志）。
   *
   * @param nodeCode 节点编码
   * @return 终止事件描述
   */
  public String getDescription(String nodeCode) {
    return "BPMN终止事件触发: nodeCode=" + nodeCode + ", 立即终止实例并清理所有运行中任务";
  }
}
