package com.njydsz.pmis.cronjob.core.dispatch;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.discovery.NodeDiscoveryStrategy;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.entity.JobNodeDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import com.njydsz.pmis.cronjob.metrics.CronjobMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FailoverScanner} 单元测试（P1-4）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>failover.enabled=false 时不扫描</li>
 *   <li>非 Leader 时不扫描</li>
 *   <li>无下线节点时不派发</li>
 *   <li>下线节点任务标记 FAILED 并以 FAILOVER 重新派发</li>
 *   <li>单条任务转移失败不影响其他任务</li>
 *   <li>达到 failoverTaskLimit 限制后停止派发</li>
 *   <li>NodeDiscoveryStrategy 不可用时跳过扫描</li>
 *   <li>任务非 NORMAL 状态时跳过派发</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("FailoverScanner 失败自动转移测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FailoverScannerTest {

    @Mock
    private JobLogMapper jobLogMapper;
    @Mock
    private JobMapper jobMapper;
    @Mock
    private TaskDispatcher taskDispatcher;
    @Mock
    private LeaderElector leaderElector;
    @Mock
    private ObjectProvider<NodeDiscoveryStrategy> nodeDiscoveryStrategyProvider;
    @Mock
    private ObjectProvider<CronjobMetrics> cronjobMetricsProvider;
    @Mock
    private NodeDiscoveryStrategy nodeDiscoveryStrategy;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private FailoverScanner scanner;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties（@InjectMocks 不会自动创建配置对象）
        java.lang.reflect.Field f = FailoverScanner.class.getDeclaredField("cronjobProperties");
        f.setAccessible(true);
        f.set(scanner, cronjobProperties);
        // P1-4: 手动注入 ObjectProvider 字段，避免 @InjectMocks 因类型擦除将
        // nodeDiscoveryStrategyProvider / cronjobMetricsProvider 互相错位注入
        java.lang.reflect.Field fStrategy = FailoverScanner.class.getDeclaredField("nodeDiscoveryStrategyProvider");
        fStrategy.setAccessible(true);
        fStrategy.set(scanner, nodeDiscoveryStrategyProvider);
        java.lang.reflect.Field fMetrics = FailoverScanner.class.getDeclaredField("cronjobMetricsProvider");
        fMetrics.setAccessible(true);
        fMetrics.set(scanner, cronjobMetricsProvider);
        scanner.init();

        // 默认是 Leader
        lenient().when(leaderElector.isLeader(anyString())).thenReturn(true);
        // 默认 NodeDiscoveryStrategy 可用，返回空在线节点列表（下线节点测试用例会覆盖）
        lenient().when(nodeDiscoveryStrategyProvider.getIfAvailable()).thenReturn(nodeDiscoveryStrategy);
        lenient().when(nodeDiscoveryStrategy.getOnlineNodes()).thenReturn(Collections.emptyList());
        // 默认 CronjobMetrics 不可用
        lenient().when(cronjobMetricsProvider.getIfAvailable()).thenReturn(null);
        // 默认无 RUNNING 任务节点
        lenient().when(jobLogMapper.selectRunningNodeIds()).thenReturn(Collections.emptyList());
    }

    @Test
    @DisplayName("failover.enabled=false 时 scan 不执行")
    void scan_failoverDisabled_skip() {
        cronjobProperties.getFailover().setEnabled(false);

        scanner.scan();

        verify(nodeDiscoveryStrategyProvider, never()).getIfAvailable();
        verify(jobLogMapper, never()).selectRunningByNode(anyString());
        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("非 Leader 时 scan 不执行")
    void scan_notLeader_skip() {
        when(leaderElector.isLeader(anyString())).thenReturn(false);

        scanner.scan();

        verify(nodeDiscoveryStrategyProvider, never()).getIfAvailable();
        verify(jobLogMapper, never()).selectRunningByNode(anyString());
        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("无下线节点时不执行故障转移")
    void scan_noOfflineNodes_noDispatch() {
        // 在线节点包含所有 RUNNING 任务的节点
        String nodeId = "host1:8080";
        when(nodeDiscoveryStrategy.getOnlineNodes()).thenReturn(List.of(buildOnlineNode(nodeId)));
        when(jobLogMapper.selectRunningNodeIds()).thenReturn(List.of(nodeId));

        scanner.scan();

        verify(jobLogMapper, never()).selectRunningByNode(anyString());
        verify(jobLogMapper, never()).markFailedByNodeOffline(anyString(), any());
        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("下线节点任务标记 FAILED 并以 FAILOVER 重新派发")
    void scan_offlineNode_markFailedAndRedispatch() {
        String nodeId = "host1:8080";
        // 在线节点列表为空（nodeId 已下线）
        when(nodeDiscoveryStrategy.getOnlineNodes()).thenReturn(Collections.emptyList());
        when(jobLogMapper.selectRunningNodeIds()).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(List.of(buildRunningLog("log-1", "job-1", nodeId)));
        when(jobLogMapper.markFailedByNodeOffline(eq(nodeId), any())).thenReturn(1);
        JobDO job = buildNormalJob("job-1", "key-1");
        when(jobMapper.selectById("job-1")).thenReturn(job);
        when(taskDispatcher.dispatch(any(), any(), eq(DefaultTaskDispatcher.TRIGGER_FAILOVER))).thenReturn("log-new");

        scanner.scan();

        verify(jobLogMapper, times(1)).markFailedByNodeOffline(eq(nodeId), any());
        verify(taskDispatcher, times(1)).dispatch(eq(job), any(), eq(DefaultTaskDispatcher.TRIGGER_FAILOVER));
    }

    @Test
    @DisplayName("单条任务转移失败不影响其他任务")
    void scan_singleTaskFail_continuesOthers() {
        String nodeId = "host1:8080";
        when(nodeDiscoveryStrategy.getOnlineNodes()).thenReturn(Collections.emptyList());
        when(jobLogMapper.selectRunningNodeIds()).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(List.of(
                buildRunningLog("log-1", "job-1", nodeId),
                buildRunningLog("log-2", "job-2", nodeId)));
        when(jobLogMapper.markFailedByNodeOffline(eq(nodeId), any())).thenReturn(2);

        JobDO job1 = buildNormalJob("job-1", "key-1");
        JobDO job2 = buildNormalJob("job-2", "key-2");
        when(jobMapper.selectById("job-1")).thenReturn(job1);
        when(jobMapper.selectById("job-2")).thenReturn(job2);
        // 第一条派发抛异常，第二条应继续
        when(taskDispatcher.dispatch(eq(job1), any(), anyString()))
                .thenThrow(new RuntimeException("dispatch err"));
        when(taskDispatcher.dispatch(eq(job2), any(), anyString())).thenReturn("log-2-new");

        scanner.scan(); // 不应抛异常

        verify(taskDispatcher, times(2)).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("达到 failoverTaskLimit 限制后停止派发")
    void scan_taskLimit_reached() {
        String nodeId = "host1:8080";
        cronjobProperties.getFailover().setFailoverTaskLimit(2);

        // 节点上有 5 个 RUNNING 任务
        List<JobLogDO> runningLogs = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            runningLogs.add(buildRunningLog("log-" + i, "job-" + i, nodeId));
        }
        when(nodeDiscoveryStrategy.getOnlineNodes()).thenReturn(Collections.emptyList());
        when(jobLogMapper.selectRunningNodeIds()).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(runningLogs);
        when(jobLogMapper.markFailedByNodeOffline(eq(nodeId), any())).thenReturn(5);
        for (int i = 1; i <= 5; i++) {
            when(jobMapper.selectById("job-" + i)).thenReturn(buildNormalJob("job-" + i, "key-" + i));
        }
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenReturn("log-new");

        scanner.scan();

        // 仅派发前 2 个（受 failoverTaskLimit 限制）
        verify(taskDispatcher, times(2)).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("NodeDiscoveryStrategy 不可用时跳过扫描")
    void scan_strategyUnavailable_skip() {
        when(nodeDiscoveryStrategyProvider.getIfAvailable()).thenReturn(null);

        scanner.scan();

        verify(jobLogMapper, never()).selectRunningByNode(anyString());
        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("任务非 NORMAL 状态时跳过派发")
    void scan_jobNotNormal_skipDispatch() {
        String nodeId = "host1:8080";
        when(nodeDiscoveryStrategy.getOnlineNodes()).thenReturn(Collections.emptyList());
        when(jobLogMapper.selectRunningNodeIds()).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(List.of(buildRunningLog("log-1", "job-1", nodeId)));
        when(jobLogMapper.markFailedByNodeOffline(eq(nodeId), any())).thenReturn(1);
        JobDO job = buildNormalJob("job-1", "key-1");
        job.setStatus("PAUSED"); // 非 NORMAL
        when(jobMapper.selectById("job-1")).thenReturn(job);

        scanner.scan();

        verify(jobLogMapper, times(1)).markFailedByNodeOffline(eq(nodeId), any());
        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("任务已删除时跳过派发")
    void scan_jobDeleted_skipDispatch() {
        String nodeId = "host1:8080";
        when(nodeDiscoveryStrategy.getOnlineNodes()).thenReturn(Collections.emptyList());
        when(jobLogMapper.selectRunningNodeIds()).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(List.of(buildRunningLog("log-1", "job-1", nodeId)));
        when(jobLogMapper.markFailedByNodeOffline(eq(nodeId), any())).thenReturn(1);
        when(jobMapper.selectById("job-1")).thenReturn(null); // 任务已删除

        scanner.scan();

        verify(jobLogMapper, times(1)).markFailedByNodeOffline(eq(nodeId), any());
        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("多个下线节点逐个执行故障转移")
    void scan_multipleOfflineNodes_failoverEach() {
        String node1 = "host1:8080";
        String node2 = "host2:8080";
        when(nodeDiscoveryStrategy.getOnlineNodes()).thenReturn(Collections.emptyList());
        when(jobLogMapper.selectRunningNodeIds()).thenReturn(List.of(node1, node2));
        when(jobLogMapper.selectRunningByNode(node1)).thenReturn(List.of(buildRunningLog("log-1", "job-1", node1)));
        when(jobLogMapper.selectRunningByNode(node2)).thenReturn(List.of(buildRunningLog("log-2", "job-2", node2)));
        when(jobLogMapper.markFailedByNodeOffline(eq(node1), any())).thenReturn(1);
        when(jobLogMapper.markFailedByNodeOffline(eq(node2), any())).thenReturn(1);
        when(jobMapper.selectById("job-1")).thenReturn(buildNormalJob("job-1", "key-1"));
        when(jobMapper.selectById("job-2")).thenReturn(buildNormalJob("job-2", "key-2"));
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenReturn("log-new");

        scanner.scan();

        verify(jobLogMapper, times(1)).markFailedByNodeOffline(eq(node1), any());
        verify(jobLogMapper, times(1)).markFailedByNodeOffline(eq(node2), any());
        verify(taskDispatcher, times(2)).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("doScan 异常时被外层 try-catch 捕获不影响下次")
    void scan_doScanException_swallowed() {
        when(nodeDiscoveryStrategy.getOnlineNodes()).thenThrow(new RuntimeException("nacos err"));

        scanner.scan(); // 不应抛异常

        verify(jobLogMapper, never()).markFailedByNodeOffline(anyString(), any());
        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("节点故障转移异常时不影响其他节点")
    void scan_nodeFailoverException_continuesOthers() {
        String node1 = "host1:8080";
        String node2 = "host2:8080";
        when(nodeDiscoveryStrategy.getOnlineNodes()).thenReturn(Collections.emptyList());
        when(jobLogMapper.selectRunningNodeIds()).thenReturn(List.of(node1, node2));
        // 第一个节点 selectRunningByNode 抛异常，第二个节点应继续
        when(jobLogMapper.selectRunningByNode(node1)).thenThrow(new RuntimeException("db err"));
        when(jobLogMapper.selectRunningByNode(node2)).thenReturn(List.of(buildRunningLog("log-2", "job-2", node2)));
        when(jobLogMapper.markFailedByNodeOffline(eq(node2), any())).thenReturn(1);
        when(jobMapper.selectById("job-2")).thenReturn(buildNormalJob("job-2", "key-2"));
        when(taskDispatcher.dispatch(any(), any(), anyString())).thenReturn("log-new");

        scanner.scan(); // 不应抛异常

        verify(jobLogMapper, times(1)).selectRunningByNode(node1);
        verify(jobLogMapper, times(1)).selectRunningByNode(node2);
        verify(taskDispatcher, times(1)).dispatch(any(), any(), anyString());
    }

    @Test
    @DisplayName("无 RUNNING 任务时不执行故障转移")
    void scan_noRunningTasks_noDispatch() {
        when(nodeDiscoveryStrategy.getOnlineNodes()).thenReturn(Collections.emptyList());
        when(jobLogMapper.selectRunningNodeIds()).thenReturn(Collections.emptyList());

        scanner.scan();

        verify(jobLogMapper, never()).selectRunningByNode(anyString());
        verify(jobLogMapper, never()).markFailedByNodeOffline(anyString(), any());
        verify(taskDispatcher, never()).dispatch(any(), any(), anyString());
    }

    // -------- 辅助方法 --------

    private JobLogDO buildRunningLog(String id, String jobId, String nodeId) {
        JobLogDO log = new JobLogDO();
        log.setId(id);
        log.setJobId(jobId);
        log.setJobKey("key-" + jobId);
        log.setStatus("RUNNING");
        log.setExecNodeId(nodeId);
        log.setStartTime(LocalDateTime.now().minusMinutes(5));
        log.setCreatedAt(LocalDateTime.now());
        log.setDeleted(0);
        return log;
    }

    private JobDO buildNormalJob(String id, String jobKey) {
        JobDO job = new JobDO();
        job.setId(id);
        job.setJobKey(jobKey);
        job.setJobName("测试任务 " + jobKey);
        job.setHandler("testHandler");
        job.setCronExpression("0 0 8 * * ?");
        job.setStatus("NORMAL");
        return job;
    }

    private JobNodeDO buildOnlineNode(String nodeId) {
        JobNodeDO node = new JobNodeDO();
        node.setNodeId(nodeId);
        node.setStatus("ONLINE");
        node.setLastHeartbeat(LocalDateTime.now());
        return node;
    }
}
