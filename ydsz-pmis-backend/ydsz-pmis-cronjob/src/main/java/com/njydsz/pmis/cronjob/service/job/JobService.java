package com.njydsz.pmis.cronjob.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.cronjob.entity.job.JobDO;
import com.njydsz.pmis.cronjob.entity.log.JobLogDO;

import java.util.List;

/**
 * 任务调度服务
 *
 * <p>提供任务的 CRUD、暂停/恢复、立即触发、调度器注册/取消、分页查询及启动加载等能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface JobService {

    /**
     * 新增任务
     *
     * @param job 任务定义
     * @return 新增任务 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当 jobKey 已存在或参数非法时抛出
     */
    String create(JobDO job);

    /**
     * 更新任务
     *
     * @param job 任务定义
     * @throws com.njydsz.pmis.common.exception.BizException 当任务不存在或 cron 表达式非法时抛出
     */
    void update(JobDO job);

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当任务不存在时抛出
     */
    void delete(String id);

    /**
     * 暂停任务
     *
     * @param id 任务 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当任务不存在时抛出
     */
    void pause(String id);

    /**
     * 恢复任务
     *
     * @param id 任务 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当任务不存在时抛出
     */
    void resume(String id);

    /**
     * 立即执行一次
     *
     * <p>默认不抢占分布式锁（与历史行为兼容）。如需测试真实分布式路径，
     * 使用 {@link #trigger(String, boolean)} 并传 {@code holdLock=true}。
     *
     * @param id 任务 ID
     * @return 执行日志 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当任务不存在时抛出
     */
    String trigger(String id);

    /**
     * 立即执行一次（可选是否抢占分布式锁）。
     *
     * <p>P0-5: 修复手动触发绕过锁的问题。在多实例部署场景下，建议传入
     * {@code holdLock=true} 走锁路径，避免与定时触发并发执行。
     *
     * @param id       任务 ID
     * @param holdLock 是否抢占分布式锁（true 时与其他实例互斥执行）
     * @return 执行日志 ID；当 holdLock=true 且锁被持有时返回 null
     * @throws com.njydsz.pmis.common.exception.BizException 当任务不存在时抛出
     */
    String trigger(String id, boolean holdLock);

    /**
     * 批量暂停任务
     *
     * <p>逐个调用 {@link #pause(String)}，单条失败不影响其他任务。
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数量
     */
    int batchPause(List<String> jobIds);

    /**
     * 批量恢复任务
     *
     * <p>逐个调用 {@link #resume(String)}，单条失败不影响其他任务。
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数量
     */
    int batchResume(List<String> jobIds);

    /**
     * 批量触发任务
     *
     * <p>逐个调用 {@link #trigger(String)}，单条失败不影响其他任务。
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数量
     */
    int batchTrigger(List<String> jobIds);

    /**
     * 批量删除任务
     *
     * <p>逐个调用 {@link #delete(String)}，单条失败不影响其他任务。
     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数量
     */
    int batchDelete(List<String> jobIds);

    /**
     * 注册到调度器（从 DB 加载/动态新增）
     *
     * @param job 任务定义
     * @return 注册成功返回 true，否则返回 false
     */
    boolean register(JobDO job);

    /**
     * 取消注册
     *
     * @param jobKey 任务 KEY
     * @return 取消成功返回 true，任务未注册返回 false
     */
    boolean unregister(String jobKey);

    /**
     * 重新注册（用于更新 Cron）
     *
     * @param job 任务定义
     * @return 重新注册成功返回 true，否则返回 false
     */
    boolean reschedule(JobDO job);

    /**
     * 详情
     *
     * @param id 任务 ID
     * @return 任务定义
     * @throws com.njydsz.pmis.common.exception.BizException 当任务不存在时抛出
     */
    JobDO getById(String id);

    /**
     * 分页查询任务
     *
     * @param page    页码
     * @param size    每页条数
     * @param keyword 关键字（任务名/KEY/处理器，可选）
     * @param status  状态过滤（可选）
     * @param group   分组过滤（可选）
     * @return 任务分页数据
     */
    Page<JobDO> page(int page, int size, String keyword, String status, String group);

    /**
     * 分页查询执行日志
     *
     * @param page   页码
     * @param size   每页条数
     * @param jobKey 任务 KEY 过滤（可选）
     * @param status 状态过滤（可选）
     * @return 执行日志分页数据
     */
    Page<JobLogDO> pageLog(int page, int size, String jobKey, String status);

    /**
     * 应用启动时加载所有 NORMAL 任务
     */
    void loadOnStartup();
}
