package com.njydsz.pmis.cronjob.core.dag;

import com.alibaba.fastjson2.JSONObject;
import com.njydsz.pmis.cronjob.core.dispatch.TaskDispatcher;
import com.njydsz.pmis.cronjob.entity.JobDagDO;
import com.njydsz.pmis.cronjob.entity.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.entity.JobDagNodeInstanceDO;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.mapper.JobDagInstanceMapper;
import com.njydsz.pmis.cronjob.mapper.JobDagMapper;
import com.njydsz.pmis.cronjob.mapper.JobDagNodeInstanceMapper;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DagInstanceExecutor} 单元测试（P2 DAG 增强）。
 *
 * <p>覆盖 DAG 实例执行器三大核心方法：
 * <ul>
 *   <li>{@link DagInstanceExecutor#execute(String)}：实例加载、markRunning、节点创建、rootNodes 派发</li>
 *   <li>{@link DagInstanceExecutor#onTaskCompleted(TaskCompletedEvent)}：节点完成处理、
 *       FAIL_FAST / CONTINUE_ON_FAIL / SKIP_SUBSEQUENT / RETRY 四种失败策略、上下文合并</li>
 *   <li>{@link DagInstanceExecutor#getDagContext(String)}：跨节点上下文查询</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DagInstanceExecutor DAG 实例执行器测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DagInstanceExecutorTest {

    /** 单节点 DAG 定义（无边） */
    private static final String SINGLE_NODE_DAG_JSON =
            "{\"nodes\":[{\"jobKey\":\"job-A\",\"jobId\":\"1\",\"label\":\"A\",\"x\":0,\"y\":0}],\"edges\":[]}";

    /** 2 节点 1 边 DAG 定义：job-A → job-B */
    private static final String TWO_NODE_DAG_JSON =
            "{\"nodes\":[{\"jobKey\":\"job-A\",\"jobId\":\"1\",\"label\":\"A\",\"x\":0,\"y\":0},"
                    + "{\"jobKey\":\"job-B\",\"jobId\":\"2\",\"label\":\"B\",\"x\":100,\"y\":0}],"
                    + "\"edges\":[{\"from\":\"job-A\",\"to\":\"job-B\"}]}";

    /** 2 节点 1 边 DAG 定义（边级 CONTINUE_ON_FAIL 策略）：job-A →(CONTINUE_ON_FAIL) job-B */
    private static final String CONTINUE_ON_FAIL_EDGE_DAG_JSON =
            "{\"nodes\":[{\"jobKey\":\"job-A\",\"jobId\":\"1\",\"label\":\"A\",\"x\":0,\"y\":0},"
                    + "{\"jobKey\":\"job-B\",\"jobId\":\"2\",\"label\":\"B\",\"x\":100,\"y\":0}],"
                    + "\"edges\":[{\"from\":\"job-A\",\"to\":\"job-B\",\"failStrategy\":\"CONTINUE_ON_FAIL\"}]}";

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

    // ==================== execute 方法 ====================

    @Test
    @DisplayName("execute: 实例不存在时跳过执行")
    void execute_instanceNotFound_skip() {
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(null);

        dagInstanceExecutor.execute("instance-1");

        verify(dagMapper, never()).selectById(any());
        verify(dagInstanceMapper, never()).markRunning(any(), any());
        verify(dagNodeInstanceMapper, never()).insert(any(JobDagNodeInstanceDO.class));
    }

    @Test
    @DisplayName("execute: DAG 定义不存在时标记实例 FAILED")
    void execute_dagNotFound_markFailed() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1", "PENDING", null, null);
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(null);

        dagInstanceExecutor.execute("instance-1");

        verify(dagInstanceMapper, never()).markRunning(any(), any());
        verify(dagInstanceMapper).markFinished(eq("instance-1"), eq("FAILED"),
                any(), anyLong(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("execute: 正常执行，markRunning 成功，创建节点实例并派发 rootNodes")
    void execute_normal_dispatchRootNodes() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1", "PENDING", null, null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", TWO_NODE_DAG_JSON, "FAIL_FAST");
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(dagInstanceMapper.markRunning(eq("instance-1"), any())).thenReturn(1);
        when(jobMapper.selectById("1")).thenReturn(buildJob("1", "job-A", "NORMAL", 2));
        when(jobMapper.selectById("2")).thenReturn(buildJob("2", "job-B", "NORMAL", 2));
        JobDagNodeInstanceDO nodeA = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "PENDING", 0, 2, null);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "1")).thenReturn(nodeA);
        when(taskDispatcher.dispatch(any(), isNull(), eq("DEPENDENT"))).thenReturn("log-A");

        dagInstanceExecutor.execute("instance-1");

        verify(dagInstanceMapper).markRunning(eq("instance-1"), any());
        verify(dagNodeInstanceMapper, times(2)).insert(any(JobDagNodeInstanceDO.class));
        verify(dagInstanceMapper).updateById(any(JobDagInstanceDO.class));
        verify(taskDispatcher, times(1)).dispatch(any(), isNull(), eq("DEPENDENT"));
    }

    @Test
    @DisplayName("execute: markRunning 返回 0（非 PENDING 状态）时跳过执行")
    void execute_notPending_skip() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1", "PENDING", null, null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", TWO_NODE_DAG_JSON, "FAIL_FAST");
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(dagInstanceMapper.markRunning(eq("instance-1"), any())).thenReturn(0);

        dagInstanceExecutor.execute("instance-1");

        verify(dagNodeInstanceMapper, never()).insert(any(JobDagNodeInstanceDO.class));
        verify(taskDispatcher, never()).dispatch(any(), any(), any());
    }

    // ==================== onTaskCompleted 方法 ====================

    @Test
    @DisplayName("onTaskCompleted: 非 DAG 节点（findRunningNodesByJobId 返回空）时跳过")
    void onTaskCompleted_nonDagNode_skip() {
        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(Collections.emptyList());

        TaskCompletedEvent event = new TaskCompletedEvent("1", "job-A", true, null);
        dagInstanceExecutor.onTaskCompleted(event);

        verify(dagNodeInstanceMapper, never()).markFinished(any(), any(), any(), anyLong(),
                any(), any(), any());
    }

    @Test
    @DisplayName("onTaskCompleted: 节点成功，markFinished(SUCCESS) 并触发后继节点")
    void onTaskCompleted_nodeSuccess_markSuccessAndTriggerSuccessors() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", TWO_NODE_DAG_JSON, "FAIL_FAST");
        JobDagNodeInstanceDO nodeA = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "RUNNING", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO nodeASuccess = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "SUCCESS", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO nodeB = buildNodeInstance("node-B", "instance-1", "dag-1",
                "2", "job-B", "PENDING", 0, 0, null);

        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(List.of(instance));
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "1"))
                .thenReturn(nodeA, nodeASuccess);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "2"))
                .thenReturn(nodeB);
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(jobMapper.selectById("2")).thenReturn(buildJob("2", "job-B", "NORMAL", 0));
        when(taskDispatcher.dispatch(any(), isNull(), eq("DEPENDENT"))).thenReturn("log-B");
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(nodeASuccess, nodeB));

        TaskCompletedEvent event = new TaskCompletedEvent("1", "job-A", true, null);
        dagInstanceExecutor.onTaskCompleted(event);

        verify(dagNodeInstanceMapper).markFinished(eq("node-A"), eq("SUCCESS"),
                any(), anyLong(), any(), any(), any());
        verify(taskDispatcher).dispatch(any(), isNull(), eq("DEPENDENT"));
    }

    @Test
    @DisplayName("onTaskCompleted: 节点失败 + FAIL_FAST 策略，跳过所有未完成节点")
    @SuppressWarnings("unchecked")
    void onTaskCompleted_nodeFailed_failFast_skipPendingNodes() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", TWO_NODE_DAG_JSON, "FAIL_FAST");
        JobDagNodeInstanceDO nodeA = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "RUNNING", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO nodeAFailed = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "FAILED", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO nodeB = buildNodeInstance("node-B", "instance-1", "dag-1",
                "2", "job-B", "PENDING", 0, 0, null);
        JobDagNodeInstanceDO nodeBSkipped = buildNodeInstance("node-B", "instance-1", "dag-1",
                "2", "job-B", "SKIPPED", 0, 0, null);

        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(List.of(instance));
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "1"))
                .thenReturn(nodeA);
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(nodeAFailed, nodeB), List.of(nodeAFailed, nodeBSkipped));

        TaskCompletedEvent event = new TaskCompletedEvent("1", "job-A", false, null);
        dagInstanceExecutor.onTaskCompleted(event);

        verify(dagNodeInstanceMapper).markFinished(eq("node-A"), eq("FAILED"),
                any(), anyLong(), any(), anyString(), any());
        verify(dagNodeInstanceMapper).markSkipped("node-B");
        verify(dagInstanceMapper).markFinished(eq("instance-1"), eq("FAILED"),
                any(), anyLong(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("onTaskCompleted: 节点失败 + CONTINUE_ON_FAIL 策略，触发后继节点")
    void onTaskCompleted_nodeFailed_continueOnFail_triggerSuccessors() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", CONTINUE_ON_FAIL_EDGE_DAG_JSON, "CONTINUE_ON_FAIL");
        JobDagNodeInstanceDO nodeA = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "RUNNING", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO nodeASuccess = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "SUCCESS", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO nodeB = buildNodeInstance("node-B", "instance-1", "dag-1",
                "2", "job-B", "PENDING", 0, 0, null);

        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(List.of(instance));
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "1"))
                .thenReturn(nodeA, nodeASuccess);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "2"))
                .thenReturn(nodeB);
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(jobMapper.selectById("2")).thenReturn(buildJob("2", "job-B", "NORMAL", 0));
        when(taskDispatcher.dispatch(any(), isNull(), eq("DEPENDENT"))).thenReturn("log-B");
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(nodeASuccess, nodeB));

        TaskCompletedEvent event = new TaskCompletedEvent("1", "job-A", false, null);
        dagInstanceExecutor.onTaskCompleted(event);

        verify(dagNodeInstanceMapper, never()).markSkipped(any());
        verify(taskDispatcher).dispatch(any(), isNull(), eq("DEPENDENT"));
    }

    @Test
    @DisplayName("onTaskCompleted: 节点失败 + SKIP_SUBSEQUENT 策略，跳过失败节点后继")
    void onTaskCompleted_nodeFailed_skipSubsequent_skipSubsequentNodes() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", TWO_NODE_DAG_JSON, "SKIP_SUBSEQUENT");
        JobDagNodeInstanceDO nodeA = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "RUNNING", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO nodeAFailed = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "FAILED", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO nodeB = buildNodeInstance("node-B", "instance-1", "dag-1",
                "2", "job-B", "PENDING", 0, 0, null);
        JobDagNodeInstanceDO nodeBSkipped = buildNodeInstance("node-B", "instance-1", "dag-1",
                "2", "job-B", "SKIPPED", 0, 0, null);

        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(List.of(instance));
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "1"))
                .thenReturn(nodeA);
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "2"))
                .thenReturn(nodeB);
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(nodeAFailed, nodeBSkipped));

        TaskCompletedEvent event = new TaskCompletedEvent("1", "job-A", false, null);
        dagInstanceExecutor.onTaskCompleted(event);

        verify(dagNodeInstanceMapper).markSkipped("node-B");
        verify(dagInstanceMapper).markFinished(eq("instance-1"), eq("FAILED"),
                any(), anyLong(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("onTaskCompleted: 节点失败 + RETRY 策略，重试节点并重新派发")
    void onTaskCompleted_nodeFailed_retry_retryNode() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", TWO_NODE_DAG_JSON, "RETRY");
        JobDagNodeInstanceDO nodeA = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "RUNNING", 0, 2, LocalDateTime.now());
        JobDagNodeInstanceDO nodeARefreshed = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "PENDING", 1, 2, null);
        JobDagNodeInstanceDO nodeB = buildNodeInstance("node-B", "instance-1", "dag-1",
                "2", "job-B", "PENDING", 0, 0, null);

        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(List.of(instance));
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "1"))
                .thenReturn(nodeA);
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(dagNodeInstanceMapper.markRetry("node-A")).thenReturn(1);
        when(dagNodeInstanceMapper.selectById("node-A")).thenReturn(nodeARefreshed);
        when(jobMapper.selectById("1")).thenReturn(buildJob("1", "job-A", "NORMAL", 2));
        when(taskDispatcher.dispatch(any(), isNull(), eq("DEPENDENT"))).thenReturn("log-A-retry");
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(nodeARefreshed, nodeB));

        TaskCompletedEvent event = new TaskCompletedEvent("1", "job-A", false, null);
        dagInstanceExecutor.onTaskCompleted(event);

        verify(dagNodeInstanceMapper).markRetry("node-A");
        verify(taskDispatcher).dispatch(any(), isNull(), eq("DEPENDENT"));
        verify(dagInstanceMapper, never()).markFinished(eq("instance-1"), any(),
                any(), anyLong(), any(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("onTaskCompleted: 节点失败 + RETRY 策略，重试次数用尽，按 FAIL_FAST 处理")
    @SuppressWarnings("unchecked")
    void onTaskCompleted_nodeFailed_retryExhausted_failFast() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", TWO_NODE_DAG_JSON, "RETRY");
        JobDagNodeInstanceDO nodeA = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "RUNNING", 2, 2, LocalDateTime.now());
        JobDagNodeInstanceDO nodeAFailed = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "FAILED", 2, 2, LocalDateTime.now());
        JobDagNodeInstanceDO nodeB = buildNodeInstance("node-B", "instance-1", "dag-1",
                "2", "job-B", "PENDING", 0, 0, null);
        JobDagNodeInstanceDO nodeBSkipped = buildNodeInstance("node-B", "instance-1", "dag-1",
                "2", "job-B", "SKIPPED", 0, 0, null);

        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(List.of(instance));
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "1"))
                .thenReturn(nodeA);
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(dagNodeInstanceMapper.markRetry("node-A")).thenReturn(0);
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(nodeAFailed, nodeB), List.of(nodeAFailed, nodeBSkipped));

        TaskCompletedEvent event = new TaskCompletedEvent("1", "job-A", false, null);
        dagInstanceExecutor.onTaskCompleted(event);

        verify(dagNodeInstanceMapper).markRetry("node-A");
        verify(dagNodeInstanceMapper).markSkipped("node-B");
        verify(dagInstanceMapper).markFinished(eq("instance-1"), eq("FAILED"),
                any(), anyLong(), anyString(), anyInt(), anyInt(), anyInt(), anyInt());
        verify(taskDispatcher, never()).dispatch(any(), any(), any());
    }

    @Test
    @DisplayName("onTaskCompleted: 所有节点完成时，finalizeInstance 更新 DAG 实例终态")
    void onTaskCompleted_allNodesComplete_finalizeInstance() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", SINGLE_NODE_DAG_JSON, "FAIL_FAST");
        JobDagNodeInstanceDO nodeA = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "RUNNING", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO nodeASuccess = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "SUCCESS", 0, 0, LocalDateTime.now());

        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(List.of(instance));
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "1"))
                .thenReturn(nodeA);
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(nodeASuccess));

        TaskCompletedEvent event = new TaskCompletedEvent("1", "job-A", true, null);
        dagInstanceExecutor.onTaskCompleted(event);

        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(dagInstanceMapper).markFinished(eq("instance-1"), statusCaptor.capture(),
                any(), anyLong(), any(), anyInt(), anyInt(), anyInt(), anyInt());
        assertEquals("SUCCESS", statusCaptor.getValue());
        verify(dagMapper).updateResultStats("dag-1", true);
    }

    @Test
    @DisplayName("onTaskCompleted: 节点成功且 JobLog.resultJson 非空时，合并到 contextJson")
    void onTaskCompleted_contextMerge() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", LocalDateTime.now(), null);
        JobDagDO dag = buildDag("dag-1", "dag-key-1", SINGLE_NODE_DAG_JSON, "FAIL_FAST");
        JobDagNodeInstanceDO nodeA = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "RUNNING", 0, 0, LocalDateTime.now());
        JobDagNodeInstanceDO nodeASuccess = buildNodeInstance("node-A", "instance-1", "dag-1",
                "1", "job-A", "SUCCESS", 0, 0, LocalDateTime.now());
        JobLogDO jobLog = buildJobLog("log-1", "1", "job-A", "{\"count\":100}");

        when(dagInstanceMapper.selectByStatus("RUNNING")).thenReturn(List.of(instance));
        when(dagNodeInstanceMapper.selectByDagInstanceAndJob("instance-1", "1"))
                .thenReturn(nodeA);
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);
        when(dagMapper.selectById("dag-1")).thenReturn(dag);
        when(jobLogMapper.selectById("log-1")).thenReturn(jobLog);
        when(dagNodeInstanceMapper.selectByDagInstanceId("instance-1"))
                .thenReturn(List.of(nodeASuccess));

        TaskCompletedEvent event = new TaskCompletedEvent("1", "job-A", true, "log-1");
        dagInstanceExecutor.onTaskCompleted(event);

        ArgumentCaptor<String> contextCaptor = ArgumentCaptor.forClass(String.class);
        verify(dagInstanceMapper).updateContext(eq("instance-1"), contextCaptor.capture());
        String contextJson = contextCaptor.getValue();
        assertTrue(contextJson.contains("job-A"), "contextJson 应包含 jobKey");
        assertTrue(contextJson.contains("100"), "contextJson 应包含节点结果");
    }

    // ==================== getDagContext 方法 ====================

    @Test
    @DisplayName("getDagContext: 实例不存在时返回空 JSONObject")
    void getDagContext_instanceNotFound_returnEmpty() {
        when(dagInstanceMapper.selectById("nonexistent")).thenReturn(null);

        JSONObject context = dagInstanceExecutor.getDagContext("nonexistent");

        assertNotNull(context);
        assertTrue(context.isEmpty());
    }

    @Test
    @DisplayName("getDagContext: 正常返回上下文 JSON")
    void getDagContext_normal_returnContext() {
        JobDagInstanceDO instance = buildInstance("instance-1", "dag-1", "dag-key-1",
                "RUNNING", null, "{\"job-A\":{\"count\":100}}");
        when(dagInstanceMapper.selectById("instance-1")).thenReturn(instance);

        JSONObject context = dagInstanceExecutor.getDagContext("instance-1");

        assertNotNull(context);
        assertEquals(100, ((JSONObject) context.get("job-A")).getIntValue("count"));
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

    private JobLogDO buildJobLog(String id, String jobId, String jobKey, String resultJson) {
        JobLogDO log = new JobLogDO();
        log.setId(id);
        log.setJobId(jobId);
        log.setJobKey(jobKey);
        log.setResultJson(resultJson);
        return log;
    }
}