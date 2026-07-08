package com.njydsz.pmis.cronjob.core.dag;

import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.cronjob.core.dispatch.TaskDispatcher;
import com.njydsz.pmis.cronjob.entity.JobDagDO;
import com.njydsz.pmis.cronjob.entity.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.entity.JobDagNodeInstanceDO;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.mapper.JobDagInstanceMapper;
import com.njydsz.pmis.cronjob.mapper.JobDagMapper;
import com.njydsz.pmis.cronjob.mapper.JobDagNodeInstanceMapper;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DagInstanceExecutor} 条件分支 / 循环 / 并行网关单元测试（P2-1）。
 *
 * <p>覆盖 P2-1 新增的三种节点类型：
 * <ul>
 *   <li>CONDITION：条件表达式解析（== 和 !=）、true 走对应边、false 跳过边</li>
 *   <li>LOOP：循环节点重复执行 loopCount 次</li>
 *   <li>PARALLEL_GATEWAY：并行网关并行执行所有下游分支</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DagInstanceExecutor 条件分支/循环/并行网关测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DagInstanceExecutorConditionTest {

    /** CONDITION DAG 定义：job-A → condition-B → job-C（条件为 true 时走 job-C） */
    private static final String CONDITION_DAG_JSON =
            "{\"nodes\":["
                    + "{\"jobKey\":\"job-A\",\"jobId\":\"1\",\"label\":\"A\",\"nodeType\":\"TASK\"},"
                    + "{\"jobKey\":\"cond-B\",\"jobId\":null,\"label\":\"条件\","
                    + "\"nodeType\":\"CONDITION\",\"conditionExpression\":\"${job-A.result=='success'}\"},"
                    + "{\"jobKey\":\"job-C\",\"jobId\":\"3\",\"label\":\"C\",\"nodeType\":\"TASK\"}"
                    + "],\"edges\":["
                    + "{\"from\":\"job-A\",\"to\":\"cond-B\"},"
                    + "{\"from\":\"cond-B\",\"to\":\"job-C\"}"
                    + "]}";

    /** LOOP DAG 定义：loop-A → job-B（循环节点，loopCount=3） */
    private static final String LOOP_DAG_JSON =
            "{\"nodes\":["
                    + "{\"jobKey\":\"loop-A\",\"jobId\":null,\"label\":\"循环\","
                    + "\"nodeType\":\"LOOP\",\"loopCount\":3},"
                    + "{\"jobKey\":\"job-B\",\"jobId\":\"2\",\"label\":\"B\",\"nodeType\":\"TASK\"}"
                    + "],\"edges\":["
                    + "{\"from\":\"loop-A\",\"to\":\"job-B\"}"
                    + "]}";

    /** PARALLEL_GATEWAY DAG 定义：gateway-A → job-B / job-C（并行网关，2 分支） */
    private static final String PARALLEL_DAG_JSON =
            "{\"nodes\":["
                    + "{\"jobKey\":\"gateway-A\",\"jobId\":null,\"label\":\"并行\","
                    + "\"nodeType\":\"PARALLEL_GATEWAY\",\"parallelBranches\":2},"
                    + "{\"jobKey\":\"job-B\",\"jobId\":\"2\",\"label\":\"B\",\"nodeType\":\"TASK\"},"
                    + "{\"jobKey\":\"job-C\",\"jobId\":\"3\",\"label\":\"C\",\"nodeType\":\"TASK\"}"
                    + "],\"edges\":["
                    + "{\"from\":\"gateway-A\",\"to\":\"job-B\"},"
                    + "{\"from\":\"gateway-A\",\"to\":\"job-C\"}"
                    + "]}";

    @Mock
    private JobDagInstanceMapper dagInstanceMapper;
    @Mock
    private JobDagNodeInstanceMapper dagNodeInstanceMapper;
    @Mock
    private JobDagMapper dagMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private JobLogMapper jobLogMapper;
    @Spy
    private final DagDefinitionCodec dagDefinitionCodec = new DagDefinitionCodec();
    @Mock
    private TaskDispatcher taskDispatcher;

    @InjectMocks
    private DagInstanceExecutor dagInstanceExecutor;

    // ==================== evaluateCondition 方法测试 ====================

    @Test
    @DisplayName("evaluateCondition: == 操作符，值匹配返回 true")
    void evaluateCondition_equalsMatch_returnTrue() {
        Map<String, Object> context = new HashMap<>();
        JSONObject nodeResult = new JSONObject();
        nodeResult.put("result", "success");
        context.put("nodeA", nodeResult);

        boolean result = dagInstanceExecutor.evaluateCondition(
                "${nodeA.result=='success'}", context);

        assertTrue(result);
    }

    @Test
    @DisplayName("evaluateCondition: == 操作符，值不匹配返回 false")
    void evaluateCondition_equalsNotMatch_returnFalse() {
        Map<String, Object> context = new HashMap<>();
        JSONObject nodeResult = new JSONObject();
        nodeResult.put("result", "failed");
        context.put("nodeA", nodeResult);

        boolean result = dagInstanceExecutor.evaluateCondition(
                "${nodeA.result=='success'}", context);

        assertFalse(result);
    }

    @Test
    @DisplayName("evaluateCondition: != 操作符，值不同返回 true")
    void evaluateCondition_notEqualsDifferent_returnTrue() {
        Map<String, Object> context = new HashMap<>();
        JSONObject nodeResult = new JSONObject();
        nodeResult.put("status", "FAILED");
        context.put("nodeA", nodeResult);

        boolean result = dagInstanceExecutor.evaluateCondition(
                "${nodeA.status!='SUCCESS'}", context);

        assertTrue(result);
    }

    @Test
    @DisplayName("evaluateCondition: != 操作符，值相同返回 false")
    void evaluateCondition_notEqualsSame_returnFalse() {
        Map<String, Object> context = new HashMap<>();
        JSONObject nodeResult = new JSONObject();
        nodeResult.put("status", "SUCCESS");
        context.put("nodeA", nodeResult);

        boolean result = dagInstanceExecutor.evaluateCondition(
                "${nodeA.status!='SUCCESS'}", context);

        assertFalse(result);
    }

    @Test
    @DisplayName("evaluateCondition: 双引号格式表达式正常解析")
    void evaluateCondition_doubleQuote_returnTrue() {
        Map<String, Object> context = new HashMap<>();
        JSONObject nodeResult = new JSONObject();
        nodeResult.put("result", "success");
        context.put("nodeA", nodeResult);

        boolean result = dagInstanceExecutor.evaluateCondition(
                "${nodeA.result==\"success\"}", context);

        assertTrue(result);
    }

    @Test
    @DisplayName("evaluateCondition: 表达式为空返回 false")
    void evaluateCondition_emptyExpression_returnFalse() {
        boolean result = dagInstanceExecutor.evaluateCondition(null, new HashMap<>());
        assertFalse(result);
    }

    @Test
    @DisplayName("evaluateCondition: 非法格式表达式返回 false")
    void evaluateCondition_invalidFormat_returnFalse() {
        Map<String, Object> context = new HashMap<>();
        boolean result = dagInstanceExecutor.evaluateCondition("invalid expression", context);
        assertFalse(result);
    }

    @Test
    @DisplayName("evaluateCondition: 上下文中无对应节点返回 false")
    void evaluateCondition_nodeNotFound_returnFalse() {
        Map<String, Object> context = new HashMap<>();
        boolean result = dagInstanceExecutor.evaluateCondition(
                "${missing.result=='success'}", context);
        assertFalse(result);
    }

    @Test
    @DisplayName("evaluateCondition: status 字段比较正常工作")
    void evaluateCondition_statusField_returnTrue() {
        Map<String, Object> context = new HashMap<>();
        JSONObject nodeResult = new JSONObject();
        nodeResult.put("status", "SUCCESS");
        context.put("nodeA", nodeResult);

        boolean result = dagInstanceExecutor.evaluateCondition(
                "${nodeA.status=='SUCCESS'}", context);
        assertTrue(result);
    }

    // ==================== CONDITION 节点测试 ====================

    @Test
    @DisplayName("CONDITION 节点: 条件为 true 时标记 SUCCESS 并触发后继")
    void conditionNode_true_markSuccessAndTriggerSuccessor() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", CONDITION_DAG_JSON, "FAIL_FAST");
        // cond-B 节点实例（CONDITION 控制节点，jobId 用 jobKey 兜底）
        JobDagNodeInstanceDO condNode = buildNodeInstance("node-cond", "instance-1", "dag-1",
                "cond-B", "cond-B", "PENDING", 0, 0, null);
        JobDagNodeInstanceDO condNodeSuccess = buildNodeInstance("node-cond", "instance-1", "dag-1",
                "cond-B", "cond-B", "SUCCESS", 0, 0, LocalDateTime.now());
        // job-A 节点实例（初始 RUNNING，完成后 SUCCESS，结果为 success）
        JobDagNodeInstanceDO jobARunning = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "RUNNING", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO jobASuccess = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "SUCCESS", 0, 0, LocalDateTime.now());
        jobASuccess.setResultJson("success");
        // job-C 节点实例
        JobDagNodeInstanceDO jobCNode = buildNodeInstance("node-C", "instance-1", "dag-1",
                "3", "job-C", "PENDING", 0, 0, null);

        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(List.of(instance));
        // job-A: 第一次 findRunningNodesByJobId 返回 RUNNING，第二次 areAllPredecessorsSuccessful 返回 SUCCESS
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "1"))
                .thenReturn(jobARunning, jobASuccess);
        // cond-B: 第一次 dispatchConditionNode 返回 PENDING，第二次 areAllPredecessorsSuccessful 返回 SUCCESS
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "cond-B"))
                .thenReturn(condNode, condNodeSuccess);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "3"))
                .thenReturn(jobCNode);
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(jobASuccess, condNode, jobCNode));
        when(jobMapper.selectById("3")).thenReturn(buildJob("3", "job-C", "NORMAL", 0));
        when(taskDispatcher.dispatch(any(), isNull(), eq("DEPENDENT"))).thenReturn("log-C");

        TaskCompletedEvent event = new TaskCompletedEvent("1", "job-A", true, "log-A");
        dagInstanceExecutor.onTaskCompleted(event);

        // 验证 CONDITION 节点被标记为 SUCCESS（条件为 true）
        verify(dagNodeInstanceMapper).markFinished(eq("node-cond"), eq("SUCCESS"),
                any(), anyLong(), any(), any(), any());
        // 验证后继 job-C 被派发
        verify(taskDispatcher).dispatch(any(), isNull(), eq("DEPENDENT"));
    }

    @Test
    @DisplayName("CONDITION 节点: 条件为 false 时标记 SKIPPED 不触发后继")
    void conditionNode_false_markSkippedAndNoSuccessor() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        // 条件表达式为 result=='success'，但 job-A 结果为 failed
        JobDagDO dag = buildDag("dag-1", "dag-key-1", CONDITION_DAG_JSON, "FAIL_FAST");
        JobDagNodeInstanceDO condNode = buildNodeInstance("node-cond", "instance-1", "dag-1",
                "cond-B", "cond-B", "PENDING", 0, 0, null);
        JobDagNodeInstanceDO jobARunning = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "RUNNING", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO jobASuccess = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "SUCCESS", 0, 0, LocalDateTime.now());
        jobASuccess.setResultJson("failed");
        JobDagNodeInstanceDO jobCNode = buildNodeInstance("node-C", "instance-1", "dag-1",
                "3", "job-C", "PENDING", 0, 0, null);

        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(List.of(instance));
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "1"))
                .thenReturn(jobARunning, jobASuccess);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "cond-B"))
                .thenReturn(condNode);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "3"))
                .thenReturn(jobCNode);
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(jobASuccess, condNode, jobCNode));

        TaskCompletedEvent event = new TaskCompletedEvent("1", "job-A", true, "log-A");
        dagInstanceExecutor.onTaskCompleted(event);

        // 验证 CONDITION 节点被标记为 SKIPPED（条件为 false）
        verify(dagNodeInstanceMapper).markSkipped("node-cond");
        // 验证后继 job-C 未被派发
        verify(taskDispatcher, never()).dispatch(any(), any(), any());
    }

    // ==================== LOOP 节点测试 ====================

    @Test
    @DisplayName("LOOP 节点: 重复派发循环体 loopCount 次")
    void loopNode_dispatchBodyLoopCountTimes() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", LOOP_DAG_JSON, "FAIL_FAST");
        // loop-A 节点实例（LOOP 控制节点，jobId 用 jobKey 兜底）
        JobDagNodeInstanceDO loopNode = buildNodeInstance("node-loop", "instance-1", "dag-1",
                "loop-A", "loop-A", "PENDING", 0, 0, null);
        // job-B 节点实例（循环体）
        JobDagNodeInstanceDO jobBNode = buildNodeInstance("node-B", "instance-1", "dag-1",
                "2", "job-B", "PENDING", 0, 0, null);

        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(List.of(instance));
        // job-B 完成事件触发 loopNode（这里直接测试 LOOP 派发路径）
        // 通过 execute 触发 loop-A 起始节点
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(dagInstanceMapper.markRunning(eq("instance-1"), any())).thenReturn(1);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "loop-A"))
                .thenReturn(loopNode);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "2"))
                .thenReturn(jobBNode);
        when(jobMapper.selectById("2")).thenReturn(buildJob("2", "job-B", "NORMAL", 0));
        when(taskDispatcher.dispatch(any(), isNull(), eq("DEPENDENT")))
                .thenReturn("log-B-1", "log-B-2", "log-B-3");
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(loopNode, jobBNode));

        dagInstanceExecutor.execute("instance-1");

        // 验证 LOOP 控制节点被标记 SUCCESS
        verify(dagNodeInstanceMapper).markFinished(eq("node-loop"), eq("SUCCESS"),
                any(), anyLong(), any(), any(), any());
        // 验证循环体被派发 3 次（loopCount=3）
        verify(taskDispatcher, times(3)).dispatch(any(), isNull(), eq("DEPENDENT"));
        // 验证创建了 3 个迭代节点实例（doExecute 先插入 2 个基础节点 + dispatchLoopNode 插入 3 个迭代实例 = 5）
        verify(dagNodeInstanceMapper, times(5)).insert(any(JobDagNodeInstanceDO.class));
    }

    // ==================== PARALLEL_GATEWAY 节点测试 ====================

    @Test
    @DisplayName("PARALLEL_GATEWAY 节点: 并行派发所有下游分支")
    void parallelGatewayNode_dispatchAllBranches() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", PARALLEL_DAG_JSON, "FAIL_FAST");
        // gateway-A 节点实例（PARALLEL_GATEWAY 控制节点，jobId 用 jobKey 兜底）
        JobDagNodeInstanceDO gatewayNode = buildNodeInstance("node-gateway", "instance-1", "dag-1",
                "gateway-A", "gateway-A", "PENDING", 0, 0, null);
        // job-B / job-C 节点实例
        JobDagNodeInstanceDO jobBNode = buildNodeInstance("node-B", "instance-1", "dag-1",
                "2", "job-B", "PENDING", 0, 0, null);
        JobDagNodeInstanceDO jobCNode = buildNodeInstance("node-C", "instance-1", "dag-1",
                "3", "job-C", "PENDING", 0, 0, null);

        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(dagInstanceMapper.markRunning(eq("instance-1"), any())).thenReturn(1);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "gateway-A"))
                .thenReturn(gatewayNode);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "2"))
                .thenReturn(jobBNode);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "3"))
                .thenReturn(jobCNode);
        when(jobMapper.selectById("2")).thenReturn(buildJob("2", "job-B", "NORMAL", 0));
        when(jobMapper.selectById("3")).thenReturn(buildJob("3", "job-C", "NORMAL", 0));
        when(taskDispatcher.dispatch(any(), isNull(), eq("DEPENDENT")))
                .thenReturn("log-B", "log-C");
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(gatewayNode, jobBNode, jobCNode));

        dagInstanceExecutor.execute("instance-1");

        // 验证 PARALLEL_GATEWAY 控制节点被标记 SUCCESS
        verify(dagNodeInstanceMapper).markFinished(eq("node-gateway"), eq("SUCCESS"),
                any(), anyLong(), any(), any(), any());
        // 验证两个下游分支都被派发（job-B 和 job-C）
        verify(taskDispatcher, times(2)).dispatch(any(), isNull(), eq("DEPENDENT"));
    }

    // ==================== 辅助方法 ====================

    private JobDagInstanceDO buildInstance(String id, String dagId, String dagKey,
                                           String status, LocalDateTime startedAt, String contextJson) {
        JobDagInstanceDO instance = new JobDagInstanceDO();
        instance.setId(id);
        instance.setDagId(dagId);
        instance.setDagKey(dagKey);
        instance.setStatus(status);
        instance.setStartedAt(startedAt);
        instance.setContextJson(contextJson);
        instance.setTotalNodes(0);
        instance.setSuccessNodes(0);
        instance.setFailedNodes(0);
        instance.setSkippedNodes(0);
        return instance;
    }

    private JobDagDO buildDag(String id, String dagKey, String dagDefinition, String failStrategy) {
        JobDagDO dag = new JobDagDO();
        dag.setId(id);
        dag.setDagKey(dagKey);
        dag.setDagName("DAG-" + dagKey);
        dag.setDagDefinition(dagDefinition);
        dag.setStatus("ENABLED");
        dag.setTriggerType("MANUAL");
        dag.setFailStrategy(failStrategy);
        dag.setMaxConcurrentInstances(1);
        dag.setVersion(1);
        return dag;
    }

    private JobDO buildJob(String id, String jobKey, String status, Integer maxRetries) {
        JobDO job = new JobDO();
        job.setId(id);
        job.setJobKey(jobKey);
        job.setJobName("Job-" + jobKey);
        job.setHandler("testHandler");
        job.setCronExpression("0 0 8 * * ?");
        job.setStatus(status);
        job.setMaxRetries(maxRetries);
        return job;
    }

    private JobDagNodeInstanceDO buildNodeInstance(String id, String dagInstanceId, String dagId,
                                                   String jobId, String jobKey, String nodeStatus,
                                                   int retryCount, int maxRetries, LocalDateTime startedAt) {
        JobDagNodeInstanceDO node = new JobDagNodeInstanceDO();
        node.setId(id);
        node.setDagInstanceId(dagInstanceId);
        node.setDagId(dagId);
        node.setJobId(jobId);
        node.setJobKey(jobKey);
        node.setNodeStatus(nodeStatus);
        node.setRetryCount(retryCount);
        node.setMaxRetries(maxRetries);
        node.setStartedAt(startedAt);
        return node;
    }
}
