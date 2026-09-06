package com.njydsz.literule.server.approval;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 规则审批与工作流引擎桥接的默认空实现。
 *
 * <p>当业务系统不需要将审批事件同步到工作流引擎时，使用此默认实现（空操作 + 日志输出）。
 * 业务系统如需接入工作流引擎，可注册自定义的 {@link RuleApprovalWorkflowBridge} Bean 来覆盖本实现。
 *
 * <p>作为兜底实现，所有方法体仅记录 debug 日志，不执行任何实际工作流操作。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RuleApprovalWorkflowBridge
 * @see RuleApprovalService
 */
@Slf4j
@Component
public class DefaultRuleApprovalWorkflowBridge implements RuleApprovalWorkflowBridge {

  @Override
  public void onApprovalSubmitted(String ruleCode, String flowCode, String operator) {
    log.debug(
        "[DefaultRuleApprovalWorkflowBridge] 规则提交审核: ruleCode={}, flowCode={}, operator={}",
        ruleCode, flowCode, operator);
  }

  @Override
  public void onApprovalPassed(
      String ruleCode, int level, String operator, String comment, boolean allPassed) {
    log.debug(
        "[DefaultRuleApprovalWorkflowBridge] 规则审批通过: ruleCode={}, level={}, operator={}, allPassed={}",
        ruleCode, level, operator, allPassed);
  }

  @Override
  public void onApprovalRejected(
      String ruleCode, int fromLevel, int toLevel, String operator, String reason) {
    log.debug(
        "[DefaultRuleApprovalWorkflowBridge] 规则审批驳回: ruleCode={}, fromLevel={}, toLevel={}, operator={}",
        ruleCode, fromLevel, toLevel, operator);
  }

  @Override
  public void onApprovalDelegated(
      String ruleCode, int level, String delegator, String delegatedTo) {
    log.debug(
        "[DefaultRuleApprovalWorkflowBridge] 规则审批委托: ruleCode={}, level={}, delegator={}, delegatedTo={}",
        ruleCode, level, delegator, delegatedTo);
  }
}
