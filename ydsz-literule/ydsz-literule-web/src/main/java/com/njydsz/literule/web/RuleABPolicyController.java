package com.njydsz.literule.web;

import java.util.List;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.domain.dto.RuleABPolicyDTO;
import com.njydsz.literule.domain.vo.RuleABPolicyVO;
import com.njydsz.literule.domain.vo.RuleABRollbackVO;
import com.njydsz.literule.server.spi.ABTestAutoRollbackProvider;

/**
 * AB Test 自动回滚 Controller
 *
 * <p>业务背景：规则发布后通过 AB Test 灰度验证新版本效果，当核心指标 （如触发率、严重度分布）劣化超过阈值时自动回滚到上一个稳定版本， 同时支持 Owner
 * 主动触发人工回滚作为紧急操作手段。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>查询/更新 AB Test 自动回滚策略（阈值、冷却时间等）
 *   <li>查询规则回滚历史
 *   <li>主动触发 AB Test 评估（人工立即检查）
 *   <li>人工回滚（Owner 主动请求 / 紧急操作）
 * </ul>
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径 {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/literule/rules")
@RequiredArgsConstructor
@Validated
@Tag(name = "AB Test 回滚", description = "AB Test 自动回滚策略配置与人工回滚")
public class RuleABPolicyController {

  /** A/B 测试自动回滚服务（SPI，由 project 模块提供实现） */
  private final ABTestAutoRollbackProvider abTestAutoRollbackProvider;

    /** 获取规则的 AB Test 自动回滚策略（无配置时返回默认策略）
   * @param ruleCode 规则唯一编码
   * @return AB Test 自动回滚策略信息
   */
  @GetMapping("/{ruleCode}/ab-policy")
  public YdszResponse<RuleABPolicyVO> getABPolicy(@PathVariable String ruleCode) {
    return YdszResponse.success(abTestAutoRollbackProvider.getPolicy(ruleCode));
  }

    /** 更新规则的 AB Test 自动回滚策略
   * @param ruleCode 规则唯一编码
   * @param dto 策略请求数据
   * @param operator 操作人用户名
   * @return 无返回内容
   */
  @Idempotent(key = "ruleAdmin:updateAbpolicy", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'updateABPolicy'")
  @RateLimit(resource = "literule.rule_a_b_policy.updateABPolicy", threshold = 50)
  @PutMapping("/{ruleCode}/ab-policy")
  public YdszResponse<Void> updateABPolicy(
      @PathVariable String ruleCode,
      @Valid @RequestBody RuleABPolicyDTO dto,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    RuleABPolicyVO policy = LiteruleWebConverter.INSTANCE.putDtoToVO(dto);
    policy.setRuleCode(ruleCode);
    abTestAutoRollbackProvider.savePolicy(policy, operator);
    return YdszResponse.success();
  }

    /** 查询规则的回滚历史
   * @param ruleCode 规则唯一编码
   * @return 回滚历史列表
   */
  @GetMapping("/{ruleCode}/ab-rollbacks")
  public YdszResponse<List<RuleABRollbackVO>> listRollbackHistory(@PathVariable String ruleCode) {
    return YdszResponse.success(
        abTestAutoRollbackProvider.listRollbackHistory(ruleCode));
  }

    /** 主动触发 AB Test 评估（人工立即检查）
   * @param ruleCode 规则唯一编码
   * @return 是否需要回滚（true/false）
   */
  @Idempotent(key = "ruleAdmin:evaluateAb", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'evaluateAB'")
  @RateLimit(resource = "literule.rule_a_b_policy.evaluateAB", threshold = 50)
  @PostMapping("/{ruleCode}/ab-evaluate")
  public YdszResponse<Boolean> evaluateAB(@PathVariable String ruleCode) {
    return YdszResponse.success(abTestAutoRollbackProvider.evaluateOne(ruleCode));
  }

  /**
   * 人工回滚（Owner 主动请求 / 紧急操作）
   *
      * @param ruleCode 规则唯一编码
   * @param reason 回滚原因（MANUAL/OWNER_REQUEST）
   * @param operator 操作人用户名
   * @return 回滚操作结果
   */
  @Idempotent(key = "ruleAdmin:manualRollback", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'manualRollback'")
  @RateLimit(resource = "literule.rule_a_b_policy.manualRollback", threshold = 50)
  @PostMapping("/{ruleCode}/ab-rollback")
  public YdszResponse<RuleABRollbackVO> manualRollback(
      @PathVariable String ruleCode,
      @RequestParam(value = "reason", defaultValue = "MANUAL") String reason,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    return YdszResponse.success(
        abTestAutoRollbackProvider.manualRollback(ruleCode, operator, reason));
  }
}


