package com.njydsz.pmis.literule.approval;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleStatus;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * RuleApprovalService 单元测试（P1-3 多级审批流）
 *
 * <p>测试多级审批流的核心能力：提交审核、级别审批通过/驳回、会签、委托、撤回、
 * 待审批列表查询、非法状态转换、无权限审批、向后兼容。
 *
 * <p>测试风格参考 {@link com.njydsz.pmis.literule.core.DefaultRuleEngineTest}：
 * 使用 Mockito.mock 手动创建 mock，不使用 @ExtendWith。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("RuleApprovalService 单元测试")
class RuleApprovalServiceTest {

    private RuleConfigProvider configProvider;
    private Map<String, RuleDefinition> ruleStore;
    private RuleApprovalService service;

    @BeforeEach
    void setUp() {
        ruleStore = new HashMap<>();
        configProvider = Mockito.mock(RuleConfigProvider.class);
        // mock findByCode 从内存存储读取
        when(configProvider.findByCode(anyString())).thenAnswer(inv ->
                ruleStore.get(inv.getArgument(0)));
        // mock save 将规则写回内存存储（同一引用，状态变更会反映到存储）
        when(configProvider.save(any(RuleDefinition.class), anyString())).thenAnswer(inv -> {
            RuleDefinition def = inv.getArgument(0);
            ruleStore.put(def.getCode(), def);
            return def;
        });
        service = new RuleApprovalService(configProvider);
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建规则并放入内存存储
     */
    private RuleDefinition createRule(String code, String status) {
        RuleDefinition def = RuleDefinition.builder()
                .code(code)
                .name("规则-" + code)
                .conditionExpression("amount > 100")
                .status(status)
                .version(1)
                .build();
        ruleStore.put(code, def);
        return def;
    }

    /**
     * 注册会签审批流（单级 2 人会签）
     */
    private void registerCountersignFlow() {
        ApprovalFlow flow = ApprovalFlow.builder()
                .flowCode("countersign-1level")
                .name("会签审批流")
                .enabled(true)
                .steps(List.of(
                        ApprovalStep.builder()
                                .level(1)
                                .name("会签审核")
                                .type(ApprovalType.COUNTERSIGN)
                                .approvers(List.of("U001", "U002"))
                                .requiredCount(2)
                                .allowDelegate(false)
                                .build()
                ))
                .build();
        service.registerFlow(flow);
    }

    /**
     * 注册单级审批流（向后兼容测试）
     */
    private void registerSingleLevelFlow() {
        ApprovalFlow flow = ApprovalFlow.builder()
                .flowCode("single-1level")
                .name("单级审批流")
                .enabled(true)
                .steps(List.of(
                        ApprovalStep.builder()
                                .level(1)
                                .name("审核")
                                .type(ApprovalType.SINGLE)
                                .approverRoles(List.of("execution:rule:approve"))
                                .allowDelegate(false)
                                .build()
                ))
                .build();
        service.registerFlow(flow);
    }

    /**
     * 注册指定审批人的审批流（无权限测试）
     */
    private void registerRestrictedFlow() {
        ApprovalFlow flow = ApprovalFlow.builder()
                .flowCode("restricted-1level")
                .name("受限审批流")
                .enabled(true)
                .steps(List.of(
                        ApprovalStep.builder()
                                .level(1)
                                .name("受限审核")
                                .type(ApprovalType.SINGLE)
                                .approvers(List.of("U001"))
                                .allowDelegate(true)
                                .build()
                ))
                .build();
        service.registerFlow(flow);
    }

    // ==================== 默认 2 级审批流 ====================

    @Nested
    @DisplayName("默认 2 级审批流")
    class DefaultTwoLevelFlowTest {

        @Test
        @DisplayName("1. 提交审核：DRAFT → REVIEW_L1")
        void shouldTransitionFromDraftToReviewL1OnSubmit() {
            createRule("R1", "DRAFT");

            ApprovalRecord record = service.submitForReview("R1", null, "owner");

            assertThat(record.getCurrentLevel()).isEqualTo(1);
            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_PENDING);
            assertThat(record.getFlowCode()).isEqualTo(RuleApprovalService.DEFAULT_FLOW_CODE);
            assertThat(ruleStore.get("R1").getStatus()).isEqualTo(RuleStatus.REVIEW_L1.name());
            // 验证日志
            assertThat(record.getLogs()).hasSize(1);
            assertThat(record.getLogs().get(0).getAction()).isEqualTo(ApprovalLog.ACTION_SUBMIT);
        }

        @Test
        @DisplayName("2. 一级审批通过：REVIEW_L1 → REVIEW_L2")
        void shouldTransitionFromReviewL1ToReviewL2OnApprove() {
            createRule("R1", "DRAFT");
            service.submitForReview("R1", null, "owner");

            ApprovalRecord record = service.approve("R1", "approver1", "一级通过");

            assertThat(record.getCurrentLevel()).isEqualTo(2);
            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_PENDING);
            assertThat(ruleStore.get("R1").getStatus()).isEqualTo(RuleStatus.REVIEW_L2.name());
        }

