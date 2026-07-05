package com.njydsz.pmis.workflow.engine.impl;

import com.njydsz.pmis.workflow.engine.FlowDefinitionCacheService;
import com.njydsz.pmis.workflow.engine.FlowVariableStrategy;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.enums.FlowInstanceStatus;
import com.njydsz.pmis.workflow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.mapper.FlowTaskMapper;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowJoinTokenService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DefaultFlowAdvancer 单元测试
 *
 * <p>P0-6：覆盖流程推进器 advance 方法的核心场景，包括驳回、普通节点推进、排他网关（CONDITION）
 * 互斥分支、包容网关（INCLUSIVE）多分支、并行网关（PARALLEL）join 聚合、SpEL 条件回退评估、
 * Redis 异常降级等。
 *
 * <p>P1：自流程定义元数据缓存接入后，节点/skip 查询统一走 {@link FlowDefinitionCacheService}，
 * 测试改为 mock 缓存服务而非底层 Mapper。
 *
 * <p>注意：{@code advance()} 方法仅计算并返回下一节点列表，不直接创建任务或更新实例状态。
 * 任务创建和实例状态更新由调用方（FlowInstanceServiceImpl）基于返回的节点列表完成。
 * 本测试类通过验证返回节点列表的正确性来断言推进结果。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>驳回 - 基于入边找到上一审批节点</li>
 *   <li>驳回 - 当前节点无入边时退回到开始节点</li>
 *   <li>通过 - 审批节点推进到下一审批节点</li>
 *   <li>通过 - 下一节点为 END 时返回结束节点</li>
 *   <li>排他网关 - 只走首条匹配的出边（互斥）</li>
 *   <li>排他网关 - 所有条件不匹配时取默认出边</li>
 *   <li>包容网关 - 多条出边同时匹配时都走</li>
 *   <li>包容网关 - 所有条件不匹配时取默认出边</li>
 *   <li>并行汇聚 - 最后一个分支到达时聚合通过</li>
 *   <li>并行汇聚 - 非最后一个分支到达时等待</li>
 *   <li>SpEL 回退 - 无 FlowRoutingService 时使用 VariableStrategy 评估条件</li>
 *   <li>join-token Redis 异常时降级到 countPendingByNode</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
class DefaultFlowAdvancerTest {

    @Mock
    private FlowDefinitionCacheService flowDefinitionCacheService;
    @Mock
    private FlowInstanceMapper instanceMapper;
    @Mock
    private FlowTaskService taskService;
    @Mock
    private FlowInstanceService instanceService;
    @Mock
    private FlowVariableStrategy variableStrategy;
    @Mock
    private FlowTaskMapper taskMapper;
    @Mock
    private FlowJoinTokenService joinTokenService;

    private DefaultFlowAdvancer advancer;

    private static final Long DEFINITION_ID = 200L;
    private static final Long INSTANCE_ID = 1001L;

    @BeforeEach
    void setUp() {
        // 构造器方式创建被测对象，FlowRoutingService 不注入（传 null），
        // 测试 SpEL 回退路径（evaluateSkipCondition 回退到 variableStrategy）
        advancer = new DefaultFlowAdvancer(
                flowDefinitionCacheService, instanceMapper,
                taskService, instanceService, variableStrategy,
                taskMapper, joinTokenService, null);
    }

    // ==================== A. reject（驳回）分支 ====================

    @Test
    @DisplayName("驳回 - 基于入边找到上一审批节点")
    void rejectShouldReturnPreviousApprovalNode() {
        // 当前节点 node2（APPROVAL），入边 skipName 指向上一节点名称
        FlowNodeDO currentNode = buildNode("node2", "主管审批", FlowNodeType.APPROVAL);
        FlowNodeDO prevNode = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);
        FlowInstanceDO instance = buildInstance();

        FlowSkipDO incomingSkip = new FlowSkipDO();
        incomingSkip.setSkipName("部门审批");

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node2")).thenReturn(currentNode);
        when(flowDefinitionCacheService.getSkipsByNextNode(DEFINITION_ID, "node2")).thenReturn(List.of(incomingSkip));
        when(flowDefinitionCacheService.getAllNodes(DEFINITION_ID)).thenReturn(List.of(currentNode, prevNode));
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node1")).thenReturn(prevNode);

