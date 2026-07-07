package com.njydsz.pmis.cronjob.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.cronjob.core.dag.DagDefinitionCodec;
import com.njydsz.pmis.cronjob.core.dag.DagInstanceExecutor;
import com.njydsz.pmis.cronjob.core.dag.DagParser;
import com.njydsz.pmis.cronjob.dto.JobDagSaveDTO;
import com.njydsz.pmis.cronjob.entity.JobDagDO;
import com.njydsz.pmis.cronjob.entity.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.mapper.JobDagInstanceMapper;
import com.njydsz.pmis.cronjob.mapper.JobDagMapper;
import com.njydsz.pmis.cronjob.mapper.JobDagNodeInstanceMapper;
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
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JobDagServiceImpl} DAG 定义服务单元测试（P2 DAG 增强）。
 *
 * <p>覆盖 DAG 定义的增删改查、状态流转（启用/禁用）与手动触发流程，
 * 重点验证 dagKey 唯一性、DAG 定义校验、CRON 校验、并发限制等业务规则。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("JobDagServiceImpl DAG 定义服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobDagServiceImplTest {

    /** 合法的单节点 DAG 定义 JSON */
    private static final String VALID_DAG_DEFINITION =
            "{\"nodes\":[{\"jobKey\":\"job-A\",\"jobId\":\"1\",\"label\":\"A\",\"x\":0,\"y\":0}],\"edges\":[]}";
    /** 非法的 DAG 定义 JSON（结构错误，codec.fromJson 会抛 BizException） */
    private static final String INVALID_DAG_DEFINITION = "{invalid-json";
    /** 合法的 CRON 表达式 */
    private static final String VALID_CRON = "0 0 8 * * *";

    @Mock
    private JobDagMapper jobDagMapper;
    @Mock
    private JobDagInstanceMapper jobDagInstanceMapper;
    @Mock
    private JobDagNodeInstanceMapper jobDagNodeInstanceMapper;
    @Spy
    private final DagDefinitionCodec dagDefinitionCodec = new DagDefinitionCodec();
    @Spy
    private final DagParser dagParser = new DagParser();
    @Mock
    private JobMapper jobMapper;
    @Mock
    @SuppressWarnings("unchecked")
    private ObjectProvider<DagInstanceExecutor> dagInstanceExecutorProvider;
    @Mock
    private DagInstanceExecutor dagInstanceExecutor;

    @InjectMocks
    private JobDagServiceImpl jobDagService;

    // ==================== createDag ====================

    @Test
    @DisplayName("createDag: 正常创建 MANUAL 类型 DAG，dagKey 不重复且定义合法")
    void createDag_normal_success() {
        JobDagSaveDTO dto = buildSaveDTO("dag-key-1", "测试DAG", VALID_DAG_DEFINITION,
                null, "MANUAL", null, 1, "FAIL_FAST", "描述");
        when(jobDagMapper.selectByDagKey("dag-key-1")).thenReturn(null);
        when(jobDagMapper.insert(any(JobDagDO.class))).thenAnswer(invocation -> {
            JobDagDO dag = invocation.getArgument(0);
            dag.setId("dag-id-1");
            return 1;
        });

        String dagId = jobDagService.createDag(dto);

        assertEquals("dag-id-1", dagId);
        ArgumentCaptor<JobDagDO> captor = ArgumentCaptor.forClass(JobDagDO.class);
        verify(jobDagMapper, times(1)).insert(captor.capture());
        JobDagDO saved = captor.getValue();
        assertEquals("dag-key-1", saved.getDagKey());
        assertEquals("测试DAG", saved.getDagName());
        assertEquals(VALID_DAG_DEFINITION, saved.getDagDefinition());
        assertEquals("DRAFT", saved.getStatus(), "未指定 status 时默认 DRAFT");
        assertEquals("MANUAL", saved.getTriggerType(), "未指定 triggerType 时默认 MANUAL");
        assertEquals("FAIL_FAST", saved.getFailStrategy(), "未指定 failStrategy 时默认 FAIL_FAST");
        assertEquals(1, saved.getMaxConcurrentInstances(), "未指定 maxConcurrentInstances 时默认 1");
        assertEquals(1, saved.getVersion(), "初始版本号为 1");
        assertEquals(0L, saved.getFireCount(), "初始触发次数为 0");
        assertEquals(0L, saved.getSuccessCount(), "初始成功次数为 0");
        assertEquals(0L, saved.getFailCount(), "初始失败次数为 0");
        assertEquals("描述", saved.getDescription());
    }

    @Test
    @DisplayName("createDag: dagKey 已存在时抛 BizException")
    void createDag_duplicateKey_throwsException() {
        JobDagSaveDTO dto = buildSaveDTO("dag-key-1", "测试DAG", VALID_DAG_DEFINITION,
                null, "MANUAL", null, 1, "FAIL_FAST", null);
        when(jobDagMapper.selectByDagKey("dag-key-1")).thenReturn(buildDag("dag-id-1", "dag-key-1", "DRAFT"));

        assertThrows(BizException.class, () -> jobDagService.createDag(dto));
        verify(jobDagMapper, never()).insert(any(JobDagDO.class));
    }

    @Test
    @DisplayName("createDag: DAG 定义非法时抛 BizException（codec.fromJson 解析失败）")
    void createDag_invalidDefinition_throwsException() {
        JobDagSaveDTO dto = buildSaveDTO("dag-key-1", "测试DAG", INVALID_DAG_DEFINITION,
                null, "MANUAL", null, 1, "FAIL_FAST", null);
        when(jobDagMapper.selectByDagKey("dag-key-1")).thenReturn(null);

        assertThrows(BizException.class, () -> jobDagService.createDag(dto));
        verify(jobDagMapper, never()).insert(any(JobDagDO.class));
    }

    @Test
    @DisplayName("createDag: triggerType=CRON 但 cronExpression 为空时抛 BizException")
    void createDag_cronWithoutExpression_throwsException() {
        JobDagSaveDTO dto = buildSaveDTO("dag-key-1", "测试DAG", VALID_DAG_DEFINITION,
                null, "CRON", null, 1, "FAIL_FAST", null);
        when(jobDagMapper.selectByDagKey("dag-key-1")).thenReturn(null);

        assertThrows(BizException.class, () -> jobDagService.createDag(dto));
        verify(jobDagMapper, never()).insert(any(JobDagDO.class));
    }

    @Test
    @DisplayName("createDag: triggerType=MANUAL 无需 cronExpression，创建成功")
    void createDag_manualWithoutCron_success() {
        JobDagSaveDTO dto = buildSaveDTO("dag-key-manual", "手动DAG", VALID_DAG_DEFINITION,
                "DRAFT", "MANUAL", null, 2, "CONTINUE_ON_FAIL", "手动触发");
        when(jobDagMapper.selectByDagKey("dag-key-manual")).thenReturn(null);
        when(jobDagMapper.insert(any(JobDagDO.class))).thenAnswer(invocation -> {
            JobDagDO dag = invocation.getArgument(0);
            dag.setId("dag-id-manual");
            return 1;
        });

        String dagId = jobDagService.createDag(dto);

        assertEquals("dag-id-manual", dagId);
        ArgumentCaptor<JobDagDO> captor = ArgumentCaptor.forClass(JobDagDO.class);
        verify(jobDagMapper).insert(captor.capture());
        assertEquals("MANUAL", captor.getValue().getTriggerType());
        assertEquals("CONTINUE_ON_FAIL", captor.getValue().getFailStrategy());
        assertEquals(2, captor.getValue().getMaxConcurrentInstances());
    }

    // ==================== updateDag ====================

    @Test
    @DisplayName("updateDag: 正常更新 DAG 定义")
    void updateDag_normal_success() {
        JobDagDO exists = buildDag("dag-id-1", "dag-key-old", "DRAFT");
        exists.setVersion(1);
        exists.setTriggerType("MANUAL");
        JobDagSaveDTO dto = buildSaveDTO("dag-key-new", "新名称", VALID_DAG_DEFINITION,
                "ENABLED", "MANUAL", null, 3, "FAIL_FAST", "新描述");
        when(jobDagMapper.selectById("dag-id-1")).thenReturn(exists);
        when(jobDagMapper.selectByDagKey("dag-key-new")).thenReturn(null);

        jobDagService.updateDag("dag-id-1", dto);

        ArgumentCaptor<JobDagDO> captor = ArgumentCaptor.forClass(JobDagDO.class);
        verify(jobDagMapper).updateById(captor.capture());
        JobDagDO updated = captor.getValue();
        assertEquals("dag-key-new", updated.getDagKey());
        assertEquals("新名称", updated.getDagName());
        assertEquals("ENABLED", updated.getStatus());
        assertEquals("MANUAL", updated.getTriggerType());
        assertEquals(3, updated.getMaxConcurrentInstances());
        assertEquals("新描述", updated.getDescription());
        assertEquals(2, updated.getVersion(), "版本号应自增为 2");
    }

    @Test
    @DisplayName("updateDag: dagId 不存在时抛 BizException")
    void updateDag_notFound_throwsException() {
        JobDagSaveDTO dto = buildSaveDTO("dag-key-1", "测试DAG", VALID_DAG_DEFINITION,
                null, "MANUAL", null, 1, "FAIL_FAST", null);
        when(jobDagMapper.selectById("nonexistent")).thenReturn(null);

        assertThrows(BizException.class, () -> jobDagService.updateDag("nonexistent", dto));
        verify(jobDagMapper, never()).updateById(any(JobDagDO.class));
    }

    @Test
    @DisplayName("updateDag: dagKey 与其他 DAG 重复时抛 BizException")
    void updateDag_duplicateKey_throwsException() {
        JobDagDO exists = buildDag("dag-id-1", "dag-key-old", "DRAFT");
        JobDagDO other = buildDag("dag-id-2", "dag-key-conflict", "ENABLED");
        JobDagSaveDTO dto = buildSaveDTO("dag-key-conflict", "测试DAG", VALID_DAG_DEFINITION,
                null, "MANUAL", null, 1, "FAIL_FAST", null);
        when(jobDagMapper.selectById("dag-id-1")).thenReturn(exists);
        when(jobDagMapper.selectByDagKey("dag-key-conflict")).thenReturn(other);

        assertThrows(BizException.class, () -> jobDagService.updateDag("dag-id-1", dto));
        verify(jobDagMapper, never()).updateById(any(JobDagDO.class));
    }

    // ==================== deleteDag ====================

    @Test
    @DisplayName("deleteDag: 正常删除 DAG")
    void deleteDag_normal_success() {
        JobDagDO exists = buildDag("dag-id-1", "dag-key-1", "DRAFT");
        when(jobDagMapper.selectById("dag-id-1")).thenReturn(exists);

        jobDagService.deleteDag("dag-id-1");

        verify(jobDagMapper, times(1)).deleteById("dag-id-1");
    }

    @Test
    @DisplayName("deleteDag: dagId 不存在时抛 BizException")
    void deleteDag_notFound_throwsException() {
        when(jobDagMapper.selectById("nonexistent")).thenReturn(null);

        assertThrows(BizException.class, () -> jobDagService.deleteDag("nonexistent"));
        verify(jobDagMapper, never()).deleteById(anyString());
    }

    // ==================== enableDag ====================

    @Test
    @DisplayName("enableDag: DRAFT → ENABLED 成功")
    void enableDag_fromDraft_success() {
        JobDagDO exists = buildDag("dag-id-1", "dag-key-1", "DRAFT");
        exists.setVersion(1);
        exists.setTriggerType("MANUAL");
        when(jobDagMapper.selectById("dag-id-1")).thenReturn(exists);

        jobDagService.enableDag("dag-id-1");

        ArgumentCaptor<JobDagDO> captor = ArgumentCaptor.forClass(JobDagDO.class);
        verify(jobDagMapper).updateById(captor.capture());
        assertEquals("ENABLED", captor.getValue().getStatus());
        assertEquals(2, captor.getValue().getVersion(), "版本号应自增");
    }

    @Test
    @DisplayName("enableDag: DISABLED → ENABLED 成功")
    void enableDag_fromDisabled_success() {
        JobDagDO exists = buildDag("dag-id-1", "dag-key-1", "DISABLED");
        exists.setVersion(3);
        exists.setTriggerType("MANUAL");
        when(jobDagMapper.selectById("dag-id-1")).thenReturn(exists);

        jobDagService.enableDag("dag-id-1");

        ArgumentCaptor<JobDagDO> captor = ArgumentCaptor.forClass(JobDagDO.class);
        verify(jobDagMapper).updateById(captor.capture());
        assertEquals("ENABLED", captor.getValue().getStatus());
        assertEquals(4, captor.getValue().getVersion());
    }

    @Test
    @DisplayName("enableDag: 已 ENABLED 状态再次启用抛 BizException")
    void enableDag_invalidStatus_throwsException() {
        JobDagDO exists = buildDag("dag-id-1", "dag-key-1", "ENABLED");
        when(jobDagMapper.selectById("dag-id-1")).thenReturn(exists);

        assertThrows(BizException.class, () -> jobDagService.enableDag("dag-id-1"));
        verify(jobDagMapper, never()).updateById(any(JobDagDO.class));
    }

    // ==================== disableDag ====================

    @Test
    @DisplayName("disableDag: ENABLED → DISABLED 成功")
    void disableDag_fromEnabled_success() {
        JobDagDO exists = buildDag("dag-id-1", "dag-key-1", "ENABLED");
        exists.setVersion(2);
        exists.setTriggerType("CRON");
        exists.setCronExpression(VALID_CRON);
        when(jobDagMapper.selectById("dag-id-1")).thenReturn(exists);

        jobDagService.disableDag("dag-id-1");

        ArgumentCaptor<JobDagDO> captor = ArgumentCaptor.forClass(JobDagDO.class);
        verify(jobDagMapper).updateById(captor.capture());
        assertEquals("DISABLED", captor.getValue().getStatus());
        assertEquals(3, captor.getValue().getVersion());
    }

    @Test
    @DisplayName("disableDag: DISABLED 状态再次禁用抛 BizException")
    void disableDag_invalidStatus_throwsException() {
        JobDagDO exists = buildDag("dag-id-1", "dag-key-1", "DISABLED");
        when(jobDagMapper.selectById("dag-id-1")).thenReturn(exists);

        assertThrows(BizException.class, () -> jobDagService.disableDag("dag-id-1"));
        verify(jobDagMapper, never()).updateById(any(JobDagDO.class));
    }

    // ==================== getDagById ====================

    @Test
    @DisplayName("getDagById: dagId 不存在时抛 BizException")
    void getDagById_notFound_throwsException() {
        when(jobDagMapper.selectById("nonexistent")).thenReturn(null);

        assertThrows(BizException.class, () -> jobDagService.getDagById("nonexistent"));
    }

    // ==================== triggerDag ====================

    @Test
    @DisplayName("triggerDag: ENABLED 状态触发成功，executor 可用时执行实例")
    void triggerDag_normal_success() {
        JobDagDO dag = buildDag("dag-id-1", "dag-key-1", "ENABLED");
        dag.setMaxConcurrentInstances(1);
        when(jobDagMapper.selectByDagKey("dag-key-1")).thenReturn(dag);
        when(jobDagInstanceMapper.countActiveInstances("dag-id-1")).thenReturn(0);
        when(jobDagInstanceMapper.insert(any(JobDagInstanceDO.class))).thenAnswer(invocation -> {
            JobDagInstanceDO instance = invocation.getArgument(0);
            instance.setId("instance-id-1");
            return 1;
        });
        when(dagInstanceExecutorProvider.getIfAvailable()).thenReturn(dagInstanceExecutor);

        String instanceId = jobDagService.triggerDag("dag-key-1", "user-1");

        assertEquals("instance-id-1", instanceId);
        ArgumentCaptor<JobDagInstanceDO> captor = ArgumentCaptor.forClass(JobDagInstanceDO.class);
        verify(jobDagInstanceMapper).insert(captor.capture());
        JobDagInstanceDO inserted = captor.getValue();
        assertEquals("dag-id-1", inserted.getDagId());
        assertEquals("dag-key-1", inserted.getDagKey());
        assertEquals("PENDING", inserted.getStatus());
        assertEquals("MANUAL", inserted.getTriggerType());
        assertEquals("user-1", inserted.getTriggerBy());
        verify(dagInstanceExecutor, times(1)).execute("instance-id-1");
    }

    @Test
    @DisplayName("triggerDag: dagKey 不存在时抛 BizException")
    void triggerDag_notFound_throwsException() {
        when(jobDagMapper.selectByDagKey("nonexistent")).thenReturn(null);

        assertThrows(BizException.class, () -> jobDagService.triggerDag("nonexistent", "user-1"));
        verify(jobDagInstanceMapper, never()).insert(any(JobDagInstanceDO.class));
    }

    @Test
    @DisplayName("triggerDag: DAG 状态非 ENABLED 时抛 BizException")
    void triggerDag_notEnabled_throwsException() {
        JobDagDO dag = buildDag("dag-id-1", "dag-key-1", "DISABLED");
        when(jobDagMapper.selectByDagKey("dag-key-1")).thenReturn(dag);

        assertThrows(BizException.class, () -> jobDagService.triggerDag("dag-key-1", "user-1"));
        verify(jobDagInstanceMapper, never()).insert(any(JobDagInstanceDO.class));
    }

    @Test
    @DisplayName("triggerDag: 活跃实例数达到上限时抛 BizException")
    void triggerDag_concurrentLimit_throwsException() {
        JobDagDO dag = buildDag("dag-id-1", "dag-key-1", "ENABLED");
        dag.setMaxConcurrentInstances(2);
        when(jobDagMapper.selectByDagKey("dag-key-1")).thenReturn(dag);
        when(jobDagInstanceMapper.countActiveInstances("dag-id-1")).thenReturn(2);

        assertThrows(BizException.class, () -> jobDagService.triggerDag("dag-key-1", "user-1"));
        verify(jobDagInstanceMapper, never()).insert(any(JobDagInstanceDO.class));
        verify(dagInstanceExecutor, never()).execute(anyString());
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造 JobDagSaveDTO。
     */
    private JobDagSaveDTO buildSaveDTO(String dagKey, String dagName, String dagDefinition,
                                       String status, String triggerType, String cronExpression,
                                       Integer maxConcurrentInstances, String failStrategy,
                                       String description) {
        JobDagSaveDTO dto = new JobDagSaveDTO();
        dto.setDagKey(dagKey);
        dto.setDagName(dagName);
        dto.setDagDefinition(dagDefinition);
        dto.setStatus(status);
        dto.setTriggerType(triggerType);
        dto.setCronExpression(cronExpression);
        dto.setMaxConcurrentInstances(maxConcurrentInstances);
        dto.setFailStrategy(failStrategy);
        dto.setDescription(description);
        return dto;
    }

    /**
     * 构造 JobDagDO（默认版本号 1，最大并发 1，触发类型 MANUAL）。
     */
    private JobDagDO buildDag(String id, String dagKey, String status) {
        JobDagDO dag = new JobDagDO();
        dag.setId(id);
        dag.setDagKey(dagKey);
        dag.setDagName("DAG-" + dagKey);
        dag.setDagDefinition(VALID_DAG_DEFINITION);
        dag.setStatus(status);
        dag.setTriggerType("MANUAL");
        dag.setMaxConcurrentInstances(1);
        dag.setFailStrategy("FAIL_FAST");
        dag.setVersion(1);
        dag.setFireCount(0L);
        dag.setSuccessCount(0L);
        dag.setFailCount(0L);
        return dag;
    }
}
