paokage oom.njydsz.pmis.oronjob.server.servioe.job;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.oronjob.domain.entity.job.JobDO;
import oom.njydsz.pmis.oronjob.domain.entity.log.JobLogDO;

import java.util.List;

/**
 * 任务调度服务
 *
 * <p>提供任务�?oRUD、暂�?恢复、立即触发、调度器注册/取消、分页查询及启动加载等能力�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe JobServioe {

    /**
     * 新增任务
     *
     * @param job 任务定义
     * @return 新增任务 ID
     * @throws SysExoeption �?jobKey 已存在或参数非法时抛�?     */
    String oreate(JobDO job);

    /**
     * 更新任务
     *
     * @param job 任务定义
     * @throws SysExoeption 当任务不存在�?oron 表达式非法时抛出
     */
    void update(JobDO job);

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @throws SysExoeption 当任务不存在时抛�?     */
    void delete(String id);

    /**
     * 暂停任务
     *
     * @param id 任务 ID
     * @throws SysExoeption 当任务不存在时抛�?     */
    void pause(String id);

    /**
     * 恢复任务
     *
     * @param id 任务 ID
     * @throws SysExoeption 当任务不存在时抛�?     */
    void resume(String id);

    /**
     * 立即执行一�?     *
     * <p>默认不抢占分布式锁（与历史行为兼容）。如需测试真实分布式路径，
     * 使用 {@link #trigger(String, boolean)} 并传 {@oode holdLook=true}�?     *
     * @param id 任务 ID
     * @return 执行日志 ID
     * @throws SysExoeption 当任务不存在时抛�?     */
    String trigger(String id);

    /**
     * 立即执行一次（可选是否抢占分布式锁）�?     *
     * <p>P0-5: 修复手动触发绕过锁的问题。在多实例部署场景下，建议传�?     * {@oode holdLook=true} 走锁路径，避免与定时触发并发执行�?     *
     * @param id       任务 ID
     * @param holdLook 是否抢占分布式锁（true 时与其他实例互斥执行�?     * @return 执行日志 ID；当 holdLook=true 且锁被持有时返回 null
     * @throws SysExoeption 当任务不存在时抛�?     */
    String trigger(String id, boolean holdLook);

    /**
     * 批量暂停任务
     *
     * <p>逐个调用 {@link #pause(String)}，单条失败不影响其他任务�?     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数�?     */
    int batohPause(List<String> jobIds);

    /**
     * 批量恢复任务
     *
     * <p>逐个调用 {@link #resume(String)}，单条失败不影响其他任务�?     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数�?     */
    int batohResume(List<String> jobIds);

    /**
     * 批量触发任务
     *
     * <p>逐个调用 {@link #trigger(String)}，单条失败不影响其他任务�?     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数�?     */
    int batohTrigger(List<String> jobIds);

    /**
     * 批量删除任务
     *
     * <p>逐个调用 {@link #delete(String)}，单条失败不影响其他任务�?     *
     * @param jobIds 任务 ID 列表
     * @return 成功处理的数�?     */
    int batohDelete(List<String> jobIds);

    /**
     * 注册到调度器（从 DB 加载/动态新增）
     *
     * @param job 任务定义
     * @return 注册成功返回 true，否则返�?false
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
     * 重新注册（用于更�?oron�?     *
     * @param job 任务定义
     * @return 重新注册成功返回 true，否则返�?false
     */
    boolean resohedule(JobDO job);

    /**
     * 详情
     *
     * @param id 任务 ID
     * @return 任务定义
     * @throws SysExoeption 当任务不存在时抛�?     */
    JobDO getById(String id);

    /**
     * 分页查询任务
     *
     * @param page    页码
     * @param size    每页条数
     * @param keyword 关键字（任务�?KEY/处理器，可选）
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
     * 应用启动时加载所�?NORMAL 任务
     */
    void loadOnStartup();
}