        List<FlowNodeDO> result = advancer.advance(instance, "node2", "REJECT", null, Collections.emptyMap());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("node1");
        assertThat(result.get(0).getNodeName()).isEqualTo("部门审批");
    }

    @Test
    @DisplayName("驳回 - 当前节点无入边时退回到开始节点")
    void rejectShouldReturnStartNodeWhenNoIncoming() {
        FlowNodeDO currentNode = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);
        FlowNodeDO startNode = buildNode("node_start", "发起", FlowNodeType.START);
        FlowInstanceDO instance = buildInstance();

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node1")).thenReturn(currentNode);
        when(flowDefinitionCacheService.getSkipsByNextNode(DEFINITION_ID, "node1")).thenReturn(Collections.emptyList());
        when(flowDefinitionCacheService.getStartNode(DEFINITION_ID)).thenReturn(startNode);
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node_start")).thenReturn(startNode);

        List<FlowNodeDO> result = advancer.advance(instance, "node1", "REJECT", null, Collections.emptyMap());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("node_start");
    }

    // ==================== B. pass - 普通节点（APPROVAL/SERVICE/START/END）====================

    @Test
    @DisplayName("通过 - 审批节点推进到下一审批节点")
    void passShouldAdvanceToNextApprovalNode() {
        FlowNodeDO currentNode = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);
        FlowNodeDO nextNode = buildNode("node2", "主管审批", FlowNodeType.APPROVAL);
        FlowInstanceDO instance = buildInstance();
        FlowSkipDO skip = buildSkip("node2", null);

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node1")).thenReturn(currentNode);
        when(flowDefinitionCacheService.getSkipsByNodeCode(DEFINITION_ID, "node1")).thenReturn(List.of(skip));
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node2")).thenReturn(nextNode);

        List<FlowNodeDO> result = advancer.advance(instance, "node1", "PASS", null, Collections.emptyMap());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("node2");
        assertThat(result.get(0).getNodeType()).isEqualTo(FlowNodeType.APPROVAL.getCode());
    }

    @Test
    @DisplayName("通过 - 下一节点为 END 时返回结束节点")
    void passShouldReturnEndNode() {
        FlowNodeDO currentNode = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);
        FlowNodeDO endNode = buildNode("node_end", "结束", FlowNodeType.END);
        FlowInstanceDO instance = buildInstance();
        FlowSkipDO skip = buildSkip("node_end", null);

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node1")).thenReturn(currentNode);
        when(flowDefinitionCacheService.getSkipsByNodeCode(DEFINITION_ID, "node1")).thenReturn(List.of(skip));
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node_end")).thenReturn(endNode);

        List<FlowNodeDO> result = advancer.advance(instance, "node1", "PASS", null, Collections.emptyMap());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeType()).isEqualTo(FlowNodeType.END.getCode());
    }

    // ==================== C. pass - CONDITION（排他网关）====================

    @Test
    @DisplayName("排他网关 - 只走首条匹配的出边（互斥）")
    void conditionGatewayShouldTakeFirstMatchOnly() {
        FlowNodeDO gateway = buildNode("gw1", "金额判断", FlowNodeType.CONDITION);
        FlowNodeDO lowNode = buildNode("node_low", "普通审批", FlowNodeType.APPROVAL);
        FlowInstanceDO instance = buildInstance();
        Map<String, Object> variables = Map.of("amount", 3000);

        FlowSkipDO skipHigh = buildSkip("node_high", "${amount > 5000}");
        FlowSkipDO skipLow = buildSkip("node_low", "${amount <= 5000}");

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "gw1")).thenReturn(gateway);
        when(flowDefinitionCacheService.getSkipsByNodeCode(DEFINITION_ID, "gw1")).thenReturn(List.of(skipHigh, skipLow));
        when(variableStrategy.evaluate("${amount > 5000}", variables)).thenReturn(false);
        when(variableStrategy.evaluate("${amount <= 5000}", variables)).thenReturn(true);
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node_low")).thenReturn(lowNode);

        List<FlowNodeDO> result = advancer.advance(instance, "gw1", "PASS", null, variables);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("node_low");
        // 排他网关互斥：首条匹配后 break，不评估后续出边对应节点
        verify(flowDefinitionCacheService, never()).getNodeByCode(DEFINITION_ID, "node_high");
    }

    @Test
    @DisplayName("排他网关 - 所有条件不匹配时取默认出边（第一条）")
    void conditionGatewayShouldTakeDefaultWhenNoMatch() {
        FlowNodeDO gateway = buildNode("gw1", "金额判断", FlowNodeType.CONDITION);
        FlowNodeDO defaultNode = buildNode("node_default", "默认审批", FlowNodeType.APPROVAL);
        FlowInstanceDO instance = buildInstance();
        Map<String, Object> variables = Map.of("amount", 1000);

        FlowSkipDO skip1 = buildSkip("node_default", "${amount > 10000}");
        FlowSkipDO skip2 = buildSkip("node_special", "${amount > 5000}");

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "gw1")).thenReturn(gateway);
        when(flowDefinitionCacheService.getSkipsByNodeCode(DEFINITION_ID, "gw1")).thenReturn(List.of(skip1, skip2));
        when(variableStrategy.evaluate("${amount > 10000}", variables)).thenReturn(false);
        when(variableStrategy.evaluate("${amount > 5000}", variables)).thenReturn(false);
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node_default")).thenReturn(defaultNode);

        List<FlowNodeDO> result = advancer.advance(instance, "gw1", "PASS", null, variables);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("node_default");
    }

    // ==================== D. pass - INCLUSIVE（包容网关）====================

    @Test
    @DisplayName("包容网关 - 多条出边同时匹配时都走")
    void inclusiveGatewayShouldTakeAllMatchedSkips() {
        FlowNodeDO gateway = buildNode("gw_inc", "包容判断", FlowNodeType.INCLUSIVE);
        FlowNodeDO nodeA = buildNode("node_a", "分支A", FlowNodeType.APPROVAL);
        FlowNodeDO nodeB = buildNode("node_b", "分支B", FlowNodeType.APPROVAL);
        FlowInstanceDO instance = buildInstance();
        Map<String, Object> variables = Map.of("amount", 5000, "type", "VIP");

        FlowSkipDO skip1 = buildSkip("node_a", "${amount > 1000}");
        FlowSkipDO skip2 = buildSkip("node_b", "${type == 'VIP'}");
        FlowSkipDO skip3 = buildSkip("node_c", "${amount > 10000}");

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "gw_inc")).thenReturn(gateway);
        when(flowDefinitionCacheService.getSkipsByNodeCode(DEFINITION_ID, "gw_inc")).thenReturn(List.of(skip1, skip2, skip3));
        when(variableStrategy.evaluate("${amount > 1000}", variables)).thenReturn(true);
        when(variableStrategy.evaluate("${type == 'VIP'}", variables)).thenReturn(true);
        when(variableStrategy.evaluate("${amount > 10000}", variables)).thenReturn(false);
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node_a")).thenReturn(nodeA);
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node_b")).thenReturn(nodeB);

        List<FlowNodeDO> result = advancer.advance(instance, "gw_inc", "PASS", null, variables);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(FlowNodeDO::getNodeCode)
                .containsExactlyInAnyOrder("node_a", "node_b");
    }

    @Test
    @DisplayName("包容网关 - 所有条件不匹配时取默认出边")
    void inclusiveGatewayShouldTakeDefaultWhenNoMatch() {
        FlowNodeDO gateway = buildNode("gw_inc", "包容判断", FlowNodeType.INCLUSIVE);
        FlowNodeDO defaultNode = buildNode("node_default", "默认分支", FlowNodeType.APPROVAL);
        FlowInstanceDO instance = buildInstance();
        Map<String, Object> variables = Map.of("amount", 500);

        FlowSkipDO skip1 = buildSkip("node_default", "${amount > 1000}");
        FlowSkipDO skip2 = buildSkip("node_special", "${amount > 5000}");

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "gw_inc")).thenReturn(gateway);
        when(flowDefinitionCacheService.getSkipsByNodeCode(DEFINITION_ID, "gw_inc")).thenReturn(List.of(skip1, skip2));
        when(variableStrategy.evaluate("${amount > 1000}", variables)).thenReturn(false);
        when(variableStrategy.evaluate("${amount > 5000}", variables)).thenReturn(false);
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node_default")).thenReturn(defaultNode);

        List<FlowNodeDO> result = advancer.advance(instance, "gw_inc", "PASS", null, variables);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("node_default");
    }

    // ==================== E. pass - PARALLEL（并行网关）汇聚 ====================

    @Test
    @DisplayName("并行汇聚 - 最后一个分支到达时聚合通过")
    void parallelJoinShouldPassWhenAllBranchesArrived() {
        FlowNodeDO branchNode = buildNode("branch1", "分支1", FlowNodeType.APPROVAL);
        FlowNodeDO joinNode = buildNode("join1", "汇聚", FlowNodeType.PARALLEL);
        FlowInstanceDO instance = buildInstance();
        FlowSkipDO skip = buildSkip("join1", null);

        // join 节点有 2 条入边（hasMultipleIncoming + incomingCount 都查 getSkipsByNextNode）
        FlowSkipDO incoming1 = new FlowSkipDO();
        FlowSkipDO incoming2 = new FlowSkipDO();

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "branch1")).thenReturn(branchNode);
        when(flowDefinitionCacheService.getSkipsByNodeCode(DEFINITION_ID, "branch1")).thenReturn(List.of(skip));
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "join1")).thenReturn(joinNode);
        when(flowDefinitionCacheService.getSkipsByNextNode(DEFINITION_ID, "join1")).thenReturn(List.of(incoming1, incoming2));
        when(joinTokenService.isInitialized(INSTANCE_ID, "join1")).thenReturn(true);
        when(joinTokenService.arriveToken(INSTANCE_ID, "join1")).thenReturn(true);

        List<FlowNodeDO> result = advancer.advance(instance, "branch1", "PASS", null, Collections.emptyMap());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("join1");
        // 全部分支到达后清理令牌
        verify(joinTokenService).clearTokens(INSTANCE_ID, "join1");
    }

    @Test
    @DisplayName("并行汇聚 - 非最后一个分支到达时等待")
    void parallelJoinShouldWaitWhenNotAllArrived() {
        FlowNodeDO branchNode = buildNode("branch1", "分支1", FlowNodeType.APPROVAL);
        FlowNodeDO joinNode = buildNode("join1", "汇聚", FlowNodeType.PARALLEL);
        FlowInstanceDO instance = buildInstance();
        FlowSkipDO skip = buildSkip("join1", null);

        FlowSkipDO incoming1 = new FlowSkipDO();
        FlowSkipDO incoming2 = new FlowSkipDO();

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "branch1")).thenReturn(branchNode);
        when(flowDefinitionCacheService.getSkipsByNodeCode(DEFINITION_ID, "branch1")).thenReturn(List.of(skip));
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "join1")).thenReturn(joinNode);
        when(flowDefinitionCacheService.getSkipsByNextNode(DEFINITION_ID, "join1")).thenReturn(List.of(incoming1, incoming2));
        // 首次到达：未初始化 → 触发 initTokens
        when(joinTokenService.isInitialized(INSTANCE_ID, "join1")).thenReturn(false);
        when(joinTokenService.arriveToken(INSTANCE_ID, "join1")).thenReturn(false);

        List<FlowNodeDO> result = advancer.advance(instance, "branch1", "PASS", null, Collections.emptyMap());

        // 等待其他分支，不返回节点
        assertThat(result).isEmpty();
        // 验证 initTokens 使用正确的入边数（branchCount=2）
        ArgumentCaptor<Integer> branchCountCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(joinTokenService).initTokens(eq(INSTANCE_ID), eq("join1"), branchCountCaptor.capture());
        assertThat(branchCountCaptor.getValue()).isEqualTo(2);
        // 未聚合通过，不应清理令牌
        verify(joinTokenService, never()).clearTokens(anyLong(), anyString());
    }

    // ==================== E2. GAP-P0-2: 多节点同退 ====================

    @Test
    @DisplayName("GAP-P0-2 多节点同退 - 返回全部指定目标节点")
    void rejectMultiShouldReturnAllTargetNodes() {
        FlowInstanceDO instance = buildInstance();
        FlowNodeDO target1 = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);
        FlowNodeDO target2 = buildNode("node3", "财务审批", FlowNodeType.APPROVAL);

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node1")).thenReturn(target1);
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node3")).thenReturn(target2);

        List<FlowNodeDO> result = advancer.advance(instance, "node5", "REJECT",
                List.of("node1", "node3"), Collections.emptyMap());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getNodeCode()).isEqualTo("node1");
        assertThat(result.get(1).getNodeCode()).isEqualTo("node3");
    }

    @Test
    @DisplayName("GAP-P0-2 多节点同退 - 单元素列表降级到单节点退回")
    void rejectMultiWithSingleElementShouldFallbackToSingle() {
        FlowInstanceDO instance = buildInstance();
        FlowNodeDO currentNode = buildNode("node2", "主管审批", FlowNodeType.APPROVAL);
        FlowNodeDO target = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node2")).thenReturn(currentNode);
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node1")).thenReturn(target);

        List<FlowNodeDO> result = advancer.advance(instance, "node2", "REJECT",
                List.of("node1"), Collections.emptyMap());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("node1");
    }

    @Test
    @DisplayName("GAP-P0-2 多节点同退 - 空列表降级到默认退回（前驱节点）")
    void rejectMultiWithEmptyListShouldFallbackToDefault() {
        FlowNodeDO currentNode = buildNode("node2", "主管审批", FlowNodeType.APPROVAL);
        FlowNodeDO prevNode = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);
        FlowInstanceDO instance = buildInstance();

        FlowSkipDO incomingSkip = new FlowSkipDO();
        incomingSkip.setSkipName("部门审批");

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node2")).thenReturn(currentNode);
        when(flowDefinitionCacheService.getSkipsByNextNode(DEFINITION_ID, "node2")).thenReturn(List.of(incomingSkip));
        when(flowDefinitionCacheService.getAllNodes(DEFINITION_ID)).thenReturn(List.of(currentNode, prevNode));
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node1")).thenReturn(prevNode);

        List<FlowNodeDO> result = advancer.advance(instance, "node2", "REJECT",
                Collections.emptyList(), Collections.emptyMap());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("node1");
    }

    @Test
    @DisplayName("GAP-P0-2 多节点同退 - 去重：重复 nodeCode 只保留一个")
    void rejectMultiShouldDeduplicateNodeCodes() {
        FlowInstanceDO instance = buildInstance();
        FlowNodeDO target1 = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node1")).thenReturn(target1);

        List<FlowNodeDO> result = advancer.advance(instance, "node5", "REJECT",
                List.of("node1", "node1", "node1"), Collections.emptyMap());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("node1");
    }

    @Test
    @DisplayName("GAP-P0-2 多节点同退 - PASS 语义不触发多节点逻辑")
    void rejectMultiShouldNotTriggerOnPass() {
        FlowNodeDO currentNode = buildNode("node1", "部门审批", FlowNodeType.APPROVAL);
        FlowNodeDO nextNode = buildNode("node2", "主管审批", FlowNodeType.APPROVAL);
        FlowInstanceDO instance = buildInstance();
        FlowSkipDO skip = buildSkip("node2", null);

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node1")).thenReturn(currentNode);
        when(flowDefinitionCacheService.getSkipsByNodeCode(DEFINITION_ID, "node1")).thenReturn(List.of(skip));
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "node2")).thenReturn(nextNode);

        // skipType=PASS 时即便传了多节点列表，也应走 PASS 推进
        List<FlowNodeDO> result = advancer.advance(instance, "node1", "PASS",
                List.of("node1", "node2"), Collections.emptyMap());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("node2");
    }

    // ==================== F. 条件表达式评估 ====================

    @Test
    @DisplayName("SpEL 回退 - 无 FlowRoutingService 时使用 VariableStrategy 评估条件")
    void evaluateSkipConditionShouldFallbackToVariableStrategy() {
        Map<String, Object> variables = Map.of("amount", 6000);

        // routingService 为 null（构造器未注入），回退到 variableStrategy
        when(variableStrategy.evaluate("${amount > 5000}", variables)).thenReturn(true);

        boolean result = advancer.evaluateSkipCondition("${amount > 5000}", variables);

        assertThat(result).isTrue();
        // 验证条件表达式被传递给 variableStrategy
        ArgumentCaptor<String> condCaptor = ArgumentCaptor.forClass(String.class);
        verify(variableStrategy).evaluate(condCaptor.capture(), eq(variables));
        assertThat(condCaptor.getValue()).isEqualTo("${amount > 5000}");
    }

    // ==================== G. Redis 降级 ====================

    @Test
    @DisplayName("join-token Redis 异常时降级到 countPendingByNode")
    void parallelJoinShouldDegradeWhenRedisThrows() {
        FlowNodeDO branchNode = buildNode("branch1", "分支1", FlowNodeType.APPROVAL);
        FlowNodeDO joinNode = buildNode("join1", "汇聚", FlowNodeType.PARALLEL);
        FlowInstanceDO instance = buildInstance();
        FlowSkipDO skip = buildSkip("join1", null);

        FlowSkipDO incoming1 = new FlowSkipDO();
        FlowSkipDO incoming2 = new FlowSkipDO();

        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "branch1")).thenReturn(branchNode);
        when(flowDefinitionCacheService.getSkipsByNodeCode(DEFINITION_ID, "branch1")).thenReturn(List.of(skip));
        when(flowDefinitionCacheService.getNodeByCode(DEFINITION_ID, "join1")).thenReturn(joinNode);
        when(flowDefinitionCacheService.getSkipsByNextNode(DEFINITION_ID, "join1")).thenReturn(List.of(incoming1, incoming2));
        // Redis 异常
        when(joinTokenService.isInitialized(INSTANCE_ID, "join1"))
                .thenThrow(new RuntimeException("Redis connection refused"));
        // 降级查询：无未完成任务 → 可推进
        when(taskMapper.countPendingByNode(INSTANCE_ID, "join1")).thenReturn(0);

        List<FlowNodeDO> result = advancer.advance(instance, "branch1", "PASS", null, Collections.emptyMap());

        // 降级后 pending=0 → 聚合通过
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNodeCode()).isEqualTo("join1");
        verify(taskMapper).countPendingByNode(INSTANCE_ID, "join1");
        // 异常路径不清理令牌
        verify(joinTokenService, never()).clearTokens(anyLong(), anyString());
    }

    // ==================== 辅助方法 ====================

    private FlowInstanceDO buildInstance() {
        FlowInstanceDO instance = new FlowInstanceDO();
        instance.setId(INSTANCE_ID);
        instance.setDefinitionId(DEFINITION_ID);
        instance.setFlowCode("LEAVE");
        instance.setFlowStatus(FlowInstanceStatus.RUNNING.name());
        return instance;
    }

    private FlowNodeDO buildNode(String code, String name, FlowNodeType type) {
        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode(code);
        node.setNodeName(name);
        node.setNodeType(type.getCode());
        node.setDefinitionId(DEFINITION_ID);
        return node;
    }

    private FlowSkipDO buildSkip(String nextNodeCode, String condition) {
        FlowSkipDO skip = new FlowSkipDO();
        skip.setNextNodeCode(nextNodeCode);
        skip.setSkipCondition(condition);
        skip.setSkipType("PASS");
        skip.setDefinitionId(DEFINITION_ID);
        return skip;
    }
}
