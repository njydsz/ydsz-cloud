package com.njydsz.pmis.scheduler.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.scheduler.entity.JobDO;
import com.njydsz.pmis.scheduler.entity.JobLogDO;

import java.util.List;

/**
 * 任务调度服务
 */
public interface JobService {

    /**
     * 新增任务
     */
    Long create(JobDO job);

    /**
     * 更新任务
     */
    void update(JobDO job);

    /**
     * 删除任务
     */
    void delete(Long id);

    /**
     * 暂停任务
     */
    void pause(Long id);

    /**
     * 恢复任务
     */
    void resume(Long id);

    /**
     * 立即执行一次
     */
    Long trigger(Long id);

    /**
     * 注册到调度器（从 DB 加载/动态新增）
     */
    boolean register(JobDO job);

    /**
     * 取消注册
     */
    boolean unregister(String jobKey);

    /**
     * 重新注册（用于更新 Cron）
     */
    boolean reschedule(JobDO job);

    /**
     * 详情
     */
    JobDO getById(Long id);

    /**
     * 分页查询任务
     */
    Page<JobDO> page(int page, int size, String keyword, String status, String group);

    /**
     * 分页查询执行日志
     */
    Page<JobLogDO> pageLog(int page, int size, String jobKey, String status);

    /**
     * 应用启动时加载所有 NORMAL 任务
     */
    void loadOnStartup();
}
