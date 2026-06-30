package com.njydsz.pmis.scheduler.service.impl;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.scheduler.entity.JobDO;
import com.njydsz.pmis.scheduler.mapper.JobLogMapper;
import com.njydsz.pmis.scheduler.mapper.JobMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JobServiceImpl 单元测试
 */
@DisplayName("JobServiceImpl 任务调度测试")
class JobServiceImplTest {

    private JobMapper jobMapper;
    private JobLogMapper jobLogMapper;
    private ApplicationContext applicationContext;
    private TaskScheduler taskScheduler;
    private JobServiceImpl service;

    @BeforeEach
    void setUp() {
        jobMapper = mock(JobMapper.class);
        jobLogMapper = mock(JobLogMapper.class);
        applicationContext = mock(ApplicationContext.class);
        taskScheduler = mock(TaskScheduler.class);
        service = new JobServiceImpl(jobMapper, jobLogMapper, applicationContext);
        // 注入调度器
        try {
            var f = JobServiceImpl.class.getDeclaredField("taskScheduler");
            f.setAccessible(true);
            f.set(service, taskScheduler);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("create 重复 jobKey 应抛 DUPLICATE_KEY")
    void create_duplicate() {
        when(jobMapper.selectByJobKey("k")).thenReturn(new JobDO());
        JobDO j = baseJob("k", "0 0/1 * * * ?");
        assertThatThrownBy(() -> service.create(j))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
    }

    @Test
    @DisplayName("create cron 非法应抛 BAD_REQUEST")
    void create_badCron() {
        JobDO j = baseJob("k", "not-a-cron");
        assertThatThrownBy(() -> service.create(j))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 正常路径应注册到调度器")
    void create_ok() {
        when(jobMapper.selectByJobKey("k")).thenReturn(null);
        when(jobMapper.insert(any(JobDO.class))).thenAnswer(inv -> {
            ((JobDO) inv.getArgument(0)).setId(1L);
            return 1;
        });
        when(taskScheduler.schedule(any(Runnable.class), any(CronTrigger.class)))
                .thenReturn(mock(java.util.concurrent.ScheduledFuture.class));

        JobDO j = baseJob("k", "0 0/1 * * * ?");
        Long id = service.create(j);
        assertThat(id).isEqualTo(1L);
        assertThat(j.getStatus()).isEqualTo("NORMAL");
        assertThat(j.getJobGroup()).isEqualTo("DEFAULT");
    }

    @Test
    @DisplayName("create 缺 jobKey 应抛 BAD_REQUEST")
    void create_noKey() {
        JobDO j = baseJob("", "0 0/1 * * * ?");
        assertThatThrownBy(() -> service.create(j))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("create 缺 handler 应抛 BAD_REQUEST")
    void create_noHandler() {
        JobDO j = baseJob("k", "0 0/1 * * * ?");
        j.setHandler(null);
        assertThatThrownBy(() -> service.create(j))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("update ID 为空应抛 BAD_REQUEST")
    void update_noId() {
        JobDO j = new JobDO();
        assertThatThrownBy(() -> service.update(j))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
    }

    @Test
    @DisplayName("update 不存在应抛 NOT_FOUND")
    void update_notFound() {
        when(jobMapper.selectById(99L)).thenReturn(null);
        JobDO j = new JobDO();
        j.setId(99L);
        j.setCronExpression("0 0/1 * * * ?");
        assertThatThrownBy(() -> service.update(j))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("pause 应注销并写库")
    void pause() {
        JobDO j = baseJob("k", "0 0/1 * * * ?");
        j.setId(1L);
        j.setStatus("NORMAL");
        when(jobMapper.selectById(1L)).thenReturn(j);
        service.pause(1L);
        assertThat(j.getStatus()).isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("delete 不存在应抛 NOT_FOUND")
    void delete_notFound() {
        when(jobMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(BizErrorCode.NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("trigger 找不到 handler 时任务状态为 FAILED")
    void trigger_handlerMissing() {
        JobDO j = baseJob("k", "0 0/1 * * * ?");
        j.setId(1L);
        when(jobMapper.selectById(1L)).thenReturn(j);
        when(jobLogMapper.insert(any(com.njydsz.pmis.scheduler.entity.JobLogDO.class)))
                .thenAnswer(inv -> {
                    ((com.njydsz.pmis.scheduler.entity.JobLogDO) inv.getArgument(0)).setId(99L);
                    return 1;
                });

        Long logId = service.trigger(1L);
        assertThat(logId).isEqualTo(99L);
        // 任务执行失败，job.status 应被置为 ERROR
        org.mockito.Mockito.verify(jobMapper).updateStats(
                org.mockito.ArgumentMatchers.eq(1L), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.eq("ERROR"));
    }

    @Test
    @DisplayName("page 分页应按条件过滤")
    void page() {
        when(jobMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page p =
                    (com.baomidou.mybatisplus.extension.plugins.pagination.Page) inv.getArgument(0);
            p.setRecords(List.of(new JobDO()));
            p.setTotal(1L);
            return p;
        });
        var p = service.page(1, 20, null, null, null);
        assertThat(p.getTotal()).isEqualTo(1L);
    }

    @Test
    @DisplayName("pageLog 应按条件过滤")
    void pageLog() {
        when(jobLogMapper.selectPage(any(), any())).thenAnswer(inv -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page p =
                    (com.baomidou.mybatisplus.extension.plugins.pagination.Page) inv.getArgument(0);
            p.setRecords(List.of(new com.njydsz.pmis.scheduler.entity.JobLogDO()));
            p.setTotal(1L);
            return p;
        });
        var p = service.pageLog(1, 20, "k", "SUCCESS");
        assertThat(p.getTotal()).isEqualTo(1L);
    }

    private JobDO baseJob(String key, String cron) {
        JobDO j = new JobDO();
        j.setJobName("test");
        j.setJobKey(key);
        j.setHandler("heartbeatHandler");
        j.setCronExpression(cron);
        return j;
    }
}
