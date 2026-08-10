package com.njydsz.literule.web;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;

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
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.literule.api.RuleDefinition;
import com.njydsz.literule.api.RuleStatus;
import com.njydsz.literule.api.dto.RuleApproveDTO;
import com.njydsz.literule.api.dto.RuleDelegateDTO;
import com.njydsz.literule.api.dto.RuleRejectDTO;
import com.njydsz.literule.api.dto.RuleStatusChangeDTO;
import com.njydsz.literule.api.dto.RuleSubmitReviewDTO;
import com.njydsz.literule.domain.vo.ApprovalFlowVO;
import com.njydsz.literule.domain.vo.ApprovalRecordVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.converter.LiteruleConverter;
import com.njydsz.literule.server.approval.RuleApprovalService;
import com.njydsz.literule.server.config.RuleAdminService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.literule.domain.enums.LiteruleExceptionCode;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;

/**
 * 规则生命周期 Controller
 *
 * <p>业务背景：规则从新建到发布需经历完整的状态流转闭环：
 * DRAFT → REVIEW → PUBLISHED → DISABLED → ARCHIVED。同时支持多级审批流，
 * 通过 SPI 由 project 模块提供的 {@link RuleApprovalService} 实现可插拔的审批策略。
 *
 * <p>核心能力：
 * <ul>
 *   <li>规则状态变更（含状态机校验）</li>
 *   <li>单级审批通过/驳回（DRAFT → PUBLISHED/ARCHIVED）</li>
 *   <li>多级审批流：提交审核、级别审批、委托、撤回</li>
 *   <li>审批状态查询、待审批列表、可用审批流配置查询</li>
 * </ul>
 *
 * <p>从 {@link RuleAdminController} 拆分而来，与原文件共享基路径
 * {@code /ruleEngine/rules}，所有端点 URL 保持不变。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ruleEngine/rules")
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
     * @param ruleCode   规则编码
     * @param request    请求体，包含 targetStatus/comment
     * @param operator   操作人
     * @return 操作结果
     */
    @Idempotent(key = "ruleAdmin:changeStatus", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.UPDATE, content = "'changeStatus'")
    @RateLimit(resource = "literule.rule_lifecycle.changeStatus", threshold = 50)
    @PutMapping("/{ruleCode}/status")
    @AuthApiPermission(apiCodes = "execution:rule:status")
    public BaseResponse<RuleDefinitionVO> changeStatus(@PathVariable String ruleCode,
                                               @Valid @RequestBody RuleStatusChangeDTO dto,
                                               @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        String targetStatus = dto.getTargetStatus();
        String comment = dto.getComment() == null ? "" : dto.getComment();
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        RuleStatus current = RuleStatus.valueOf(def.getStatus());
        RuleStatus target = RuleStatus.valueOf(targetStatus);
        if (!current.canTransitionTo(target)) {
            throw new IllegalArgumentException("不允许从 " + current.getDesc() + " 变更到 " + target.getDesc());
        }
        def.setStatus(targetStatus);
        if (target == RuleStatus.PUBLISHED) {
            def.setReviewedBy(operator);
            def.setReviewedAt(LocalDateTime.now().toString());
            def.setReviewComment(comment);
        }
        return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(ruleAdminService.save(def, operator, "状态变更: " + current.getDesc() + " -> " + target.getDesc())));
    }

    /**
     * 审批通过（1.4.0 起支持）
     *
     * <p>将规则从 DRAFT/REVIEW 状态变更为 PUBLISHED，并记录审批人、审批时间、审批意见。
     * 主要用于规则审批闭环：新建 → DRAFT → 人工审批 → PUBLISHED。
     *
     * @param ruleCode 规则编码
     * @param request  请求体，包含 comment（审批意见）
     * @param operator 审批人
     * @return 审批后的规则定义
     */
    @Idempotent(key = "ruleAdmin:approve", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'approve'")
    @RateLimit(resource = "literule.rule_lifecycle.approve", threshold = 50)
    @PostMapping("/{ruleCode}/approve")
    @AuthApiPermission(apiCodes = "execution:rule:approve")
    public BaseResponse<RuleDefinitionVO> approve(@PathVariable String ruleCode,
                                           @Valid @RequestBody RuleApproveDTO dto,
                                           @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return BaseResponse.error(LiteruleExceptionCode.RULE_NOT_FOUND, "规则不存在: " + ruleCode);
        }

        RuleStatus current = parseStatusSafely(def.getStatus());
        if (!current.canTransitionTo(RuleStatus.PUBLISHED)) {
            return BaseResponse.error(LiteruleExceptionCode.RULE_STATUS_INVALID, "当前状态 " + current.getDesc() + " 不允许审批通过，仅 DRAFT/REVIEW 可审批");
        }

        String comment = dto.getComment() == null ? "" : dto.getComment();

        // 记录审批留痕
        def.setStatus(RuleStatus.PUBLISHED.name());
        def.setReviewedBy(operator);
        def.setReviewedAt(LocalDateTime.now().toString());
        def.setReviewComment(comment);
        // 审批通过后默认启用（运营可后续手动 toggle 关闭）
        def.setEnabled(true);

        String changeDesc = String.format("[审批通过] %s -> PUBLISHED, 审批人=%s, 意见=%s",
                current.getDesc(), operator, comment.isEmpty() ? "无" : comment);
        return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(ruleAdminService.save(def, operator, changeDesc)));
    }

    /**
     * 审批驳回（1.4.0 起支持）
     *
     * <p>将规则从 DRAFT/REVIEW 状态变更为 ARCHIVED，并记录驳回理由。
     * 主要用于规则审批闭环：新建 → DRAFT → 人工驳回 → ARCHIVED。
     *
     * @param ruleCode 规则编码
     * @param request  请求体，包含 reason（驳回理由，必填）
     * @param operator 审批人
     * @return 驳回后的规则定义
     */
    @Idempotent(key = "ruleAdmin:reject", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'reject'")
    @RateLimit(resource = "literule.rule_lifecycle.reject", threshold = 50)
    @PostMapping("/{ruleCode}/reject")
    @AuthApiPermission(apiCodes = "execution:rule:approve")
    public BaseResponse<RuleDefinitionVO> reject(@PathVariable String ruleCode,
                                          @Valid @RequestBody RuleRejectDTO dto,
                                          @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleDefinition def = ruleAdminService.getByCode(ruleCode);
        if (def == null) {
            return BaseResponse.error(LiteruleExceptionCode.RULE_NOT_FOUND, "规则不存在: " + ruleCode);
        }

        RuleStatus current = parseStatusSafely(def.getStatus());
        if (!current.canTransitionTo(RuleStatus.ARCHIVED)) {
            return BaseResponse.error(LiteruleExceptionCode.RULE_STATUS_INVALID, "当前状态 " + current.getDesc() + " 不允许驳回，仅 DRAFT/REVIEW/PUBLISHED 可驳回");
        }

        String reason = dto.getReason();
        // @NotBlank 已校验非空，移除手动校验

        // 记录驳回留痕
        def.setStatus(RuleStatus.ARCHIVED.name());
        def.setReviewedBy(operator);
        def.setReviewedAt(LocalDateTime.now().toString());
        def.setReviewComment("[驳回] " + reason);
        def.setEnabled(false);

        String changeDesc = String.format("[审批驳回] %s -> ARCHIVED, 审批人=%s, 理由=%s",
                current.getDesc(), operator, reason);
        return BaseResponse.success(LiteruleConverter.INSTANT.entityToVO(ruleAdminService.save(def, operator, changeDesc)));
    }

    /**
     * 安全解析规则状态，无效值回退到 PUBLISHED
     */
    private RuleStatus parseStatusSafely(String status) {
        try {
            return RuleStatus.valueOf(status);
        } catch (Exception e) {
            return RuleStatus.PUBLISHED;
        }
    }

    /**
     * 提交审核（P1-3 多级审批流）
     *
     * <p>将规则从 DRAFT 状态提交到指定审批流的第一级。flowCode 为空时使用默认 2 级审批流。
     *
     * @param ruleCode 规则编码
     * @param dto      请求体，包含 flowCode（可选）
     * @param operator 操作人
     * @return 审批记录
     */
    @Idempotent(key = "ruleAdmin:submitReview", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'submitReview'")
    @RateLimit(resource = "literule.rule_lifecycle.submitReview", threshold = 50)
    @PostMapping("/{ruleCode}/submitReview")
    @AuthApiPermission(apiCodes = "execution:rule:save")
    public BaseResponse<ApprovalRecordVO> submitReview(@PathVariable String ruleCode,
                                                @Valid @RequestBody(required = false) RuleSubmitReviewDTO dto,
                                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "多级审批流服务未启用");
        }
        String flowCode = dto == null ? null : dto.getFlowCode();
        return BaseResponse.success(LiteruleWebConverter.INSTANT.entityToVO(svc.submitForReview(ruleCode, flowCode, operator)));
    }

    /**
     * 级别审批通过（P1-3 多级审批流）
     *
     * <p>审批通过当前级别。根据审批类型（SINGLE/COUNTERSIGN/SEQUENCE）决定是否进入下一级。
     * 全部级别通过后规则状态变为 PUBLISHED。
     *
     * @param ruleCode 规则编码
     * @param dto      请求体，包含 comment（审批意见）
     * @param operator 审批人
     * @return 审批记录
     */
    @Idempotent(key = "ruleAdmin:approveLevel", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'approveLevel'")
    @RateLimit(resource = "literule.rule_lifecycle.approveLevel", threshold = 50)
    @PostMapping("/{ruleCode}/approveLevel")
    @AuthApiPermission(apiCodes = "execution:rule:approve")
    public BaseResponse<ApprovalRecordVO> approveLevel(@PathVariable String ruleCode,
                                                @Valid @RequestBody RuleApproveDTO dto,
                                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "多级审批流服务未启用");
        }
        String comment = dto.getComment() == null ? "" : dto.getComment();
        return BaseResponse.success(LiteruleWebConverter.INSTANT.entityToVO(svc.approve(ruleCode, operator, comment)));
    }

    /**
     * 级别审批驳回（P1-3 多级审批流）
     *
     * <p>驳回当前级别，回退到上一级。一级驳回回退到 DRAFT。
     *
     * @param ruleCode 规则编码
     * @param dto      请求体，包含 reason（驳回理由，必填）
     * @param operator 审批人
     * @return 审批记录
     */
    @Idempotent(key = "ruleAdmin:rejectLevel", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'rejectLevel'")
    @RateLimit(resource = "literule.rule_lifecycle.rejectLevel", threshold = 50)
    @PostMapping("/{ruleCode}/rejectLevel")
    @AuthApiPermission(apiCodes = "execution:rule:approve")
    public BaseResponse<ApprovalRecordVO> rejectLevel(@PathVariable String ruleCode,
                                               @Valid @RequestBody RuleRejectDTO dto,
                                               @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "多级审批流服务未启用");
        }
        return BaseResponse.success(LiteruleWebConverter.INSTANT.entityToVO(svc.reject(ruleCode, operator, dto.getReason())));
    }

    /**
     * 委托审批（P1-3 多级审批流）
     *
     * <p>将当前级别的审批权委托给他人。委托后被委托人通过 approve-level 完成审批。
     *
     * @param ruleCode 规则编码
     * @param dto      请求体，包含 delegatedTo（被委托人工号，必填）和 comment（委托说明）
     * @param operator 委托人
     * @return 审批记录
     */
    @Idempotent(key = "ruleAdmin:delegate", ttlSeconds = 5, message = "请勿重复提交")
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'delegate'")
    @RateLimit(resource = "literule.rule_lifecycle.delegate", threshold = 50)
    @PostMapping("/{ruleCode}/delegate")
    @AuthApiPermission(apiCodes = "execution:rule:approve")
    public BaseResponse<ApprovalRecordVO> delegate(@PathVariable String ruleCode,
                                            @Valid @RequestBody RuleDelegateDTO dto,
                                            @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "多级审批流服务未启用");
        }
        String comment = dto.getComment() == null ? "" : dto.getComment();
        return BaseResponse.success(LiteruleWebConverter.INSTANT.entityToVO(svc.delegate(ruleCode, operator, dto.getDelegatedTo(), comment)));
    }

    /**
     * 查询审批状态（P1-3 多级审批流）
     *
     * @param ruleCode 规则编码
     * @return 审批记录；无审批记录时返回 null
     */
    @GetMapping("/{ruleCode}/approvalStatus")
    public BaseResponse<ApprovalRecordVO> approvalStatus(@PathVariable String ruleCode) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.success((ApprovalRecordVO) null);
        }
        return BaseResponse.success(LiteruleWebConverter.INSTANT.entityToVO(svc.getApprovalStatus(ruleCode)));
    }

    /**
     * 查询待审批列表（P1-3 多级审批流）
     *
     * @param approver 审批人工号
     * @return 待审批记录列表
     */
    @GetMapping("/pendingApprovals")
    public BaseResponse<List<ApprovalRecordVO>> pendingApprovals(@RequestParam String approver) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.success(List.of());
        }
        return BaseResponse.success(svc.listPendingApprovals(approver).stream().map(LiteruleWebConverter.INSTANT::entityToVO).toList());
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
    @Audit(module = "规则管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'cancelReview'")
    @RateLimit(resource = "literule.rule_lifecycle.cancelReview", threshold = 50)
    @PostMapping("/{ruleCode}/cancelReview")
    @AuthApiPermission(apiCodes = "execution:rule:save")
    public BaseResponse<ApprovalRecordVO> cancelReview(@PathVariable String ruleCode,
                                                @RequestHeader(value = "X-Operator", defaultValue = "SYSTEM") String operator) {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.error(BaseResultCode.FORBIDDEN, "多级审批流服务未启用");
        }
        return BaseResponse.success(LiteruleWebConverter.INSTANT.entityToVO(svc.cancelReview(ruleCode, operator)));
    }

    /**
     * 查询可用审批流配置（P1-3 多级审批流）
     *
     * @return 审批流配置列表
     */
    @GetMapping("/approvalFlows")
    public BaseResponse<List<ApprovalFlowVO>> approvalFlows() {
        RuleApprovalService svc = ruleApprovalServiceProvider.getIfAvailable();
        if (svc == null) {
            return BaseResponse.success(List.of());
        }
        return BaseResponse.success(svc.listFlows().stream().map(LiteruleWebConverter.INSTANT::entityToVO).toList());
    }
}
