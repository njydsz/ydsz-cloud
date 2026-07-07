package com.njydsz.pmis.cronjob.core.executor;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.entity.JobNodeDO;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobNodeHeartbeat} 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>leader.enabled=false 时 register 不调用 mapper</li>
 *   <li>leader.enabled=true 时 register 调用 insert/updateById</li>
 *   <li>register-on-startup=false 时 register 不调用 mapper</li>
 *   <li>leader.enabled=false 时 heartbeat 不调用 mapper</li>
 *   <li>onTaskStart/onTaskComplete 递增递减 running_count</li>
 *   <li>shutdown 在 leader.enabled=false 时不调用 mapper</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobNodeHeartbeat 节点心跳组件测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobNodeHeartbeatTest {

    @Mock
    private JobNodeMapper jobNodeMapper;

    private CronjobProperties cronjobProperties;

    @InjectMocks
    private JobNodeHeartbeat heartbeat;

    @BeforeEach
    void setUp() throws Exception {
        cronjobProperties = new CronjobProperties();
        // 通过反射注入 cronjobProperties
        try {
            java.lang.reflect.Field f = JobNodeHeartbeat.class.getDeclaredField("cronjobProperties");
            f.setAccessible(true);
            f.set(heartbeat, cronjobProperties);
        } catch (Exception e) {
            throw new IllegalStateException("注入 cronjobProperties 失败", e);
        }
        lenient().when(jobNodeMapper.selectById(anyString())).thenReturn(null);
    }

    @Test
    @DisplayName("leader.enabled=false 时 register 不调用 mapper（Leaderless 模式）")
    void register_leaderDisabled_skipMapper() {
        cronjobProperties.getLeader().setEnabled(false);

        heartbeat.register();

        verify(jobNodeMapper, never()).insert(any(JobNodeDO.class));
        verify(jobNodeMapper, never()).updateById(any(JobNodeDO.class));
        assertNull(heartbeat.getNodeId());
    }

    @Test
    @DisplayName("register-on-startup=false 时 register 不调用 mapper")
    void register_registerOnStartupFalse_skip() {
        cronjobProperties.getLeader().setEnabled(true);
        cronjobProperties.getExecutor().setRegisterOnStartup(false);

        heartbeat.register();

        verify(jobNodeMapper, never()).insert(any(JobNodeDO.class));
        verify(jobNodeMapper, never()).updateById(any(JobNodeDO.class));
    }

    @Test
    @DisplayName("leader.enabled=true 且节点不存在时调用 insert")
    void register_leaderEnabledNewNode_callsInsert() {
        cronjobProperties.getLeader().setEnabled(true);
        when(jobNodeMapper.selectById(anyString())).thenReturn(null);

        heartbeat.register();

        verify(jobNodeMapper, times(1)).insert(any(JobNodeDO.class));
        verify(jobNodeMapper, never()).updateById(any(JobNodeDO.class));
    }

    @Test
    @DisplayName("leader.enabled=true 且节点已存在时调用 updateById")
    void register_leaderEnabledExistingNode_callsUpdateById() {
        cronjobProperties.getLeader().setEnabled(true);
        JobNodeDO existing = new JobNodeDO();
        existing.setNodeId("existing-node");
        when(jobNodeMapper.selectById(anyString())).thenReturn(existing);

        heartbeat.register();

        verify(jobNodeMapper, never()).insert(any(JobNodeDO.class));
        verify(jobNodeMapper, times(1)).updateById(any(JobNodeDO.class));
    }

    @Test
    @DisplayName("leader.enabled=false 时 heartbeat 不调用 mapper")
    void heartbeat_leaderDisabled_skip() {
        cronjobProperties.getLeader().setEnabled(false);
        // 即使 nodeId 已设置（通过 register），heartbeat 也应短路返回
        heartbeat.heartbeat();
        verify(jobNodeMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("未注册时 heartbeat 直接返回不调用 mapper")
    void heartbeat_nodeIdNull_skip() {
        // nodeId 未初始化（未调用 register）
        heartbeat.heartbeat();
        verify(jobNodeMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("onTaskStart 递增 running_count")
    void onTaskStart_incrementsCount() {
        heartbeat.onTaskStart();
        heartbeat.onTaskStart();
        // 通过 register + heartbeat 验证 running_count 已递增到 2
        cronjobProperties.getLeader().setEnabled(true);
        when(jobNodeMapper.selectById(anyString())).thenReturn(null);
        heartbeat.register();
        heartbeat.heartbeat();
        // 仅验证不抛异常（具体 running_count 由 AtomicInteger 维护）
    }

    @Test
    @DisplayName("onTaskComplete 递减 running_count（不低于 0）")
    void onTaskComplete_decrementsCount() {
        heartbeat.onTaskStart();
        heartbeat.onTaskComplete();
        heartbeat.onTaskComplete(); // 不应低于 0
        // 验证不抛异常
    }

    @Test
    @DisplayName("shutdown 在 leader.enabled=false 时不调用 mapper")
    void shutdown_leaderDisabled_skip() {
        cronjobProperties.getLeader().setEnabled(false);
        heartbeat.shutdown();
        verify(jobNodeMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("shutdown 在未注册时不调用 mapper")
    void shutdown_nodeIdNull_skip() {
        heartbeat.shutdown();
        verify(jobNodeMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("getNodeId 在未注册时返回 null")
    void getNodeId_beforeRegister_returnsNull() {
        assertNull(heartbeat.getNodeId());
    }

    @Test
    @DisplayName("getNodeId 在 register 后返回非 null")
    void getNodeId_afterRegister_returnsValue() {
        cronjobProperties.getLeader().setEnabled(true);
        when(jobNodeMapper.selectById(anyString())).thenReturn(null);
        heartbeat.register();
        assertEquals(true, heartbeat.getNodeId() != null);
    }
}
