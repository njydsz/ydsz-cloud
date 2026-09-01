package com.njydsz.literule.web;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.literule.domain.dto.RuleDefinition;
import com.njydsz.literule.domain.enums.RuleStatus;
import com.njydsz.literule.api.dto.RuleApproveDTO;
import com.njydsz.literule.api.dto.RuleDelegateDTO;
import com.njydsz.literule.api.dto.RuleRejectDTO;
import com.njydsz.literule.api.dto.RuleStatusChangeDTO;
import com.njydsz.literule.api.dto.RuleSubmitReviewDTO;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;
import com.njydsz.literule.domain.vo.ApprovalFlowVO;
import com.njydsz.literule.domain.vo.ApprovalRecordVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.server.approval.RuleApprovalService;
import com.njydsz.literule.server.config.RuleAdminService;

/**
 * 规则生命周期 Controller
 *
 * <p>业务背景：规则从新建到发布需经历完整的状态流转闭环： DRAFT → REVIEW → PUBLISHED → DISABLED → ARCHIVED。同时支持多级审批流， 通过 SPI
 * 由 project 模块提供的 {@link RuleApprovalService} 实现可插拔的审批策略。
 *
 * <p>核心能力：
 *
 * <ul>
 *   <li>规则状态变更（含状态机校验）
 *   <li>单级审批通过/驳回（DRAFT → PUBLISHED/ARCHIVED）
 *   <li>多级审批流：提交审核、级别审批、委托、撤回
 *   <li>审批状态查询、待审批列表、可用审批流配置查询
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
@Tag(name = "规则生命周期", description = "规则状态变更、审批与多级审批流")
public class RuleLifecycleController {

  /** 规则管理服务 */
  private final RuleAdminService ruleAdminService;

  /** 多级审批流服务（P1-3）：可选注入，未配置 RuleConfigProvider 时为空 */
  private final ObjectProvider<RuleApprovalService> ruleApprovalServiceProvider;

  /**
   * 规则状态变更
   *
   * <p>执行状态机驱动的规则状态流转。非法状态流转会返回明确的业务异常码。
   *
   * @param ruleCode 规则编码
   * @param operator 操作人
   * @return 操作结果
      * @param dto 参数说明
   */
  @Idempotent(key = "ruleAdmin:changeStatus", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'规则状态变更: ' + #ruleCode + ', 目标状态: ' + #dto.targetStatus")
  @RateLimit(resource = "literule.rule_lifecycle.changeStatus", threshold = 50)
  @PutMapping("/{ruleCode}/status")
  @AuthApiPermission(apiCodes = "execution:rule:status")
  public YdszResponse<RuleDefinitionVO> changeStatus(
      @PathVariable String ruleCode,
      @Valid @RequestBody RuleStatusChangeDTO dto,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    String targetStatus = dto.getTargetStatus();
    String comment = dto.getComment() == null ? "" : dto.getComment();
    RuleDefinition def = ruleAdminService.getByCode(ruleCode);
    if (def == null) {
      return YdszResponse.error(LiteruleExceptionCode.RULE_NOT_FOUND, "规则不存在: " + ruleCode);
    }

    RuleStatus current = parseStatusSafely(def.getStatus());
    if (current == null) {
      return YdszResponse.error(
          LiteruleExceptionCode.RULE_STATUS_INVALID, "规则当前状态非法: " + def.getStatus());
    }
    RuleStatus target = parseStatusSafely(targetStatus);
    if (target == null) {
      return YdszResponse.error(
          LiteruleExceptionCode.RULE_STATUS_INVALID, "目标状态非法: " + targetStatus);
    }
    if (!current.canTransitionTo(target)) {
      return YdszResponse.error(
          LiteruleExceptionCode.RULE_STATUS_TRANSITION_ILLEGAL,
          String.format(
              "不允许从 %s(%s) 变更到 %s(%s)",
              current.name(), current.getDesc(), target.name(), target.getDesc()));
    }
    def.setStatus(targetStatus);
    if (target == RuleStatus.PUBLISHED) {
      def.setReviewedBy(operator);
      def.setReviewedAt(LocalDateTime.now());
      def.setReviewComment(comment);
    }
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(
            ruleAdminService.save(
                def, operator, "状态变更: " + current.getDesc() + " -> " + target.getDesc())));
  }

  /**
   * 审批通过（1.4.0 起支持）
   *
   * <p>将规则从 DRAFT/REVIEW 状态变更为 PUBLISHED，并记录审批人、审批时间、审批意见。 主要用于规则审批闭环：新建 → DRAFT → 人工审批 →
   * PUBLISHED。
   *
   * @param ruleCode 规则编码
   * @param operator 审批人
   * @return 审批后的规则定义
      * @param dto 参数说明
   */
  @Idempotent(key = "ruleAdmin:approve", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'规则审批通过: ' + #ruleCode + ', 审批人: ' + #operator")
  @RateLimit(resource = "literule.rule_lifecycle.approve", threshold = 50)
  @PostMapping("/{ruleCode}/approve")
  @AuthApiPermission(apiCodes = "execution:rule:approve")
  public YdszResponse<RuleDefinitionVO> approve(
      @PathVariable String ruleCode,
      @Valid @RequestBody RuleApproveDTO dto,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    RuleDefinition def = ruleAdminService.getByCode(ruleCode);
    if (def == null) {
      return YdszResponse.error(LiteruleExceptionCode.RULE_NOT_FOUND, "规则不存在: " + ruleCode);
    }

    RuleStatus current = parseStatusSafely(def.getStatus());
    if (current == null) {
      return YdszResponse.error(
          LiteruleExceptionCode.RULE_STATUS_INVALID, "规则状态非法: " + def.getStatus());
    }
    if (!current.canTransitionTo(RuleStatus.PUBLISHED)) {
      return YdszResponse.error(
          LiteruleExceptionCode.RULE_STATUS_INVALID,
          "当前状态 " + current.getDesc() + " 不允许审批通过，仅 DRAFT/REVIEW 可审批");
    }

    String comment = dto.getComment() == null ? "" : dto.getComment();

    // 记录审批留痕
    def.setStatus(RuleStatus.PUBLISHED.name());
    def.setReviewedBy(operator);
    def.setReviewedAt(LocalDateTime.now());
    def.setReviewComment(comment);
    // 审批通过后默认启用（运营可后续手动 toggle 关闭）
    def.setEnabled(true);

    String changeDesc =
        String.format(
            "[审批通过] %s -> PUBLISHED, 审批人=%s, 意见=%s",
            current.getDesc(), operator, comment.isEmpty() ? "无" : comment);
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(ruleAdminService.save(def, operator, changeDesc)));
  }

  /**
   * 审批驳回（1.4.0 起支持）
   *
   * <p>将规则从 DRAFT/REVIEW 状态变更为 ARCHIVED，并记录驳回理由。 主要用于规则审批闭环：新建 → DRAFT → 人工驳回 → ARCHIVED。
   *
   * @param ruleCode 规则编码
   * @param operator 审批人
   * @return 驳回后的规则定义
      * @param dto 参数说明
   */
  @Idempotent(key = "ruleAdmin:reject", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'规则审批驳回: ' + #ruleCode + ', 审批人: ' + #operator + ', 理由: ' + #dto.reason")
  @RateLimit(resource = "literule.rule_lifecycle.reject", threshold = 50)
  @PostMapping("/{ruleCode}/reject")
  @AuthApiPermission(apiCodes = "execution:rule:approve")
  public YdszResponse<RuleDefinitionVO> reject(
      @PathVariable String ruleCode,
      @Valid @RequestBody RuleRejectDTO dto,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    RuleDefinition def = ruleAdminService.getByCode(ruleCode);
    if (def == null) {
      return YdszResponse.error(LiteruleExceptionCode.RULE_NOT_FOUND, "规则不存在: " + ruleCode);
    }

    RuleStatus current = parseStatusSafely(def.getStatus());
    if (current == null) {
      return YdszResponse.error(
          LiteruleExceptionCode.RULE_STATUS_INVALID, "规则状态非法: " + def.getStatus());
    }
    if (!current.canTransitionTo(RuleStatus.ARCHIVED)) {
      return YdszResponse.error(
          LiteruleExceptionCode.RULE_STATUS_INVALID,
          "当前状态 " + current.getDesc() + " 不允许驳回，仅 DRAFT/REVIEW/PUBLISHED 可驳回");
    }

    String reason = dto.getReason();
    // @NotBlank 已校验非空，移除手动校验

    // 记录驳回留痕
    def.setStatus(RuleStatus.ARCHIVED.name());
    def.setReviewedBy(operator);
    def.setReviewedAt(LocalDateTime.now());
    def.setReviewComment("[驳回] " + reason);
    def.setEnabled(false);

    String changeDesc =
        String.format("[审批驳回] %s -> ARCHIVED, 审批人=%s, 理由=%s", current.getDesc(), operator, reason);
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(ruleAdminService.save(def, operator, changeDesc)));
  }

  /**
   * 安全解析规则状态
   *
   * <p>解析失败时返回 null，由调用方决定如何处理（抛出明确业务异常）。
   *
   * @param status 状态字符串
   * @return RuleStatus；无法解析时返回 null
   */
  private RuleStatus parseStatusSafely(String status) {
    if (status == null || status.isBlank()) {
      return null;
    }
    try {
      return RuleStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      log.warn("规则状态解析失败，status={}", status);
      return null;
    }
  }

  /**
   * 提交审核（P1-3 多级审批流）
   *
   * <p>将规则从 DRAFT 状态提交到指定审批流的第一级。flowCode 为空时使用默认 2 级审批流。
   *
   * @param ruleCode 规则编码
   * @param dto 请求体，包含 flowCode（可选）
   * @param operator 操作人
   * @return 审批记录
   */
  @Idempotent(key = "ruleAdmin:submitReview", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'提交规则审核: ' + #ruleCode + ', 操作人: ' + #operator")
  @RateLimit(resource = "literule.rule_lifecycle.submitReview", threshold = 50)
  @PostMapping("/{ruleCode}/submit-review")
  @AuthApiPermission(apiCodes = "execution:rule:save")
  public YdszResponse<ApprovalRecordVO> submitReview(
      @PathVariable String ruleCode,
      @Valid @RequestBody(required = false) RuleSubmitReviewDTO dto,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
    if (svc == null) {
      return YdszResponse.error(YdszResultCode.FORBIDDEN, "多级审批流服务未启用");
    }
    String flowCode = dto == null ? null : dto.getFlowCode();
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(svc.submitForReview(ruleCode, flowCode, operator)));
  }

  /**
   * 级别审批通过（P1-3 多级审批流）
   *
   * <p>审批通过当前级别。根据审批类型（SINGLE/COUNTERSIGN/SEQUENCE）决定是否进入下一级。 全部级别通过后规则状态变为 PUBLISHED。
   *
   * @param ruleCode 规则编码
   * @param dto 请求体，包含 comment（审批意见）
   * @param operator 审批人
   * @return 审批记录
   */
  @Idempotent(key = "ruleAdmin:approveLevel", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'多级审批通过: ' + #ruleCode + ', 审批人: ' + #operator")
  @RateLimit(resource = "literule.rule_lifecycle.approveLevel", threshold = 50)
  @PostMapping("/{ruleCode}/approve-level")
  @AuthApiPermission(apiCodes = "execution:rule:approve")
  public YdszResponse<ApprovalRecordVO> approveLevel(
      @PathVariable String ruleCode,
      @Valid @RequestBody RuleApproveDTO dto,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
    if (svc == null) {
      return YdszResponse.error(YdszResultCode.FORBIDDEN, "多级审批流服务未启用");
    }
    String comment = dto.getComment() == null ? "" : dto.getComment();
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(svc.approve(ruleCode, operator, comment)));
  }

  /**
   * 级别审批驳回（P1-3 多级审批流）
   *
   * <p>驳回当前级别，回退到上一级。一级驳回回退到 DRAFT。
   *
   * @param ruleCode 规则编码
   * @param dto 请求体，包含 reason（驳回理由，必填）
   * @param operator 审批人
   * @return 审批记录
   */
  @Idempotent(key = "ruleAdmin:rejectLevel", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'多级审批驳回: ' + #ruleCode + ', 驳回人: ' + #operator + ', 理由: ' + #dto.reason")
  @RateLimit(resource = "literule.rule_lifecycle.rejectLevel", threshold = 50)
  @PostMapping("/{ruleCode}/reject-level")
  @AuthApiPermission(apiCodes = "execution:rule:approve")
  public YdszResponse<ApprovalRecordVO> rejectLevel(
      @PathVariable String ruleCode,
      @Valid @RequestBody RuleRejectDTO dto,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
    if (svc == null) {
      return YdszResponse.error(YdszResultCode.FORBIDDEN, "多级审批流服务未启用");
    }
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(svc.reject(ruleCode, operator, dto.getReason())));
  }

  /**
   * 委托审批（P1-3 多级审批流）
   *
   * <p>将当前级别的审批权委托给他人。委托后被委托人通过 approve-level 完成审批。
   *
   * @param ruleCode 规则编码
   * @param dto 请求体，包含 delegatedTo（被委托人工号，必填）和 comment（委托说明）
   * @param operator 委托人
   * @return 审批记录
   */
  @Idempotent(key = "ruleAdmin:delegate", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'审批委托: ' + #ruleCode + ', 委托人: ' + #operator + ', 被委托人: ' + #dto.delegatedTo")
  @RateLimit(resource = "literule.rule_lifecycle.delegate", threshold = 50)
  @PostMapping("/{ruleCode}/delegate")
  @AuthApiPermission(apiCodes = "execution:rule:approve")
  public YdszResponse<ApprovalRecordVO> delegate(
      @PathVariable String ruleCode,
      @Valid @RequestBody RuleDelegateDTO dto,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
    if (svc == null) {
      return YdszResponse.error(YdszResultCode.FORBIDDEN, "多级审批流服务未启用");
    }
    String comment = dto.getComment() == null ? "" : dto.getComment();
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(
            svc.delegate(ruleCode, operator, dto.getDelegatedTo(), comment)));
  }

  /**
   * 查询审批状态（P1-3 多级审批流）
   *
   * @param ruleCode 规则编码
   * @return 审批记录；无审批记录时返回 null
   */
  @GetMapping("/{ruleCode}/approval-status")
  public YdszResponse<ApprovalRecordVO> approvalStatus(@PathVariable String ruleCode) {
    RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
    if (svc == null) {
      return YdszResponse.success((ApprovalRecordVO) null);
    }
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(svc.getApprovalStatus(ruleCode)));
  }

  /**
   * 查询待审批列表（P1-3 多级审批流）
   *
   * @param approver 审批人工号
   * @return 待审批记录列表
   */
  @GetMapping("/pending-approvals")
  public YdszResponse<List<ApprovalRecordVO>> pendingApprovals(@RequestParam String approver) {
    RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
    if (svc == null) {
      return YdszResponse.success(List.of());
    }
    return YdszResponse.success(
        svc.listPendingApprovals(approver).stream()
            .map(LiteruleWebConverter.INSTANCE::entityToVO)
            .toList());
  }

  /**
   * 撤回审核（P1-3 多级审批流）
   *
   * <p>将规则从审核中状态撤回到 DRAFT。仅 PENDING/DELEGATED 状态可撤回。
   *
   * @param ruleCode 规则编码
   * @param operator 操作人
   * @return 审批记录
   */
  @Idempotent(key = "ruleAdmin:cancelReview", ttlSeconds = 5, message = "请勿重复提交")
  @Audit(
      module = "规则管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'撤审: ' + #ruleCode + ', 操作人: ' + #operator")
  @RateLimit(resource = "literule.rule_lifecycle.cancelReview", threshold = 50)
  @PostMapping("/{ruleCode}/cancel-review")
  @AuthApiPermission(apiCodes = "execution:rule:save")
  public YdszResponse<ApprovalRecordVO> cancelReview(
      @PathVariable String ruleCode,
      @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
    RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
    if (svc == null) {
      return YdszResponse.error(YdszResultCode.FORBIDDEN, "多级审批流服务未启用");
    }
    return YdszResponse.success(
        LiteruleWebConverter.INSTANCE.entityToVO(svc.cancelReview(ruleCode, operator)));
  }

  /**
   * 查询可用审批流配置（P1-3 多级审批流）
   *
   * @return 审批流配置列表
   */
  @GetMapping("/approval-flows")
  public YdszResponse<List<ApprovalFlowVO>> approvalFlows() {
    RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
    if (svc == null) {
      return YdszResponse.success(List.of());
    }
    return YdszResponse.success(
        svc.listFlows().stream().map(LiteruleWebConverter.INSTANCE::entityToVO).toList());
  }
}
