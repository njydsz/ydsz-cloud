package com.remisoft.literule.server.approval;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.remisoft.literule.api.RuleDefinition;
import com.remisoft.literule.api.RuleStatus;
import com.remisoft.literule.server.spi.RuleConfigProvider;

import lombok.extern.slf4j.Slf4j;

/**
 * 规则审批流服务（P1-3 多级审批流）
 *
 * <p>提供多级审批流的核心能力，包括提交审核、级别审批通过/驳回、委托、撤回、
 * 查询审批状态与待审批列表。支持三种审批类型：
 * <ul>
 *   <li>{@link ApprovalType#SINGLE} - 单人审批，任一有权限者通过即进入下一级</li>
 *   <li>{@link ApprovalType#COUNTERSIGN} - 会签，所有指定人都需通过才进入下一级</li>
 *   <li>{@link ApprovalType#SEQUENCE} - 顺序审批，按 approvers 列表顺序依次审批</li>
 * </ul>
 *
 * <p>审批流配置与审批记录默认使用内存 Map 存储；消费方可通过
 * {@link ApprovalRecordRepository} 与自定义 {@link ApprovalFlowRegistry}
 * 提供持久化实现。权限校验通过 {@link ApprovalPermissionChecker} SPI 委托给消费方。
 *
 * <p>向后兼容：现有 approve/reject 单级审批端点保留不变；新端点为增量。
 * 单级审批流（maxLevel=1）使用 {@link RuleStatus#REVIEW} 状态，
 * 与既有单级审批完全兼容。
 *
 * @author remi-team
 *
 * @since 1.0.0
 */
@Slf4j
public class RuleApprovalService {

    /** 默认审批流编码（2 级审批） */
    public static final String DEFAULT_FLOW_CODE = "default-2level";

    private final RuleConfigProvider configProvider;

    /** 审批流配置注册表（flowCode -> ApprovalFlow） */
    private final Map<String, ApprovalFlow> flowRegistry = new ConcurrentHashMap<>();

    /** 审批记录存储（ruleCode -> ApprovalRecord） */
    private final Map<String, ApprovalRecord> recordStore = new ConcurrentHashMap<>();

    /** 审批记录持久化仓库（可选 SPI） */
    private ApprovalRecordRepository recordRepository;

    /** 审批权限检查器（可选 SPI） */
    private ApprovalPermissionChecker permissionChecker;

    /** P1-5: 工作流引擎桥接（可选 SPI，用于将审批事件转发到 workflow 模块） */
    private RuleApprovalWorkflowBridge workflowBridge;

    /**
     * 构造审批流服务
     *
     * @param configProvider 规则配置提供者（用于读写规则定义的 status 字段）
     */
    public RuleApprovalService(RuleConfigProvider configProvider) {
        this.configProvider = configProvider;
        // 注册默认审批流（2 级审批）
        registerDefaultFlows();
    }

    /**
     * 注册默认审批流
     *
     * <p>包含一个 2 级审批流（default-2level）：
     * <ul>
     *   <li>Level 1: SINGLE，角色 execution:rule:approve</li>
     *   <li>Level 2: SINGLE，角色 execution:rule:approve:final</li>
     * </ul>
     */
    private void registerDefaultFlows() {
        ApprovalFlow defaultFlow = ApprovalFlow.builder()
                .flowCode(DEFAULT_FLOW_CODE)
                .name("默认 2 级审批流")
                .enabled(true)
                .steps(List.of(
                        ApprovalStep.builder()
                                .level(1)
                                .name("一级审核")
                                .type(ApprovalType.SINGLE)
                                .approverRoles(List.of("execution:rule:approve"))
                                .allowDelegate(true)
                                .build(),
                        ApprovalStep.builder()
                                .level(2)
                                .name("二级审核")
                                .type(ApprovalType.SINGLE)
                                .approverRoles(List.of("execution:rule:approve:final"))
                                .allowDelegate(true)
                                .build()
                ))
                .build();
        flowRegistry.put(defaultFlow.getFlowCode(), defaultFlow);
    }