        @Test
        @DisplayName("3. 二级审批通过：REVIEW_L2 → PUBLISHED")
        void shouldTransitionFromReviewL2ToPublishedOnApprove() {
            createRule("R1", "DRAFT");
            service.submitForReview("R1", null, "owner");
            service.approve("R1", "approver1", "一级通过");

            ApprovalRecord record = service.approve("R1", "approver2", "二级通过");

            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_APPROVED);
            assertThat(ruleStore.get("R1").getStatus()).isEqualTo(RuleStatus.PUBLISHED.name());
            assertThat(ruleStore.get("R1").isEnabled()).isTrue();
            assertThat(ruleStore.get("R1").getReviewedBy()).isEqualTo("approver2");
        }

        @Test
        @DisplayName("4. 一级驳回：REVIEW_L1 → DRAFT")
        void shouldTransitionFromReviewL1ToDraftOnReject() {
            createRule("R1", "DRAFT");
            service.submitForReview("R1", null, "owner");

            ApprovalRecord record = service.reject("R1", "approver1", "一级驳回");

            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_CANCELLED);
            assertThat(ruleStore.get("R1").getStatus()).isEqualTo(RuleStatus.DRAFT.name());
        }

        @Test
        @DisplayName("5. 二级驳回：REVIEW_L2 → REVIEW_L1")
        void shouldTransitionFromReviewL2ToReviewL1OnReject() {
            createRule("R1", "DRAFT");
            service.submitForReview("R1", null, "owner");
            service.approve("R1", "approver1", "一级通过");

            ApprovalRecord record = service.reject("R1", "approver2", "二级驳回");

            assertThat(record.getCurrentLevel()).isEqualTo(1);
            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_PENDING);
            assertThat(ruleStore.get("R1").getStatus()).isEqualTo(RuleStatus.REVIEW_L1.name());
        }

        @Test
        @DisplayName("8. 撤回审核：REVIEW_L1 → DRAFT")
        void shouldTransitionFromReviewL1ToDraftOnCancel() {
            createRule("R1", "DRAFT");
            service.submitForReview("R1", null, "owner");

            ApprovalRecord record = service.cancelReview("R1", "owner");

            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_CANCELLED);
            assertThat(ruleStore.get("R1").getStatus()).isEqualTo(RuleStatus.DRAFT.name());
        }

        @Test
        @DisplayName("10. 非法状态转换抛异常：PUBLISHED 提交审核")
        void shouldThrowOnIllegalTransitionFromPublished() {
            createRule("R1", "PUBLISHED");

            assertThatThrownBy(() -> service.submitForReview("R1", null, "owner"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("不允许提交审核");
        }

        @Test
        @DisplayName("10b. 审批记录不存在抛异常")
        void shouldThrowWhenApprovalRecordNotExists() {
            createRule("R1", "DRAFT");

            assertThatThrownBy(() -> service.approve("R1", "approver1", "通过"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("审批记录不存在");
        }

        @Test
        @DisplayName("提交审核时规则不存在抛异常")
        void shouldThrowWhenRuleNotExists() {
            assertThatThrownBy(() -> service.submitForReview("NON_EXIST", null, "owner"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("规则不存在");
        }

        @Test
        @DisplayName("审批流不存在抛异常")
        void shouldThrowWhenFlowNotExists() {
            createRule("R1", "DRAFT");

            assertThatThrownBy(() -> service.submitForReview("R1", "non-existent-flow", "owner"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("审批流不存在");
        }

        @Test
        @DisplayName("查询审批状态")
        void shouldReturnApprovalStatus() {
            createRule("R1", "DRAFT");
            service.submitForReview("R1", null, "owner");

            ApprovalRecord record = service.getApprovalStatus("R1");

            assertThat(record).isNotNull();
            assertThat(record.getRuleCode()).isEqualTo("R1");
            assertThat(record.getCurrentLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("查询审批状态 - 无记录返回 null")
        void shouldReturnNullWhenNoRecord() {
            assertThat(service.getApprovalStatus("R1")).isNull();
        }
    }

    // ==================== 会签场景 ====================

    @Nested
    @DisplayName("会签场景")
    class CountersignTest {

        @Test
        @DisplayName("6. 2 人会签：1 人通过不进入下一级")
        void shouldNotAdvanceWhenOnlyOneApproverApproved() {
            registerCountersignFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "countersign-1level", "owner");

            ApprovalRecord record = service.approve("R1", "U001", "通过");

            // 1 人通过，未达到 requiredCount=2，仍停留在级别 1
            assertThat(record.getCurrentLevel()).isEqualTo(1);
            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_PENDING);
            assertThat(record.getCurrentLevelApprovedApprovers()).contains("U001");
            // 单级会签流程，1 级即终态，状态仍为 REVIEW（单级兼容）
            assertThat(ruleStore.get("R1").getStatus()).isEqualTo(RuleStatus.REVIEW.name());
        }

        @Test
        @DisplayName("6b. 2 人会签：2 人都通过才进入下一级（发布）")
        void shouldAdvanceWhenAllApproversApproved() {
            registerCountersignFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "countersign-1level", "owner");

            service.approve("R1", "U001", "通过");
            ApprovalRecord record = service.approve("R1", "U002", "通过");

            // 2 人都通过，单级会签流程，直接发布
            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_APPROVED);
            assertThat(ruleStore.get("R1").getStatus()).isEqualTo(RuleStatus.PUBLISHED.name());
        }

        @Test
        @DisplayName("6c. 会签：同一人重复通过抛异常")
        void shouldThrowWhenSameApproverApprovesTwice() {
            registerCountersignFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "countersign-1level", "owner");
            service.approve("R1", "U001", "通过");

            assertThatThrownBy(() -> service.approve("R1", "U001", "再次通过"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已通过当前级别");
        }

        @Test
        @DisplayName("6d. 会签：非指定审批人抛异常")
        void shouldThrowWhenNonDesignatedApprover() {
            registerCountersignFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "countersign-1level", "owner");

            assertThatThrownBy(() -> service.approve("R1", "U999", "通过"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("不在指定审批人列表中");
        }
    }

    // ==================== 委托场景 ====================

    @Nested
    @DisplayName("委托场景")
    class DelegateTest {

        @Test
        @DisplayName("7. 委托后由被委托人审批")
        void shouldAllowDelegatedApproverToApprove() {
            registerRestrictedFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "restricted-1level", "owner");

            // U001 委托给 U003
            ApprovalRecord record = service.delegate("R1", "U001", "U003", "出差请代审");
            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_DELEGATED);

            // U003 作为被委托人审批通过
            record = service.approve("R1", "U003", "代审通过");

            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_APPROVED);
            assertThat(ruleStore.get("R1").getStatus()).isEqualTo(RuleStatus.PUBLISHED.name());
        }

        @Test
        @DisplayName("7b. 委托后非被委托人审批抛异常")
        void shouldThrowWhenNonDelegateApproves() {
            registerRestrictedFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "restricted-1level", "owner");
            service.delegate("R1", "U001", "U003", "出差请代审");

            // U001 不能再审批（已委托给 U003）
            assertThatThrownBy(() -> service.approve("R1", "U001", "通过"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("已委托");
        }

        @Test
        @DisplayName("7c. 不允许委托给自己")
        void shouldThrowWhenDelegateToSelf() {
            registerRestrictedFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "restricted-1level", "owner");

            assertThatThrownBy(() -> service.delegate("R1", "U001", "U001", "自己"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不允许委托给自己");
        }

        @Test
        @DisplayName("7d. 步骤不允许委托时抛异常")
        void shouldThrowWhenDelegateNotAllowed() {
            registerCountersignFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "countersign-1level", "owner");

            assertThatThrownBy(() -> service.delegate("R1", "U001", "U003", "代审"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("不允许委托");
        }
    }

    // ==================== 待审批列表查询 ====================

    @Nested
    @DisplayName("待审批列表查询")
    class PendingApprovalsTest {

        @Test
        @DisplayName("9. 查询待审批列表 - 指定审批人")
        void shouldListPendingApprovalsForDesignatedApprover() {
            registerRestrictedFlow();
            createRule("R1", "DRAFT");
            createRule("R2", "DRAFT");
            service.submitForReview("R1", "restricted-1level", "owner");
            service.submitForReview("R2", "restricted-1level", "owner");

            List<ApprovalRecord> pending = service.listPendingApprovals("U001");

            assertThat(pending).hasSize(2);
            assertThat(pending).extracting(ApprovalRecord::getRuleCode)
                    .containsExactlyInAnyOrder("R1", "R2");
        }

        @Test
        @DisplayName("9b. 查询待审批列表 - 非指定审批人返回空")
        void shouldReturnEmptyForNonDesignatedApprover() {
            registerRestrictedFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "restricted-1level", "owner");

            List<ApprovalRecord> pending = service.listPendingApprovals("U999");

            assertThat(pending).isEmpty();
        }

        @Test
        @DisplayName("9c. 查询待审批列表 - 已通过规则不出现")
        void shouldNotListApprovedRules() {
            registerRestrictedFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "restricted-1level", "owner");
            service.approve("R1", "U001", "通过");

            List<ApprovalRecord> pending = service.listPendingApprovals("U001");

            assertThat(pending).isEmpty();
        }
    }

    // ==================== 无权限审批 ====================

    @Nested
    @DisplayName("无权限审批")
    class PermissionTest {

        @Test
        @DisplayName("11. 无权限审批抛异常")
        void shouldThrowWhenNoPermission() {
            registerRestrictedFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "restricted-1level", "owner");

            assertThatThrownBy(() -> service.approve("R1", "U999", "通过"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("不在指定审批人列表中");
        }

        @Test
        @DisplayName("11b. 通过权限检查器拒绝")
        void shouldThrowWhenPermissionCheckerDenies() {
            createRule("R1", "DRAFT");
            ApprovalPermissionChecker checker = Mockito.mock(ApprovalPermissionChecker.class);
            when(checker.hasApprovePermission(anyString(), any(ApprovalStep.class))).thenReturn(false);
            service.setPermissionChecker(checker);

            // 默认审批流无 approvers，使用权限检查器
            service.submitForReview("R1", null, "owner");

            assertThatThrownBy(() -> service.approve("R1", "approver1", "通过"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("无审批权限");
        }

        @Test
        @DisplayName("11c. 通过权限检查器放行")
        void shouldApproveWhenPermissionCheckerAllows() {
            createRule("R1", "DRAFT");
            ApprovalPermissionChecker checker = Mockito.mock(ApprovalPermissionChecker.class);
            when(checker.hasApprovePermission(anyString(), any(ApprovalStep.class))).thenReturn(true);
            service.setPermissionChecker(checker);

            service.submitForReview("R1", null, "owner");
            ApprovalRecord record = service.approve("R1", "approver1", "通过");

            assertThat(record.getCurrentLevel()).isEqualTo(2);
        }
    }

    // ==================== 向后兼容 ====================

    @Nested
    @DisplayName("向后兼容")
    class BackwardCompatTest {

        @Test
        @DisplayName("12. 单级审批流使用 REVIEW 状态")
        void shouldUseReviewStatusForSingleLevelFlow() {
            registerSingleLevelFlow();
            createRule("R1", "DRAFT");

            service.submitForReview("R1", "single-1level", "owner");

            // 单级审批流使用 REVIEW 状态（向后兼容）
            assertThat(ruleStore.get("R1").getStatus()).isEqualTo(RuleStatus.REVIEW.name());
        }

        @Test
        @DisplayName("12b. 单级审批流审批通过直接 PUBLISHED")
        void shouldPublishDirectlyForSingleLevelFlow() {
            registerSingleLevelFlow();
            createRule("R1", "DRAFT");
            service.submitForReview("R1", "single-1level", "owner");

            ApprovalRecord record = service.approve("R1", "approver1", "通过");

            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_APPROVED);
            assertThat(ruleStore.get("R1").getStatus()).isEqualTo(RuleStatus.PUBLISHED.name());
        }

        @Test
        @DisplayName("12c. REVIEW 状态可转换到 REVIEW_L2（向后兼容）")
        void shouldAllowReviewToReviewL2Transition() {
            assertThat(RuleStatus.REVIEW.canTransitionTo(RuleStatus.REVIEW_L2)).isTrue();
            assertThat(RuleStatus.REVIEW.canTransitionTo(RuleStatus.PUBLISHED)).isTrue();
            assertThat(RuleStatus.REVIEW.canTransitionTo(RuleStatus.DRAFT)).isTrue();
        }

        @Test
        @DisplayName("12d. 现有 REVIEW → PUBLISHED 单级审批直通仍可用")
        void shouldKeepReviewToPublishedDirectTransition() {
            assertThat(RuleStatus.REVIEW.canTransitionTo(RuleStatus.PUBLISHED)).isTrue();
        }
    }

    // ==================== 状态机扩展 ====================

    @Nested
    @DisplayName("状态机扩展")
    class StatusMachineTest {

        @Test
        @DisplayName("DRAFT → REVIEW_L1 允许")
        void shouldAllowDraftToReviewL1() {
            assertThat(RuleStatus.DRAFT.canTransitionTo(RuleStatus.REVIEW_L1)).isTrue();
        }

        @Test
        @DisplayName("REVIEW_L1 → REVIEW_L2 允许")
        void shouldAllowReviewL1ToReviewL2() {
            assertThat(RuleStatus.REVIEW_L1.canTransitionTo(RuleStatus.REVIEW_L2)).isTrue();
        }

        @Test
        @DisplayName("REVIEW_L1 → DRAFT 允许（驳回）")
        void shouldAllowReviewL1ToDraft() {
            assertThat(RuleStatus.REVIEW_L1.canTransitionTo(RuleStatus.DRAFT)).isTrue();
        }

        @Test
        @DisplayName("REVIEW_L2 → REVIEW_FINAL 允许")
        void shouldAllowReviewL2ToReviewFinal() {
            assertThat(RuleStatus.REVIEW_L2.canTransitionTo(RuleStatus.REVIEW_FINAL)).isTrue();
        }

        @Test
        @DisplayName("REVIEW_L2 → REVIEW_L1 允许（驳回）")
        void shouldAllowReviewL2ToReviewL1() {
            assertThat(RuleStatus.REVIEW_L2.canTransitionTo(RuleStatus.REVIEW_L1)).isTrue();
        }

        @Test
        @DisplayName("REVIEW_FINAL → PUBLISHED 允许")
        void shouldAllowReviewFinalToPublished() {
            assertThat(RuleStatus.REVIEW_FINAL.canTransitionTo(RuleStatus.PUBLISHED)).isTrue();
        }

        @Test
        @DisplayName("REVIEW_FINAL → REVIEW_L2 允许（驳回）")
        void shouldAllowReviewFinalToReviewL2() {
            assertThat(RuleStatus.REVIEW_FINAL.canTransitionTo(RuleStatus.REVIEW_L2)).isTrue();
        }

        @Test
        @DisplayName("REVIEW_L1 → PUBLISHED 允许（1 级审批流直通发布）")
        void shouldAllowReviewL1ToPublished() {
            assertThat(RuleStatus.REVIEW_L1.canTransitionTo(RuleStatus.PUBLISHED)).isTrue();
        }

        @Test
        @DisplayName("REVIEW_L2 → PUBLISHED 允许（2 级审批流直通发布）")
        void shouldAllowReviewL2ToPublished() {
            assertThat(RuleStatus.REVIEW_L2.canTransitionTo(RuleStatus.PUBLISHED)).isTrue();
        }

        @Test
        @DisplayName("ARCHIVED 不可再变更")
        void shouldNotAllowAnyTransitionFromArchived() {
            assertThat(RuleStatus.ARCHIVED.canTransitionTo(RuleStatus.DRAFT)).isFalse();
            assertThat(RuleStatus.ARCHIVED.canTransitionTo(RuleStatus.PUBLISHED)).isFalse();
        }

        @Test
        @DisplayName("fromCode 可解析新增状态")
        void shouldParseNewStatusCodes() {
            assertThat(RuleStatus.fromCode("REVIEW_L1")).isEqualTo(RuleStatus.REVIEW_L1);
            assertThat(RuleStatus.fromCode("REVIEW_L2")).isEqualTo(RuleStatus.REVIEW_L2);
            assertThat(RuleStatus.fromCode("REVIEW_FINAL")).isEqualTo(RuleStatus.REVIEW_FINAL);
            assertThat(RuleStatus.fromCode("review_l1")).isEqualTo(RuleStatus.REVIEW_L1);
        }
    }

    // ==================== 审批流配置 ====================

    @Nested
    @DisplayName("审批流配置管理")
    class FlowConfigTest {

        @Test
        @DisplayName("默认 2 级审批流已注册")
        void shouldRegisterDefaultFlow() {
            List<ApprovalFlow> flows = service.listFlows();

            assertThat(flows).isNotEmpty();
            ApprovalFlow defaultFlow = service.getFlow(RuleApprovalService.DEFAULT_FLOW_CODE);
            assertThat(defaultFlow).isNotNull();
            assertThat(defaultFlow.getName()).contains("2 级");
            assertThat(defaultFlow.maxLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("注册自定义审批流")
        void shouldRegisterCustomFlow() {
            registerSingleLevelFlow();

            ApprovalFlow flow = service.getFlow("single-1level");
            assertThat(flow).isNotNull();
            assertThat(flow.maxLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("注册非法审批流抛异常")
        void shouldThrowWhenRegisterInvalidFlow() {
            assertThatThrownBy(() -> service.registerFlow(null))
                    .isInstanceOf(IllegalArgumentException.class);

            ApprovalFlow emptySteps = ApprovalFlow.builder()
                    .flowCode("empty")
                    .name("空步骤")
                    .steps(List.of())
                    .build();
            assertThatThrownBy(() -> service.registerFlow(emptySteps))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("steps 不能为空");
        }
    }
}
