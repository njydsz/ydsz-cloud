package com.njydsz.pmis.cronjob.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.cronjob.entity.JobDO;
import com.njydsz.pmis.cronjob.entity.JobLogDO;

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
    Long create(JobDO job);

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
    void delete(Long id);

    /**
     * 暂停任务
     *
     * @param id 任务 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当任务不存在时抛出
     */
    void pause(Long id);

    /**
     * 恢复任务
     *
     * @param id 任务 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当任务不存在时抛出
     */
    void resume(Long id);

    /**
     * 立即执行一次
     *
     * @param id 任务 ID
     * @return 执行日志 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当任务不存在时抛出
     */
    Long trigger(Long id);

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
    JobDO getById(Long id);

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