    /**
     * 设置审批记录持久化仓库
     *
     * @param recordRepository 持久化仓库
     */
    public void setRecordRepository(ApprovalRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    /**
     * 设置审批权限检查器
     *
     * @param permissionChecker 权限检查器
     */
    public void setPermissionChecker(ApprovalPermissionChecker permissionChecker) {
        this.permissionChecker = permissionChecker;
    }

    /**
     * P1-5: 设置工作流引擎桥接
     *
     * <p>设置后，审批事件（提交/通过/驳回/委托）会同步通知 workflow 模块，
     * 实现两套审批系统的数据打通。
     *
     * @param workflowBridge 工作流桥接
     */
    public void setWorkflowBridge(RuleApprovalWorkflowBridge workflowBridge) {
        this.workflowBridge = workflowBridge;
    }

    /**
     * 注册自定义审批流
     *
     * @param flow 审批流配置
     */
    public void registerFlow(ApprovalFlow flow) {
        if (flow == null || flow.getFlowCode() == null || flow.getFlowCode().isBlank()) {
            throw new IllegalArgumentException("审批流配置非法：flowCode 不能为空");
        }
        if (flow.getSteps() == null || flow.getSteps().isEmpty()) {
            throw new IllegalArgumentException("审批流配置非法：steps 不能为空: " + flow.getFlowCode());
        }
        flowRegistry.put(flow.getFlowCode(), flow);
        log.info("[Approval] 审批流已注册: flowCode={}, name={}, levels={}",
                flow.getFlowCode(), flow.getName(), flow.maxLevel());
    }

    /**
     * 查询全部已注册的审批流
     *
     * @return 审批流列表
     */
    public List<ApprovalFlow> listFlows() {
        return new ArrayList<>(flowRegistry.values());
    }

    /**
     * 查询指定审批流
     *
     * @param flowCode 流程编码
     * @return 审批流；不存在返回 null
     */
    public ApprovalFlow getFlow(String flowCode) {
        return flowRegistry.get(flowCode);
    }

    // ==================== 核心审批操作 ====================

    /**
     * 提交审核
     *
     * <p>将规则从 DRAFT 状态提交到多级审批流的第一级。规则状态变更为
     * {@link RuleStatus#REVIEW_L1}（多级）或 {@link RuleStatus#REVIEW}（单级兼容）。
     *
     * @param ruleCode 规则编码
     * @param flowCode 审批流编码（null 时使用默认 2 级审批流）
     * @param operator 操作人
     * @return 审批记录
     * @throws IllegalArgumentException 规则不存在、状态非法、审批流不存在
     */
    public synchronized ApprovalRecord submitForReview(String ruleCode, String flowCode, String operator) {
        requireNonBlank(ruleCode, "ruleCode");
        requireNonBlank(operator, "operator");

        RuleDefinition def = loadRule(ruleCode);
        RuleStatus current = parseStatus(def.getStatus());
        ApprovalFlow flow = resolveFlow(flowCode);

        RuleStatus firstLevelStatus = levelToStatus(1, flow.maxLevel());
        if (!current.canTransitionTo(firstLevelStatus)) {
            throw new IllegalStateException("当前状态 " + current.getDesc()
                    + " 不允许提交审核，仅 DRAFT 可提交");
        }

        // 创建审批记录
        ApprovalRecord record = ApprovalRecord.builder()
                .recordId(generateRecordId())
                .ruleCode(ruleCode)
                .flowCode(flow.getFlowCode())
                .currentLevel(1)
                .currentStatus(ApprovalRecord.STATUS_PENDING)
                .currentLevelApprovedApprovers(new ArrayList<>())
                .logs(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        record.appendLog(ApprovalLog.builder()
                .level(1)
                .approver(operator)
                .action(ApprovalLog.ACTION_SUBMIT)
                .comment("提交" + flow.getName())
                .timestamp(LocalDateTime.now())
                .build());

        // 更新规则状态到第一级
        updateRuleStatus(def, firstLevelStatus, operator, "提交审核: " + flow.getName());

        saveRecord(record);
        log.info("[Approval] 规则已提交审核: ruleCode={}, flow={}, operator={}",
                ruleCode, flow.getFlowCode(), operator);
        // P1-5: 通知工作流引擎
        notifyWorkflowBridge(b -> b.onApprovalSubmitted(ruleCode, flow.getFlowCode(), operator));
        return record;
    }

    /**
     * 审批通过（当前级别）
     *
     * <p>根据当前级别的审批类型决定是否进入下一级：
     * <ul>
     *   <li>SINGLE：任一有权限者通过即进入下一级</li>
     *   <li>COUNTERSIGN：所有指定人都需通过才进入下一级</li>
     *   <li>SEQUENCE：按 approvers 顺序依次审批</li>
     * </ul>
     * 全部级别通过后，规则状态变为 PUBLISHED。
     *
     * @param ruleCode 规则编码
     * @param operator 审批人
     * @param comment  审批意见
     * @return 审批记录
     * @throws IllegalArgumentException 规则不存在、审批记录不存在
     * @throws IllegalStateException    状态非法（非 PENDING/DELEGATED）
     * @throws SecurityException        无权限审批
     */
    public synchronized ApprovalRecord approve(String ruleCode, String operator, String comment) {
        requireNonBlank(ruleCode, "ruleCode");
        requireNonBlank(operator, "operator");

        ApprovalRecord record = loadRecordForAction(ruleCode);
        ApprovalFlow flow = resolveFlow(record.getFlowCode());
        ApprovalStep step = flow.getStep(record.getCurrentLevel());
        if (step == null) {
            throw new IllegalStateException("审批步骤不存在: level=" + record.getCurrentLevel()
                    + ", flow=" + flow.getFlowCode());
        }

        // 校验权限（考虑委托场景）
        validateApprovePermission(operator, step, record);

        // COUNTERSIGN：不允许同一人重复通过
        if (step.getType() == ApprovalType.COUNTERSIGN
                && record.getCurrentLevelApprovedApprovers().contains(operator)) {
            throw new IllegalStateException("会签场景下审批人已通过当前级别: " + operator);
        }

        // SEQUENCE：必须是下一个该审批的人
        if (step.getType() == ApprovalType.SEQUENCE) {
            validateSequenceApprover(operator, step, record);
        }

        // 追加通过日志
        record.appendLog(ApprovalLog.builder()
                .level(record.getCurrentLevel())
                .approver(operator)
                .action(ApprovalLog.ACTION_APPROVE)
                .comment(comment)
                .timestamp(LocalDateTime.now())
                .build());

        // 判断当前级别是否通过
        boolean levelPassed = checkLevelPassed(step, record, operator);

        if (!levelPassed) {
            // 当前级别未全部通过，保持 PENDING，记录已通过审批人
            record.getCurrentLevelApprovedApprovers().add(operator);
            // 委托状态审批后恢复 PENDING
            record.setCurrentStatus(ApprovalRecord.STATUS_PENDING);
            saveRecord(record);
            log.info("[Approval] 审批人通过但当前级别未完成: ruleCode={}, level={}, approver={}, type={}",
                    ruleCode, record.getCurrentLevel(), operator, step.getType());
            return record;
        }

        // 当前级别通过，进入下一级
        record.getCurrentLevelApprovedApprovers().clear();
        int nextLevel = record.getCurrentLevel() + 1;
        RuleDefinition def = loadRule(ruleCode);

        if (nextLevel > flow.maxLevel()) {
            // 全部级别通过，发布规则
            RuleStatus currentStatus = parseStatus(def.getStatus());
            if (!currentStatus.canTransitionTo(RuleStatus.PUBLISHED)) {
                throw new IllegalStateException("当前状态 " + currentStatus.getDesc()
                        + " 不允许变更为 PUBLISHED");
            }
            updateRuleStatus(def, RuleStatus.PUBLISHED, operator,
                    "审批通过: 全部 " + flow.maxLevel() + " 级已完成");
            record.setCurrentLevel(flow.maxLevel());
            record.setCurrentStatus(ApprovalRecord.STATUS_APPROVED);
            log.info("[Approval] 规则全部审批通过已发布: ruleCode={}, flow={}, operator={}",
                    ruleCode, flow.getFlowCode(), operator);
            // P1-5: 通知工作流引擎（全部通过）
            notifyWorkflowBridge(b -> b.onApprovalPassed(ruleCode, record.getCurrentLevel(), operator, comment, true));
        } else {
            // 进入下一级
            RuleStatus nextStatus = levelToStatus(nextLevel, flow.maxLevel());
            RuleStatus currentStatus = parseStatus(def.getStatus());
            if (!currentStatus.canTransitionTo(nextStatus)) {
                throw new IllegalStateException("当前状态 " + currentStatus.getDesc()
                        + " 不允许变更为 " + nextStatus.getDesc());
            }
            updateRuleStatus(def, nextStatus, operator,
                    "通过第 " + record.getCurrentLevel() + " 级，进入第 " + nextLevel + " 级");
            record.setCurrentLevel(nextLevel);
            record.setCurrentStatus(ApprovalRecord.STATUS_PENDING);
            log.info("[Approval] 规则进入下一级审批: ruleCode={}, level={}, operator={}",
                    ruleCode, nextLevel, operator);
            // P1-5: 通知工作流引擎（当前级别通过）
            notifyWorkflowBridge(b -> b.onApprovalPassed(ruleCode, record.getCurrentLevel() - 1, operator, comment, false));
        }

        saveRecord(record);
        return record;
    }

    /**
     * 审批驳回（回退到上一级）
     *
     * <p>驳回语义：当前级别驳回后回退到上一级。
     * <ul>
     *   <li>一级驳回：状态回退到 DRAFT，审批记录状态变为 CANCELLED</li>
     *   <li>二级驳回：状态回退到 REVIEW_L1，当前级别变为 1</li>
     *   <li>终审驳回：状态回退到 REVIEW_L2，当前级别变为 2</li>
     * </ul>
     *
     * @param ruleCode 规则编码
     * @param operator 审批人
     * @param reason   驳回理由
     * @return 审批记录
     */
    public synchronized ApprovalRecord reject(String ruleCode, String operator, String reason) {
        requireNonBlank(ruleCode, "ruleCode");
        requireNonBlank(operator, "operator");
        requireNonBlank(reason, "reason");

        ApprovalRecord record = loadRecordForAction(ruleCode);
        ApprovalFlow flow = resolveFlow(record.getFlowCode());
        ApprovalStep step = flow.getStep(record.getCurrentLevel());
        if (step == null) {
            throw new IllegalStateException("审批步骤不存在: level=" + record.getCurrentLevel());
        }

        validateApprovePermission(operator, step, record);

        record.appendLog(ApprovalLog.builder()
                .level(record.getCurrentLevel())
                .approver(operator)
                .action(ApprovalLog.ACTION_REJECT)
                .comment(reason)
                .timestamp(LocalDateTime.now())
                .build());

        RuleDefinition def = loadRule(ruleCode);
        int currentLevel = record.getCurrentLevel();

        if (currentLevel <= 1) {
            // 一级驳回：回退到 DRAFT
            RuleStatus currentStatus = parseStatus(def.getStatus());
            if (!currentStatus.canTransitionTo(RuleStatus.DRAFT)) {
                throw new IllegalStateException("当前状态 " + currentStatus.getDesc()
                        + " 不允许驳回回 DRAFT");
            }
            updateRuleStatus(def, RuleStatus.DRAFT, operator, "一级驳回: " + reason);
            record.setCurrentStatus(ApprovalRecord.STATUS_CANCELLED);
            record.getCurrentLevelApprovedApprovers().clear();
            log.info("[Approval] 规则一级驳回回草稿: ruleCode={}, operator={}", ruleCode, operator);
        } else {
            // 二级/终审驳回：回退到上一级
            int previousLevel = currentLevel - 1;
            RuleStatus previousStatus = levelToStatus(previousLevel, flow.maxLevel());
            RuleStatus currentStatus = parseStatus(def.getStatus());
            if (!currentStatus.canTransitionTo(previousStatus)) {
                throw new IllegalStateException("当前状态 " + currentStatus.getDesc()
                        + " 不允许驳回回 " + previousStatus.getDesc());
            }
            updateRuleStatus(def, previousStatus, operator,
                    "第 " + currentLevel + " 级驳回，回退到第 " + previousLevel + " 级: " + reason);
            record.setCurrentLevel(previousLevel);
            record.setCurrentStatus(ApprovalRecord.STATUS_PENDING);
            record.getCurrentLevelApprovedApprovers().clear();
            log.info("[Approval] 规则驳回回上一级: ruleCode={}, fromLevel={}, toLevel={}, operator={}",
                    ruleCode, currentLevel, previousLevel, operator);
        }

        saveRecord(record);
        // P1-5: 通知工作流引擎（驳回）
        int toLevel = currentLevel <= 1 ? 0 : currentLevel - 1;
        notifyWorkflowBridge(b -> b.onApprovalRejected(ruleCode, currentLevel, toLevel, operator, reason));
        return record;
    }

    /**
     * 委托审批
     *
     * <p>将当前级别的审批权委托给他人。委托后审批记录状态变为 DELEGATED，
     * 被委托人通过 {@link #approve} 完成审批后状态恢复 PENDING。
     *
     * @param ruleCode    规则编码
     * @param operator    委托人
     * @param delegatedTo 被委托人工号
     * @param comment     委托说明
     * @return 审批记录
     */
    public synchronized ApprovalRecord delegate(String ruleCode, String operator, String delegatedTo, String comment) {
        requireNonBlank(ruleCode, "ruleCode");
        requireNonBlank(operator, "operator");
        requireNonBlank(delegatedTo, "delegatedTo");

        ApprovalRecord record = loadRecordForAction(ruleCode);
        ApprovalFlow flow = resolveFlow(record.getFlowCode());
        ApprovalStep step = flow.getStep(record.getCurrentLevel());
        if (step == null) {
            throw new IllegalStateException("审批步骤不存在: level=" + record.getCurrentLevel());
        }
        if (!step.isAllowDelegate()) {
            throw new IllegalStateException("当前步骤不允许委托: level=" + record.getCurrentLevel());
        }

        validateApprovePermission(operator, step, record);

        if (operator.equals(delegatedTo)) {
            throw new IllegalArgumentException("不允许委托给自己: " + operator);
        }

        record.appendLog(ApprovalLog.builder()
                .level(record.getCurrentLevel())
                .approver(operator)
                .action(ApprovalLog.ACTION_DELEGATE)
                .comment(comment)
                .delegatedTo(delegatedTo)
                .timestamp(LocalDateTime.now())
                .build());
        record.setCurrentStatus(ApprovalRecord.STATUS_DELEGATED);

        saveRecord(record);
        log.info("[Approval] 审批已委托: ruleCode={}, level={}, from={}, to={}",
                ruleCode, record.getCurrentLevel(), operator, delegatedTo);
        // P1-5: 通知工作流引擎（委托）
        notifyWorkflowBridge(b -> b.onApprovalDelegated(ruleCode, record.getCurrentLevel(), operator, delegatedTo));
        return record;
    }

    /**
     * 撤回审核
     *
     * <p>将规则从审核中状态撤回到 DRAFT。仅 PENDING/DELEGATED 状态可撤回。
     *
     * @param ruleCode 规则编码
     * @param operator 操作人
     * @return 审批记录
     */
    public synchronized ApprovalRecord cancelReview(String ruleCode, String operator) {
        requireNonBlank(ruleCode, "ruleCode");
        requireNonBlank(operator, "operator");

        ApprovalRecord record = loadRecord(ruleCode);
        if (record == null) {
            throw new IllegalArgumentException("审批记录不存在: " + ruleCode);
        }
        if (!ApprovalRecord.STATUS_PENDING.equals(record.getCurrentStatus())
                && !ApprovalRecord.STATUS_DELEGATED.equals(record.getCurrentStatus())) {
            throw new IllegalStateException("当前审批状态不允许撤回: " + record.getCurrentStatus());
        }

        record.appendLog(ApprovalLog.builder()
                .level(record.getCurrentLevel())
                .approver(operator)
                .action(ApprovalLog.ACTION_CANCEL)
                .comment("撤回审核")
                .timestamp(LocalDateTime.now())
                .build());
        record.setCurrentStatus(ApprovalRecord.STATUS_CANCELLED);
        record.getCurrentLevelApprovedApprovers().clear();

        // 规则状态回退到 DRAFT
        RuleDefinition def = loadRule(ruleCode);
        RuleStatus currentStatus = parseStatus(def.getStatus());
        if (currentStatus.canTransitionTo(RuleStatus.DRAFT)) {
            updateRuleStatus(def, RuleStatus.DRAFT, operator, "撤回审核");
        }

        saveRecord(record);
        log.info("[Approval] 审核已撤回: ruleCode={}, operator={}", ruleCode, operator);
        return record;
    }

    // ==================== 查询方法 ====================

    /**
     * 查询审批状态
     *
     * @param ruleCode 规则编码
     * @return 审批记录；不存在返回 null
     */
    public ApprovalRecord getApprovalStatus(String ruleCode) {
        return loadRecord(ruleCode);
    }

    /**
     * 查询待审批列表
     *
     * <p>返回当前需要指定审批人处理的审批记录：
     * <ul>
     *   <li>PENDING 状态：审批人在当前步骤的 approvers 中，或具备 approverRoles 权限</li>
     *   <li>DELEGATED 状态：审批人是被委托人</li>
     *   <li>COUNTERSIGN：审批人在 approvers 中且尚未通过</li>
     *   <li>SEQUENCE：审批人是 approvers 中下一个该审批的人</li>
     * </ul>
     *
     * @param approver 审批人工号
     * @return 待审批记录列表
     */
    public List<ApprovalRecord> listPendingApprovals(String approver) {
        requireNonBlank(approver, "approver");
        List<ApprovalRecord> result = new ArrayList<>();
        for (ApprovalRecord record : recordStore.values()) {
            if (!ApprovalRecord.STATUS_PENDING.equals(record.getCurrentStatus())
                    && !ApprovalRecord.STATUS_DELEGATED.equals(record.getCurrentStatus())) {
                continue;
            }
            if (isApproverForCurrentLevel(approver, record)) {
                result.add(record);
            }
        }
        return result;
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 判断指定审批人是否需要处理当前记录
     */
    private boolean isApproverForCurrentLevel(String approver, ApprovalRecord record) {
        ApprovalFlow flow = flowRegistry.get(record.getFlowCode());
        if (flow == null) {
            return false;
        }
        ApprovalStep step = flow.getStep(record.getCurrentLevel());
        if (step == null) {
            return false;
        }

        // 委托状态：只有被委托人需要处理
        if (ApprovalRecord.STATUS_DELEGATED.equals(record.getCurrentStatus())) {
            return approver.equals(findLatestDelegateTo(record));
        }

        // COUNTERSIGN：已通过的人不需要再次处理
        if (step.getType() == ApprovalType.COUNTERSIGN
                && record.getCurrentLevelApprovedApprovers().contains(approver)) {
            return false;
        }

        // SEQUENCE：只有下一个该审批的人需要处理
        if (step.getType() == ApprovalType.SEQUENCE) {
            String nextApprover = nextSequenceApprover(step, record);
            return approver.equals(nextApprover);
        }

        // 指定了审批人列表：必须在列表中
        if (step.getApprovers() != null && !step.getApprovers().isEmpty()) {
            return step.getApprovers().contains(approver);
        }

        // 未指定审批人列表：由权限检查器决定（无检查器时放行）
        if (permissionChecker != null) {
            return permissionChecker.hasApprovePermission(approver, step);
        }
        // 无 approvers 也无权限检查器，视为无需该 approver 处理
        return false;
    }

    /**
     * 加载规则定义
     */
    private RuleDefinition loadRule(String ruleCode) {
        RuleDefinition def = configProvider.findByCode(ruleCode);
        if (def == null) {
            throw new IllegalArgumentException("规则不存在: " + ruleCode);
        }
        return def;
    }

    /**
     * 解析审批流（null 时使用默认）
     */
    private ApprovalFlow resolveFlow(String flowCode) {
        String code = (flowCode == null || flowCode.isBlank()) ? DEFAULT_FLOW_CODE : flowCode;
        ApprovalFlow flow = flowRegistry.get(code);
        if (flow == null) {
            throw new IllegalArgumentException("审批流不存在: " + code);
        }
        if (!flow.isEnabled()) {
            throw new IllegalStateException("审批流已禁用: " + code);
        }
        return flow;
    }

    /**
     * 加载审批记录（用于审批动作，必须存在且状态合法）
     */
    private ApprovalRecord loadRecordForAction(String ruleCode) {
        ApprovalRecord record = loadRecord(ruleCode);
        if (record == null) {
            throw new IllegalArgumentException("审批记录不存在: " + ruleCode);
        }
        if (!ApprovalRecord.STATUS_PENDING.equals(record.getCurrentStatus())
                && !ApprovalRecord.STATUS_DELEGATED.equals(record.getCurrentStatus())) {
            throw new IllegalStateException("审批记录状态非 PENDING/DELEGATED: "
                    + record.getCurrentStatus());
        }
        return record;
    }

    /**
     * 加载审批记录（先查内存，再查持久化仓库）
     */
    private ApprovalRecord loadRecord(String ruleCode) {
        ApprovalRecord record = recordStore.get(ruleCode);
        if (record == null && recordRepository != null) {
            record = recordRepository.findByRuleCode(ruleCode);
            if (record != null) {
                recordStore.put(ruleCode, record);
            }
        }
        return record;
    }

    /**
     * 保存审批记录（内存 + 持久化仓库）
     */
    private void saveRecord(ApprovalRecord record) {
        recordStore.put(record.getRuleCode(), record);
        if (recordRepository != null) {
            try {
                recordRepository.save(record);
            } catch (Exception e) {
                log.warn("[Approval] 审批记录持久化失败: ruleCode={}, err={}",
                        record.getRuleCode(), e.getMessage());
            }
        }
    }

    /**
     * 校验审批权限
     */
    private void validateApprovePermission(String operator, ApprovalStep step, ApprovalRecord record) {
        // 委托状态：只有被委托人有权限
        if (ApprovalRecord.STATUS_DELEGATED.equals(record.getCurrentStatus())) {
            String delegateTo = findLatestDelegateTo(record);
            if (delegateTo == null || !delegateTo.equals(operator)) {
                throw new SecurityException("当前审批已委托给 " + delegateTo + "，无权操作: " + operator);
            }
            return;
        }

        // 指定了审批人列表：必须在列表中
        if (step.getApprovers() != null && !step.getApprovers().isEmpty()) {
            if (!step.getApprovers().contains(operator)) {
                throw new SecurityException("审批人不在指定审批人列表中: " + operator);
            }
            return;
        }

        // 使用权限检查器
        if (permissionChecker != null) {
            if (!permissionChecker.hasApprovePermission(operator, step)) {
                throw new SecurityException("无审批权限: " + operator);
            }
        }
        // 无 approvers 也无权限检查器，放行（便于单元测试与开发环境调试）
    }

    /**
     * SEQUENCE 类型：校验是否是下一个该审批的人
     */
    private void validateSequenceApprover(String operator, ApprovalStep step, ApprovalRecord record) {
        String next = nextSequenceApprover(step, record);
        if (next == null || !next.equals(operator)) {
            throw new IllegalStateException("顺序审批场景下当前应审批人: " + next
                    + "，实际操作人: " + operator);
        }
    }

    /**
     * SEQUENCE 类型：获取下一个该审批的人
     */
    private String nextSequenceApprover(ApprovalStep step, ApprovalRecord record) {
        if (step.getApprovers() == null || step.getApprovers().isEmpty()) {
            return null;
        }
        int alreadyApproved = record.getCurrentLevelApprovedApprovers().size();
        if (alreadyApproved >= step.getApprovers().size()) {
            return null;
        }
        return step.getApprovers().get(alreadyApproved);
    }

    /**
     * 判断当前级别是否通过
     */
    private boolean checkLevelPassed(ApprovalStep step, ApprovalRecord record, String currentApprover) {
        switch (step.getType()) {
            case SINGLE:
                // 单人审批：当前审批人通过即视为本级通过
                return true;
            case COUNTERSIGN:
                // 会签：已通过人数 + 1 >= requiredCount
                int required = step.getRequiredCount() > 0
                        ? step.getRequiredCount()
                        : (step.getApprovers() != null ? step.getApprovers().size() : 1);
                int approvedCount = record.getCurrentLevelApprovedApprovers().size() + 1;
                return approvedCount >= required;
            case SEQUENCE:
                // 顺序：全部 approvers 都通过
                int total = step.getApprovers() != null ? step.getApprovers().size() : 1;
                return record.getCurrentLevelApprovedApprovers().size() + 1 >= total;
            default:
                return true;
        }
    }

    /**
     * 查找最新的被委托人
     */
    private String findLatestDelegateTo(ApprovalRecord record) {
        if (record.getLogs() == null) {
            return null;
        }
        for (int i = record.getLogs().size() - 1; i >= 0; i--) {
            ApprovalLog log = record.getLogs().get(i);
            if (ApprovalLog.ACTION_DELEGATE.equals(log.getAction())
                    && log.getLevel() == record.getCurrentLevel()) {
                return log.getDelegatedTo();
            }
        }
        return null;
    }

    /**
     * 更新规则状态
     */
    private void updateRuleStatus(RuleDefinition def, RuleStatus target, String operator, String changeDesc) {
        RuleStatus current = RuleStatus.fromCode(def.getStatus());
        if (current != null && !current.canTransitionTo(target)) {
            throw new IllegalStateException("不允许的状态转换: "
                    + (current != null ? current.getDesc() : "UNKNOWN") + " → " + target.getDesc());
        }
        def.setStatus(target.name());
        if (target == RuleStatus.PUBLISHED) {
            def.setEnabled(true);
            def.setReviewedBy(operator);
            def.setReviewedAt(LocalDateTime.now().toString());
        }
        configProvider.save(def, operator);
    }

    /**
     * 级别到状态的映射
     *
     * <p>单级审批流（maxLevel=1）使用 REVIEW 保持向后兼容；
     * 多级审批流使用 REVIEW_L1/REVIEW_L2/REVIEW_FINAL。
     */
    private RuleStatus levelToStatus(int level, int maxLevel) {
        if (maxLevel == 1 && level == 1) {
            return RuleStatus.REVIEW;
        }
        return switch (level) {
            case 1 -> RuleStatus.REVIEW_L1;
            case 2 -> RuleStatus.REVIEW_L2;
            case 3 -> RuleStatus.REVIEW_FINAL;
            default -> throw new IllegalArgumentException(
                    "不支持的审批级别: " + level + "（当前最多支持 3 级）");
        };
    }

    /**
     * 安全解析规则状态
     */
    private RuleStatus parseStatus(String status) {
        RuleStatus parsed = RuleStatus.fromCode(status);
        if (parsed == null) {
            return RuleStatus.PUBLISHED;
        }
        return parsed;
    }

    /**
     * 生成审批记录 ID
     */
    private String generateRecordId() {
        return "AR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * 校验字符串非空
     */
    private void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }

    /**
     * P1-5: 安全通知工作流引擎桥接（bridge=null 或通知失败均不影响主流程）
     *
     * @param action 通知动作
     */
    private void notifyWorkflowBridge(Consumer<RuleApprovalWorkflowBridge> action) {
        if (workflowBridge == null) {
            return;
        }
        try {
            action.accept(workflowBridge);
        } catch (Exception e) {
            log.warn("[Approval] 工作流桥接通知失败(不影响审批主流程): err={}", e.getMessage());
        }
    }

    /**
     * 获取全部审批记录快照（用于测试与监控）
     *
     * @return 不可修改的审批记录映射
     */
    public Map<String, ApprovalRecord> snapshotRecords() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(recordStore));
    }
}
