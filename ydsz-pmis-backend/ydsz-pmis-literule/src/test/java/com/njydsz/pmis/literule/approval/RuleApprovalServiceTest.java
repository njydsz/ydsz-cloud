package com.njydsz.pmis.literule.approval;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RuleApprovalService} 单元测试。
 *
 * <p>覆盖多级审批流核心能力，包括提交审核、审批通过、审批驳回、委托、撤回、
 * 查询审批状态与待审批列表，含 SINGLE/COUNTERSIGN/SEQUENCE 三种审批类型。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则审批流服务测试")
@ExtendWith(MockitoExtension.class)
class RuleApprovalServiceTest {

    @Mock
    private RuleConfigProvider configProvider;

    private RuleApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = new RuleApprovalService(configProvider);
    }

    private RuleDefinition buildRule(String code, String status) {
        return RuleDefinition.builder()
                .code(code)
                .name("规则-" + code)
                .status(status)
                .build();
    }

    private ApprovalFlow buildCustomFlow(String flowCode, ApprovalType type, List<String> approvers,
                                          int requiredCount, int levels, boolean allowDelegate) {
        return ApprovalFlow.builder()
                .flowCode(flowCode)
                .name("自定义审批流-" + flowCode)
                .enabled(true)
                .steps(List.of(ApprovalStep.builder()
                        .level(1)
                        .name("一级审核")
                        .type(type)
                        .approvers(approvers)
                        .requiredCount(requiredCount)
                        .allowDelegate(allowDelegate)
                        .build()))
                .build();
    }

    // ==================== 审批流注册与查询 ====================

    @Nested
    @DisplayName("审批流注册与查询")
    class FlowManagementTest {

        @Test
        @DisplayName("正常场景：构造时注册默认 2 级审批流")
        void shouldRegisterDefaultFlowOnConstruction() {
            ApprovalFlow flow = approvalService.getFlow(RuleApprovalService.DEFAULT_FLOW_CODE);

            assertThat(flow).isNotNull();
            assertThat(flow.getFlowCode()).isEqualTo(RuleApprovalService.DEFAULT_FLOW_CODE);
            assertThat(flow.maxLevel()).isEqualTo(2);
        }

        @Test
        @DisplayName("正常场景：listFlows 返回全部已注册审批流")
        void shouldListAllFlows() {
            List<ApprovalFlow> flows = approvalService.listFlows();

            assertThat(flows).hasSize(1);
            assertThat(flows.get(0).getFlowCode()).isEqualTo(RuleApprovalService.DEFAULT_FLOW_CODE);
        }

        @Test
        @DisplayName("异常场景：registerFlow 传入 null flowCode 抛异常")
        void shouldThrowWhenFlowCodeNull() {
            ApprovalFlow flow = ApprovalFlow.builder()
                    .flowCode(null)
                    .steps(List.of(ApprovalStep.builder().level(1).type(ApprovalType.SINGLE).build()))
                    .build();

            assertThatThrownBy(() -> approvalService.registerFlow(flow))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("flowCode");
        }

        @Test
        @DisplayName("异常场景：registerFlow 传入空 steps 抛异常")
        void shouldThrowWhenStepsEmpty() {
            ApprovalFlow flow = ApprovalFlow.builder()
                    .flowCode("empty-flow")
                    .steps(List.of())
                    .build();

            assertThatThrownBy(() -> approvalService.registerFlow(flow))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("steps");
        }

        @Test
        @DisplayName("正常场景：registerFlow 注册自定义审批流")
        void shouldRegisterCustomFlow() {
            ApprovalFlow flow = buildCustomFlow("custom-1", ApprovalType.SINGLE,
                    List.of("u1"), 0, 1, true);

            approvalService.registerFlow(flow);

            assertThat(approvalService.getFlow("custom-1")).isNotNull();
            assertThat(approvalService.listFlows()).hasSize(2);
        }

        @Test
        @DisplayName("正常场景：getFlow 查询不存在的 flowCode 返回 null")
        void shouldReturnNullWhenFlowNotExist() {
            assertThat(approvalService.getFlow("not-exist")).isNull();
        }
    }

    // ==================== 提交审核 ====================

    @Nested
    @DisplayName("提交审核：submitForReview")
    class SubmitForReviewTest {

        @Test
        @DisplayName("异常场景：ruleCode 为空抛异常")
        void shouldThrowWhenRuleCodeBlank() {
            assertThatThrownBy(() -> approvalService.submitForReview("  ", null, "operator"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ruleCode");
        }

        @Test
        @DisplayName("异常场景：operator 为空抛异常")
        void shouldThrowWhenOperatorBlank() {
            assertThatThrownBy(() -> approvalService.submitForReview("R001", null, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("operator");
        }

        @Test
        @DisplayName("异常场景：规则不存在抛异常")
        void shouldThrowWhenRuleNotFound() {
            when(configProvider.findByCode("R001")).thenReturn(null);

            assertThatThrownBy(() -> approvalService.submitForReview("R001", null, "operator"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("规则不存在");
        }

        @Test
        @DisplayName("异常场景：规则状态非 DRAFT 抛异常")
        void shouldThrowWhenStatusNotDraft() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "PUBLISHED"));

            assertThatThrownBy(() -> approvalService.submitForReview("R001", null, "operator"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("不允许提交审核");
        }

        @Test
        @DisplayName("正常场景：使用默认 2 级审批流提交，规则状态变为 REVIEW_L1")
        void shouldSubmitWithDefaultFlow() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));

            ApprovalRecord record = approvalService.submitForReview("R001", null, "operator");

            assertThat(record).isNotNull();
            assertThat(record.getRuleCode()).isEqualTo("R001");
            assertThat(record.getFlowCode()).isEqualTo(RuleApprovalService.DEFAULT_FLOW_CODE);
            assertThat(record.getCurrentLevel()).isEqualTo(1);
            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_PENDING);
            assertThat(record.getLogs()).hasSize(1);
            verify(configProvider).save(any(RuleDefinition.class), eq("operator"));
        }

        @Test
        @DisplayName("异常场景：flowCode 不存在抛异常")
        void shouldThrowWhenFlowNotExist() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));

            assertThatThrownBy(() -> approvalService.submitForReview("R001", "not-exist", "operator"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("审批流不存在");
        }
    }

    // ==================== 审批通过 ====================

    @Nested
    @DisplayName("审批通过：approve")
    class ApproveTest {

        @Test
        @DisplayName("异常场景：审批记录不存在抛异常")
        void shouldThrowWhenRecordNotExist() {
            assertThatThrownBy(() -> approvalService.approve("R001", "u1", "通过"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("审批记录不存在");
        }

        @Test
        @DisplayName("正常场景：默认 2 级审批流第一级通过后进入第二级")
        void shouldAdvanceToNextLevelWhenFirstLevelApproved() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");
            // 模拟 configProvider.save 更新规则状态
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "REVIEW_L1"));

            ApprovalRecord record = approvalService.approve("R001", "u1", "通过");

            assertThat(record.getCurrentLevel()).isEqualTo(2);
            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_PENDING);
        }

        @Test
        @DisplayName("正常场景：默认 2 级审批流全部通过后规则发布")
        void shouldPublishWhenAllLevelsApproved() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "REVIEW_L1"));
            approvalService.approve("R001", "u1", "通过一级");
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "REVIEW_L2"));

            ApprovalRecord record = approvalService.approve("R001", "u2", "通过二级");

            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_APPROVED);
            assertThat(record.getCurrentLevel()).isEqualTo(2);
        }
    }

    // ==================== 审批驳回 ====================

    @Nested
    @DisplayName("审批驳回：reject")
    class RejectTest {

        @Test
        @DisplayName("异常场景：reason 为空抛异常")
        void shouldThrowWhenReasonBlank() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");

            assertThatThrownBy(() -> approvalService.reject("R001", "u1", "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("reason");
        }

        @Test
        @DisplayName("正常场景：一级驳回回退到 DRAFT")
        void shouldRejectToDraftWhenFirstLevel() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "REVIEW_L1"));

            ApprovalRecord record = approvalService.reject("R001", "u1", "不通过");

            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_CANCELLED);
            assertThat(record.getCurrentLevel()).isEqualTo(1);
        }

        @Test
        @DisplayName("正常场景：二级驳回回退到第一级")
        void shouldRejectToPreviousLevel() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "REVIEW_L1"));
            approvalService.approve("R001", "u1", "通过一级");
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "REVIEW_L2"));

            ApprovalRecord record = approvalService.reject("R001", "u2", "驳回二级");

            assertThat(record.getCurrentLevel()).isEqualTo(1);
            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_PENDING);
        }
    }

    // ==================== 委托审批 ====================

    @Nested
    @DisplayName("委托审批：delegate")
    class DelegateTest {

        @Test
        @DisplayName("异常场景：审批记录不存在抛异常")
        void shouldThrowWhenRecordNotExist() {
            assertThatThrownBy(() -> approvalService.delegate("R001", "u1", "u2", "委托"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("审批记录不存在");
        }

        @Test
        @DisplayName("异常场景：委托给自己抛异常")
        void shouldThrowWhenDelegateToSelf() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");

            assertThatThrownBy(() -> approvalService.delegate("R001", "u1", "u1", "委托"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("委托给自己");
        }

        @Test
        @DisplayName("正常场景：委托后状态变为 DELEGATED")
        void shouldDelegateSuccessfully() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");

            ApprovalRecord record = approvalService.delegate("R001", "submitter", "u2", "出差委托");

            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_DELEGATED);
            assertThat(record.getLogs()).anyMatch(log ->
                    ApprovalLog.ACTION_DELEGATE.equals(log.getAction())
                            && "u2".equals(log.getDelegatedTo()));
        }
    }

    // ==================== 撤回审核 ====================

    @Nested
    @DisplayName("撤回审核：cancelReview")
    class CancelReviewTest {

        @Test
        @DisplayName("异常场景：审批记录不存在抛异常")
        void shouldThrowWhenRecordNotExist() {
            assertThatThrownBy(() -> approvalService.cancelReview("R001", "operator"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("审批记录不存在");
        }

        @Test
        @DisplayName("正常场景：PENDING 状态可撤回")
        void shouldCancelWhenPending() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "REVIEW_L1"));

            ApprovalRecord record = approvalService.cancelReview("R001", "submitter");

            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_CANCELLED);
        }

        @Test
        @DisplayName("异常场景：APPROVED 状态不可撤回抛异常")
        void shouldThrowWhenCancelApprovedRecord() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "REVIEW_L1"));
            approvalService.approve("R001", "u1", "通过一级");
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "REVIEW_L2"));
            approvalService.approve("R001", "u2", "通过二级");

            assertThatThrownBy(() -> approvalService.cancelReview("R001", "submitter"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("不允许撤回");
        }
    }

    // ==================== 查询方法 ====================

    @Nested
    @DisplayName("查询方法")
    class QueryTest {

        @Test
        @DisplayName("正常场景：getApprovalStatus 返回审批记录")
        void shouldReturnApprovalStatus() {
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");

            ApprovalRecord record = approvalService.getApprovalStatus("R001");

            assertThat(record).isNotNull();
            assertThat(record.getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("正常场景：getApprovalStatus 查询不存在的规则返回 null")
        void shouldReturnNullWhenApprovalStatusNotExist() {
            assertThat(approvalService.getApprovalStatus("R999")).isNull();
        }

        @Test
        @DisplayName("异常场景：listPendingApprovals approver 为空抛异常")
        void shouldThrowWhenListPendingApprovalsBlank() {
            assertThatThrownBy(() -> approvalService.listPendingApprovals("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("approver");
        }

        @Test
        @DisplayName("正常场景：listPendingApprovals 返回空列表")
        void shouldReturnEmptyPendingApprovals() {
            List<ApprovalRecord> records = approvalService.listPendingApprovals("u1");

            assertThat(records).isEmpty();
        }

        @Test
        @DisplayName("正常场景：snapshotRecords 返回不可修改的记录映射")
        void shouldReturnUnmodifiableSnapshot() {
            java.util.Map<String, ApprovalRecord> snapshot = approvalService.snapshotRecords();

            assertThatThrownBy(() -> snapshot.put("R001", null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ==================== 持久化仓库 ====================

    @Nested
    @DisplayName("持久化仓库 SPI")
    class RepositoryTest {

        @Test
        @DisplayName("正常场景：设置 recordRepository 后保存记录会委托给仓库")
        void shouldDelegateSaveToRepository() {
            ApprovalRecordRepository repository = mock(ApprovalRecordRepository.class);
            approvalService.setRecordRepository(repository);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));

            approvalService.submitForReview("R001", null, "submitter");

            verify(repository).save(any(ApprovalRecord.class));
        }

        @Test
        @DisplayName("异常场景：repository.save 抛异常时不影响主流程")
        void shouldNotFailWhenRepositorySaveThrowsException() {
            ApprovalRecordRepository repository = mock(ApprovalRecordRepository.class);
            approvalService.setRecordRepository(repository);
            doThrow(new RuntimeException("DB 异常")).when(repository).save(any());
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));

            ApprovalRecord record = approvalService.submitForReview("R001", null, "submitter");

            assertThat(record).isNotNull();
            assertThat(record.getRuleCode()).isEqualTo("R001");
        }
    }

    // ==================== 权限检查器 ====================

    @Nested
    @DisplayName("权限检查器 SPI")
    class PermissionCheckerTest {

        @Test
        @DisplayName("正常场景：设置 permissionChecker 后用于权限校验")
        void shouldUsePermissionCheckerWhenSet() {
            ApprovalPermissionChecker checker = mock(ApprovalPermissionChecker.class);
            approvalService.setPermissionChecker(checker);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");
            when(checker.hasApprovePermission(eq("u1"), any())).thenReturn(false);

            assertThatThrownBy(() -> approvalService.approve("R001", "u1", "通过"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("无审批权限");
        }

        @Test
        @DisplayName("正常场景：permissionChecker 返回 true 时放行")
        void shouldPassWhenPermissionCheckerReturnsTrue() {
            ApprovalPermissionChecker checker = mock(ApprovalPermissionChecker.class);
            approvalService.setPermissionChecker(checker);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", null, "submitter");
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "REVIEW_L1"));
            when(checker.hasApprovePermission(eq("u1"), any())).thenReturn(true);

            ApprovalRecord record = approvalService.approve("R001", "u1", "通过");

            assertThat(record.getCurrentLevel()).isEqualTo(2);
        }
    }

    // ==================== SEQUENCE 审批类型 ====================

    @Nested
    @DisplayName("顺序审批类型：SEQUENCE")
    class SequenceApprovalTest {

        @Test
        @DisplayName("正常场景：SEQUENCE 类型按顺序依次审批")
        void shouldApproveInSequence() {
            ApprovalFlow flow = buildCustomFlow("seq-flow", ApprovalType.SEQUENCE,
                    List.of("u1", "u2"), 0, 1, false);
            approvalService.registerFlow(flow);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));

            approvalService.submitForReview("R001", "seq-flow", "submitter");

            // 第一个审批人 u1 通过后级别未完成
            ApprovalRecord record = approvalService.approve("R001", "u1", "通过");
            assertThat(record.getCurrentLevelApprovedApprovers()).contains("u1");
        }

        @Test
        @DisplayName("异常场景：SEQUENCE 类型非下一个审批人操作抛异常")
        void shouldThrowWhenNotNextApproverInSequence() {
            ApprovalFlow flow = buildCustomFlow("seq-flow", ApprovalType.SEQUENCE,
                    List.of("u1", "u2"), 0, 1, false);
            approvalService.registerFlow(flow);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", "seq-flow", "submitter");

            assertThatThrownBy(() -> approvalService.approve("R001", "u2", "通过"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("顺序审批");
        }
    }

    // ==================== COUNTERSIGN 审批类型 ====================

    @Nested
    @DisplayName("会签审批类型：COUNTERSIGN")
    class CountersignApprovalTest {

        @Test
        @DisplayName("正常场景：COUNTERSIGN 类型需全部指定审批人通过")
        void shouldRequireAllApproversForCountersign() {
            ApprovalFlow flow = buildCustomFlow("cs-flow", ApprovalType.COUNTERSIGN,
                    List.of("u1", "u2"), 2, 1, false);
            approvalService.registerFlow(flow);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", "cs-flow", "submitter");

            // u1 通过后级别未完成（需 2 人通过）
            ApprovalRecord record = approvalService.approve("R001", "u1", "通过");
            assertThat(record.getCurrentLevelApprovedApprovers()).contains("u1");

            // u2 通过后级别完成
            record = approvalService.approve("R001", "u2", "通过");
            assertThat(record.getCurrentStatus()).isEqualTo(ApprovalRecord.STATUS_APPROVED);
        }

        @Test
        @DisplayName("异常场景：COUNTERSIGN 类型同一人重复通过抛异常")
        void shouldThrowWhenDuplicateApproveInCountersign() {
            ApprovalFlow flow = buildCustomFlow("cs-flow", ApprovalType.COUNTERSIGN,
                    List.of("u1", "u2"), 2, 1, false);
            approvalService.registerFlow(flow);
            when(configProvider.findByCode("R001"))
                    .thenReturn(buildRule("R001", "DRAFT"));
            approvalService.submitForReview("R001", "cs-flow", "submitter");
            approvalService.approve("R001", "u1", "通过");

            assertThatThrownBy(() -> approvalService.approve("R001", "u1", "再次通过"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已通过当前级别");
        }
    }
}
