package com.njydsz.pmis.cronjob.core.executor;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobNodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

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
 * {@link JobNodeReaper} 单元测试。
 *
 * <p>覆盖 P0-8 僵尸节点回收 + P1-3 故障转移。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobNodeReaper 僵尸节点回收与故障转移测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class JobNodeReaperTest {

    @Mock
    private JobNodeMapper jobNodeMapper;
    @Mock
    private JobLogMapper jobLogMapper;
    @Mock
    private LeaderElector leaderElector;
    @Mock
    private StringRedisTemplate redisTemplate;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private JobNodeReaper reaper;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        try {
            java.lang.reflect.Field f = JobNodeReaper.class.getDeclaredField("cronjobProperties");
            f.setAccessible(true);
            f.set(reaper, cronjobProperties);
        } catch (Exception e) {
            throw new IllegalStateException("注入 cronjobProperties 失败", e);
        }
        reaper.init();
        lenient().when(leaderElector.isLeader(anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("非 Leader 时 reap 不执行")
    void reap_notLeader_skip() {
        when(leaderElector.isLeader(anyString())).thenReturn(false);

        reaper.reap();

        verify(jobNodeMapper, never()).selectStaleOnlineNodeIds(any());
        verify(jobNodeMapper, never()).markStaleOnlineAsOffline(any());
    }

    @Test
    @DisplayName("无僵尸节点时不执行故障转移")
    void reap_noStaleNodes_noFailover() {
        when(jobNodeMapper.selectStaleOnlineNodeIds(any())).thenReturn(Collections.emptyList());

        reaper.reap();

        verify(jobLogMapper, never()).selectRunningByNode(anyString());
        verify(jobLogMapper, never()).markFailedByNodeOffline(anyString(), any());
    }

    @Test
    @DisplayName("僵尸节点无 RUNNING 任务时仅标记 OFFLINE")
    void reap_staleNodeNoRunningTasks_onlyMarkOffline() {
        String nodeId = "host1:8080";
        when(jobNodeMapper.selectStaleOnlineNodeIds(any())).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(Collections.emptyList());

        reaper.reap();

        verify(jobLogMapper, never()).markFailedByNodeOffline(anyString(), any());
        verify(jobNodeMapper, times(1)).markStaleOnlineAsOffline(any());
    }

    @Test
    @DisplayName("僵尸节点有 RUNNING 任务时执行故障转移：释放锁 + 标记 FAILED")
    void reap_staleNodeWithRunningTasks_failoverExecuted() {
        String nodeId = "host1:8080";
        JobLogDO log1 = new JobLogDO();
        log1.setId("log-1");
        log1.setJobId("job-1");
        log1.setJobKey("key-1");
        log1.setStatus("RUNNING");
        log1.setLockHolder("instance-1");

        when(jobNodeMapper.selectStaleOnlineNodeIds(any())).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(List.of(log1));
        when(jobLogMapper.markFailedByNodeOffline(eq(nodeId), any())).thenReturn(1);

        reaper.reap();

        // 释放锁（Lua 脚本安全释放）
        verify(redisTemplate, times(1)).execute(any(RedisScript.class),
                eq(Collections.singletonList("pmis:job:lock:key-1")), eq("instance-1"));
        // 标记为 FAILED
        verify(jobLogMapper, times(1)).markFailedByNodeOffline(eq(nodeId), any());
        // 标记节点为 OFFLINE
        verify(jobNodeMapper, times(1)).markStaleOnlineAsOffline(any());
    }

    @Test
    @DisplayName("无 lockHolder 的日志不触发锁释放")
    void reap_noLockHolder_skipReleaseLock() {
        String nodeId = "host1:8080";
        JobLogDO log1 = new JobLogDO();
        log1.setId("log-1");
        log1.setJobId("job-1");
        log1.setJobKey("key-1");
        log1.setStatus("RUNNING");
        log1.setLockHolder(null);

        when(jobNodeMapper.selectStaleOnlineNodeIds(any())).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(List.of(log1));

        reaper.reap();

        verify(redisTemplate, never()).execute(any(RedisScript.class), any(), any());
        verify(jobLogMapper, times(1)).markFailedByNodeOffline(eq(nodeId), any());
    }

    @Test
    @DisplayName("释放锁异常时不影响标记 FAILED")
    void reap_releaseLockException_continuesMarkFailed() {
        String nodeId = "host1:8080";
        JobLogDO log1 = new JobLogDO();
        log1.setId("log-1");
        log1.setJobId("job-1");
        log1.setJobKey("key-1");
        log1.setStatus("RUNNING");
        log1.setLockHolder("instance-1");

        when(jobNodeMapper.selectStaleOnlineNodeIds(any())).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(List.of(log1));
        when(redisTemplate.execute(any(RedisScript.class), any(), any()))
                .thenThrow(new RuntimeException("redis conn err"));

        reaper.reap(); // 不应抛异常

        verify(jobLogMapper, times(1)).markFailedByNodeOffline(eq(nodeId), any());
    }

    @Test
    @DisplayName("多个僵尸节点逐个执行故障转移")
    void reap_multipleStaleNodes_failoverEach() {
        String node1 = "host1:8080";
        String node2 = "host2:8080";
        JobLogDO log1 = new JobLogDO();
        log1.setId("log-1");
        log1.setJobKey("key-1");
        log1.setLockHolder("instance-1");
        JobLogDO log2 = new JobLogDO();
        log2.setId("log-2");
        log2.setJobKey("key-2");
        log2.setLockHolder("instance-2");

        when(jobNodeMapper.selectStaleOnlineNodeIds(any())).thenReturn(List.of(node1, node2));
        when(jobLogMapper.selectRunningByNode(node1)).thenReturn(List.of(log1));
        when(jobLogMapper.selectRunningByNode(node2)).thenReturn(List.of(log2));

        reaper.reap();

        verify(jobLogMapper, times(1)).selectRunningByNode(node1);
        verify(jobLogMapper, times(1)).selectRunningByNode(node2);
        verify(jobLogMapper, times(1)).markFailedByNodeOffline(eq(node1), any());
        verify(jobLogMapper, times(1)).markFailedByNodeOffline(eq(node2), any());
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), any(), any());
    }

    @Test
    @DisplayName("故障转移异常时不影响标记节点 OFFLINE")
    void reap_failoverException_continuesMarkOffline() {
        String nodeId = "host1:8080";
        when(jobNodeMapper.selectStaleOnlineNodeIds(any())).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenThrow(new RuntimeException("db conn err"));

        reaper.reap(); // 不应抛异常

        verify(jobLogMapper, never()).markFailedByNodeOffline(anyString(), any());
        verify(jobNodeMapper, times(1)).markStaleOnlineAsOffline(any());
    }

    @Test
    @DisplayName("离线超过 30 分钟的节点记录被物理删除")
    void reap_staleOfflineRecords_deleted() {
        when(jobNodeMapper.selectStaleOnlineNodeIds(any())).thenReturn(Collections.emptyList());
        when(jobNodeMapper.deleteStaleOfflineNodes(any())).thenReturn(2);

        reaper.reap();

        verify(jobNodeMapper, times(1)).deleteStaleOfflineNodes(any());
    }

    @Test
    @DisplayName("P1-4: 分片任务故障转移时释放分片级锁（lockKey 含 shard 索引）")
    void reap_shardedTask_releasesShardLevelLock() {
        String nodeId = "host1:8080";
        JobLogDO shardLog = new JobLogDO();
        shardLog.setId("log-shard-1");
        shardLog.setJobId("job-1");
        shardLog.setJobKey("sharded-key");
        shardLog.setStatus("RUNNING");
        shardLog.setLockHolder("instance-1");
        shardLog.setShardIndex(2);
        shardLog.setShardTotal(4);

        when(jobNodeMapper.selectStaleOnlineNodeIds(any())).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(List.of(shardLog));
        when(jobLogMapper.markFailedByNodeOffline(eq(nodeId), any())).thenReturn(1);

        reaper.reap();

        // 释放分片级锁: pmis:job:lock:{jobKey}:shard:{shardIndex}
        verify(redisTemplate, times(1)).execute(any(RedisScript.class),
                eq(Collections.singletonList("pmis:job:lock:sharded-key:shard:2")), eq("instance-1"));
        verify(jobLogMapper, times(1)).markFailedByNodeOffline(eq(nodeId), any());
    }

    @Test
    @DisplayName("P1-4: 非分片任务故障转移时释放普通锁（shardIndex=null）")
    void reap_nonShardedTask_releasesNormalLock() {
        String nodeId = "host1:8080";
        JobLogDO normalLog = new JobLogDO();
        normalLog.setId("log-normal-1");
        normalLog.setJobId("job-2");
        normalLog.setJobKey("normal-key");
        normalLog.setStatus("RUNNING");
        normalLog.setLockHolder("instance-2");
        normalLog.setShardIndex(null);
        normalLog.setShardTotal(null);

        when(jobNodeMapper.selectStaleOnlineNodeIds(any())).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(List.of(normalLog));
        when(jobLogMapper.markFailedByNodeOffline(eq(nodeId), any())).thenReturn(1);

        reaper.reap();

        // 释放普通锁: pmis:job:lock:{jobKey}（无 shard 后缀）
        verify(redisTemplate, times(1)).execute(any(RedisScript.class),
                eq(Collections.singletonList("pmis:job:lock:normal-key")), eq("instance-2"));
    }

    @Test
    @DisplayName("P1-4: 分片和非分片任务混合时分别释放对应锁")
    void reap_mixedShardedAndNonSharded_releasesCorrectLocks() {
        String nodeId = "host1:8080";
        JobLogDO normalLog = new JobLogDO();
        normalLog.setId("log-mix-1");
        normalLog.setJobKey("normal-key");
        normalLog.setLockHolder("instance-1");
        normalLog.setShardIndex(null);

        JobLogDO shardLog = new JobLogDO();
        shardLog.setId("log-mix-2");
        shardLog.setJobKey("sharded-key");
        shardLog.setLockHolder("instance-2");
        shardLog.setShardIndex(3);

        when(jobNodeMapper.selectStaleOnlineNodeIds(any())).thenReturn(List.of(nodeId));
        when(jobLogMapper.selectRunningByNode(nodeId)).thenReturn(List.of(normalLog, shardLog));
        when(jobLogMapper.markFailedByNodeOffline(eq(nodeId), any())).thenReturn(2);

        reaper.reap();

        // 两个锁都被释放（不同 key）
        verify(redisTemplate, times(1)).execute(any(RedisScript.class),
                eq(Collections.singletonList("pmis:job:lock:normal-key")), eq("instance-1"));
        verify(redisTemplate, times(1)).execute(any(RedisScript.class),
                eq(Collections.singletonList("pmis:job:lock:sharded-key:shard:3")), eq("instance-2"));
    }
}
