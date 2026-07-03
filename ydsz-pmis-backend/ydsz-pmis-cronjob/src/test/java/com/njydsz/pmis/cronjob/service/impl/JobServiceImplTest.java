package com.njydsz.pmis.cronjob.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.job.JobHandler;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;
import com.njydsz.pmis.cronjob.mapper.JobLogMapper;
import com.njydsz.pmis.cronjob.mapper.JobMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * JobServiceImpl 单元测试
 *
 * @author ydsz-pmis-team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JobServiceImpl 单元测试")
class JobServiceImplTest {

    @Mock
    private JobMapper jobMapper;

    @Mock
    private JobLogMapper jobLogMapper;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private JobServiceImpl jobService;

    private MockedStatic<TenantContext> tenantContextMock;
    private MockedStatic<TraceIdUtil> traceIdUtilMock;

    @BeforeEach
    void setUp() {
        tenantContextMock = mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::getTenantId).thenReturn(1L);

        traceIdUtilMock = mockStatic(TraceIdUtil.class);
        traceIdUtilMock.when(TraceIdUtil::get).thenReturn("test-trace-id");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        tenantContextMock.close();
        traceIdUtilMock.close();
    }

    // ==================== create ====================

    @Test
    @DisplayName("create - 正常创建任务并返回 ID")
    void create_shouldCreateJobAndReturnId() {
        JobDO job = buildJob("test-job", "testHandler", "0 0 8 * * ?");
        when(jobMapper.selectByJobKey("test-job")).thenReturn(null);
        when(jobMapper.insert(any(JobDO.class))).thenAnswer(inv -> {
            JobDO j = inv.getArgument(0);
            j.setId(1L);
            return 1;
        });

        Long id = jobService.create(job);

        assertNotNull(id);
        assertEquals(1L, id);
        verify(jobMapper).insert(any(JobDO.class));
    }

    @Test
    @DisplayName("create - jobKey 重复时抛出 BizException")
    void create_shouldThrowBizExceptionWhenJobKeyDuplicate() {
        JobDO job = buildJob("dup-key", "testHandler", "0 0 8 * * ?");
        when(jobMapper.selectByJobKey("dup-key")).thenReturn(new JobDO());

        BizException ex = assertThrows(BizException.class, () -> jobService.create(job));
        assertEquals(BizErrorCode.DUPLICATE_KEY, ex.getCode());
    }

    @Test
    @DisplayName("create - jobKey 为空时抛出 BizException")
    void create_shouldThrowBizExceptionWhenJobKeyEmpty() {
        JobDO job = buildJob("", "testHandler", "0 0 8 * * ?");

        BizException ex = assertThrows(BizException.class, () -> jobService.create(job));
        assertEquals(BizErrorCode.BAD_REQUEST, ex.getCode());
    }

    @Test
    @DisplayName("create - handler 为空时抛出 BizException")
    void create_shouldThrowBizExceptionWhenHandlerEmpty() {
        JobDO job = buildJob("test-job", "", "0 0 8 * * ?");

        BizException ex = assertThrows(BizException.class, () -> jobService.create(job));
        assertEquals(BizErrorCode.BAD_REQUEST, ex.getCode());
    }

    @Test
    @DisplayName("create - cron 表达式为空时抛出 BizException")
    void create_shouldThrowBizExceptionWhenCronEmpty() {
        JobDO job = buildJob("test-job", "testHandler", "");

        BizException ex = assertThrows(BizException.class, () -> jobService.create(job));
        assertEquals(BizErrorCode.BAD_REQUEST, ex.getCode());
    }

    @Test
    @DisplayName("create - 非法 cron 表达式时抛出 BizException")
    void create_shouldThrowBizExceptionWhenCronInvalid() {
        JobDO job = buildJob("test-job", "testHandler", "invalid-cron");

        BizException ex = assertThrows(BizException.class, () -> jobService.create(job));
        assertEquals(BizErrorCode.BAD_REQUEST, ex.getCode());
    }

    // ==================== getById ====================

    @Test
    @DisplayName("getById - 查询存在的任务返回实体")
    void getById_shouldReturnJobWhenFound() {
        JobDO job = buildJob("test-job", "testHandler", "0 0 8 * * ?");
        job.setId(1L);

        when(jobMapper.selectById(1L)).thenReturn(job);

        JobDO result = jobService.getById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("test-job", result.getJobKey());
    }

    @Test
    @DisplayName("getById - 任务不存在时抛出 BizException")
    void getById_shouldThrowBizExceptionWhenNotFound() {
        when(jobMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> jobService.getById(999L));
        assertEquals(BizErrorCode.NOT_FOUND, ex.getCode());
    }

    // ==================== pause ====================

    @Test
    @DisplayName("pause - 暂停任务并更新状态为 PAUSED")
    void pause_shouldSetStatusToPaused() {
        JobDO job = buildJob("test-job", "testHandler", "0 0 8 * * ?");
        job.setId(1L);
        job.setStatus("NORMAL");

        when(jobMapper.selectById(1L)).thenReturn(job);

        jobService.pause(1L);

        assertEquals("PAUSED", job.getStatus());
        verify(jobMapper).updateById(job);
    }

    @Test
    @DisplayName("pause - 任务不存在时抛出 BizException")
    void pause_shouldThrowBizExceptionWhenJobNotFound() {
        when(jobMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> jobService.pause(999L));
        assertEquals(BizErrorCode.NOT_FOUND, ex.getCode());
    }

    // ==================== resume ====================

    @Test
    @DisplayName("resume - 恢复 PAUSED 任务并更新状态为 NORMAL")
    void resume_shouldSetStatusToNormal() {
        JobDO job = buildJob("test-job", "testHandler", "0 0 8 * * ?");
        job.setId(1L);
        job.setStatus("PAUSED");

        when(jobMapper.selectById(1L)).thenReturn(job);

        jobService.resume(1L);

        assertEquals("NORMAL", job.getStatus());
        verify(jobMapper).updateById(job);
    }

    // ==================== trigger ====================

    @Test
    @DisplayName("trigger - 手动触发执行任务并返回日志 ID")
    void trigger_shouldExecuteJobAndReturnLogId() {
        JobDO job = buildJob("test-job", "testHandler", "0 0 8 * * ?");
        job.setId(1L);
        job.setStatus("NORMAL");

        JobHandler handler = params -> "success";
        when(jobMapper.selectById(1L)).thenReturn(job);
        when(applicationContext.getBean("testHandler", JobHandler.class)).thenReturn(handler);
        when(jobLogMapper.insert(any(JobLogDO.class))).thenAnswer(inv -> {
            JobLogDO log = inv.getArgument(0);
            log.setId(10L);
            return 1;
        });
        when(jobMapper.updateStats(anyLong(), any(), isNull(), anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(1);

        Long logId = jobService.trigger(1L);

        assertNotNull(logId);
        assertEquals(10L, logId);
        verify(jobLogMapper).insert(any(JobLogDO.class));
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete - 删除存在的任务")
    void delete_shouldDeleteJobWhenFound() {
        JobDO job = buildJob("test-job", "testHandler", "0 0 8 * * ?");
        job.setId(1L);

        when(jobMapper.selectById(1L)).thenReturn(job);

        jobService.delete(1L);

        verify(jobMapper).deleteById(1L);
    }

    @Test
    @DisplayName("delete - 任务不存在时抛出 BizException")
    void delete_shouldThrowBizExceptionWhenJobNotFound() {
        when(jobMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> jobService.delete(999L));
        assertEquals(BizErrorCode.NOT_FOUND, ex.getCode());
    }

    // ==================== page ====================

    @Test
    @DisplayName("page - 分页查询带过滤条件返回结果")
    void page_shouldReturnPageWithFilters() {
        Page<JobDO> mockPage = new Page<>(1, 10);
        when(jobMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        Page<JobDO> result = jobService.page(1, 10, "test", "NORMAL", "DEFAULT");

        assertNotNull(result);
        assertEquals(1, result.getCurrent());
        assertEquals(10, result.getSize());
    }

    // ==================== pageLog ====================

    @Test
    @DisplayName("pageLog - 分页查询执行日志")
    void pageLog_shouldReturnLogPage() {
        Page<JobLogDO> mockPage = new Page<>(1, 10);
        when(jobLogMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        Page<JobLogDO> result = jobService.pageLog(1, 10, "test-job", "SUCCESS");

        assertNotNull(result);
        assertEquals(1, result.getCurrent());
    }

    // ==================== register ====================

    @Test
    @DisplayName("register - 非 NORMAL 状态任务注册返回 false")
    void register_shouldReturnFalseWhenStatusNotNormal() {
        JobDO job = buildJob("test-job", "testHandler", "0 0 8 * * ?");
        job.setStatus("PAUSED");

        boolean result = jobService.register(job);

        assertFalse(result);
    }

    @Test
    @DisplayName("register - cron 表达式为空时注册返回 false")
    void register_shouldReturnFalseWhenCronEmpty() {
        JobDO job = buildJob("test-job", "testHandler", null);
        job.setStatus("NORMAL");

        boolean result = jobService.register(job);

        assertFalse(result);
    }

    // ==================== unregister ====================

    @Test
    @DisplayName("unregister - 未注册的任务取消返回 false")
    void unregister_shouldReturnFalseWhenNotRegistered() {
        boolean result = jobService.unregister("non-existent-key");

        assertFalse(result);
    }

    // ==================== update ====================

    @Test
    @DisplayName("update - 更新不存在任务时抛出 BizException")
    void update_shouldThrowBizExceptionWhenJobNotFound() {
        JobDO job = new JobDO();
        job.setId(999L);

        when(jobMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> jobService.update(job));
        assertEquals(BizErrorCode.NOT_FOUND, ex.getCode());
    }

    @Test
    @DisplayName("update - id 为 null 时抛出 BizException")
    void update_shouldThrowBizExceptionWhenIdNull() {
        JobDO job = new JobDO();

        BizException ex = assertThrows(BizException.class, () -> jobService.update(job));
        assertEquals(BizErrorCode.BAD_REQUEST, ex.getCode());
    }

    // ==================== helper ====================

    private JobDO buildJob(String jobKey, String handler, String cronExpression) {
        JobDO job = new JobDO();
        job.setJobName("测试任务");
        job.setJobKey(jobKey);
        job.setHandler(handler);
        job.setCronExpression(cronExpression);
        job.setJobGroup("DEFAULT");
        job.setStatus("NORMAL");
        job.setParamsJson("{}");
        return job;
    }
}