package com.njydsz.cronjob.server.service.job;

import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.cronjob.domain.dto.BatchResult;
import com.njydsz.cronjob.domain.dto.post.JobPostDTO;
import com.njydsz.cronjob.domain.dto.put.JobPutDTO;
import com.njydsz.cronjob.domain.vo.JobLogVO;
import com.njydsz.cronjob.domain.vo.JobVO;

/**
 * 任务调度 Service 接口
 *
 * <p>提供任务（{@code ydsz_job}）的完整管理能力：CRUD、暂停/恢复、立即触发、 调度器注册/取消、应用启动加载等。是定时任务（ydsz-cronjob）模块的核心入口。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #create} / {@link #update} / {@link #delete} / {@link #getById}
 *   <li><b>调度</b>：{@link #pause} / {@link #resume} / {@link #trigger} — 暂停/恢复/立即触发
 *   <li><b>批量</b>：{@link #batchPause} / {@link #batchResume} / {@link #batchTrigger} / {@link
 *       #batchDelete}
 *   <li><b>调度器集成</b>：{@link #register} / {@link #unregister} / {@link #reschedule} — 与 Quartz 调度器集成
 *   <li><b>查询</b>：{@link #page} / {@link #pageLog} — 任务列表 + 执行日志
 *   <li><b>生命周期</b>：{@link #loadOnStartup} — 应用启动时加载所有 NORMAL 任务到调度器
 * </ul>
 *
 * <p><b>并发控制：</b>立即触发支持 {@code holdLock=true} 选项抢占分布式锁， 避免与定时触发并发执行导致重复任务。
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/delete/pause/resume}）开启 {@code @Transactional(rollbackFor
 * = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.cronjob.domain.vo.JobVO 任务视图对象
 * @see com.njydsz.cronjob.server.service.JobHistoryService 任务历史 Service
 */
public interface JobService {

  /**
   * 新增任务
   *
   * <p>创建任务时同时注册到调度器（{@code NORMAL} 状态）。
   *
   * @param dto 任务创建 DTO
   * @return 新增任务 ID
   * @throws SysException 当 jobKey 已存在或参数非法时抛出
   */
  String create(JobPostDTO dto);

  /**
   * 更新任务
   *
   * <p>更新后需重新注册到调度器（{@link #reschedule}）。
   *
   * @param dto 任务更新 DTO
   * @throws SysException 当任务不存在或 cron 表达式非法时抛出
   */
  void update(JobPutDTO dto);

  /**
   * 删除任务
   *
   * <p>同时取消调度器注册 + 清理历史日志。
   *
   * @param id 任务 ID
   * @throws SysException 当任务不存在时抛出
   */
  void delete(String id);

  /**
   * 暂停任务
   *
   * <p>从调度器中移除触发器，但保留任务定义。
   *
   * @param id 任务 ID
   * @throws SysException 当任务不存在时抛出
   */
  void pause(String id);

  /**
   * 恢复任务
   *
   * <p>重新注册到调度器，从下次触发时间开始按 cron 表达式执行。
   *
   * @param id 任务 ID
   * @throws SysException 当任务不存在时抛出
   */
  void resume(String id);

  /**
   * 立即执行一次（默认不抢占分布式锁）
   *
   * <p>默认不抢占分布式锁（与历史行为兼容）。如需测试真实分布式路径， 使用 {@link #trigger(String, boolean)} 并传 {@code
   * holdLock=true}。
   *
   * @param id 任务 ID
   * @return 执行日志 ID
   * @throws SysException 当任务不存在时抛出
   */
  String trigger(String id);

  /**
   * 立即执行一次（可选是否抢占分布式锁）
   *
   * <p>P0-5: 修复手动触发绕过锁的问题。在多实例部署场景下，建议传入 {@code holdLock=true} 走锁路径，避免与定时触发并发执行。
   *
   * @param id 任务 ID
   * @param holdLock 是否抢占分布式锁（true 时与其他实例互斥执行）
   * @return 执行日志 ID；当 holdLock=true 且锁被持有时返回 null
   * @throws SysException 当任务不存在时抛出
   */
  String trigger(String id, boolean holdLock);

  /**
   * 批量暂停任务
   *
   * <p>逐个调用 {@link #pause(String)}，单条失败不影响其他任务。
   *
   * @param jobIds 任务 ID 列表
   * @return 批量操作结果（含成功明细）
   */
  BatchResult<String> batchPause(List<String> jobIds);

  /**
   * 批量恢复任务
   *
   * <p>逐个调用 {@link #resume(String)}，单条失败不影响其他任务。
   *
   * @param jobIds 任务 ID 列表
   * @return 批量操作结果（含成功明细）
   */
  BatchResult<String> batchResume(List<String> jobIds);

  /**
   * 批量触发任务
   *
   * <p>逐个调用 {@link #trigger(String)}，单条失败不影响其他任务。
   *
   * @param jobIds 任务 ID 列表
   * @return 批量操作结果（含成功明细）
   */
  BatchResult<String> batchTrigger(List<String> jobIds);

  /**
   * 批量删除任务
   *
   * <p>逐个调用 {@link #delete(String)}，单条失败不影响其他任务。
   *
   * @param jobIds 任务 ID 列表
   * @return 批量操作结果（含成功明细）
   */
  BatchResult<String> batchDelete(List<String> jobIds);

  /**
   * 注册到调度器（从 DB 加载/动态新增）
   *
   * <p>由 {@link #loadOnStartup} 或 {@link #create} 调用。
   *
   * @param dto 任务创建 DTO
   * @return 注册成功返回 true，否则返回 false
   */
  boolean register(JobPostDTO dto);

  /**
   * 取消注册
   *
   * <p>从 Quartz 调度器中移除触发器，任务定义保留在 DB。
   *
   * @param jobKey 任务 KEY
   * @return 取消成功返回 true，任务未注册返回 false
   */
  boolean unregister(String jobKey);

  /**
   * 重新注册（用于更新 Cron）
   *
   * <p>先取消旧触发器，再注册新触发器（实现 cron 表达式热更新）。
   *
   * @param dto 任务更新 DTO
   * @return 重新注册成功返回 true，否则返回 false
   */
  boolean reschedule(JobPutDTO dto);

  /**
   * 任务详情查询
   *
   * @param id 任务 ID
   * @return 任务视图对象
   * @throws SysException 当任务不存在时抛出
   */
  JobVO getById(String id);

  /**
   * 分页查询任务
   *
   * <p>支持关键字（任务名/KEY/处理器）、状态、分组多条件过滤。
   *
   * @param page 页码
   * @param size 每页条数
   * @param keyword 关键字（任务名/KEY/处理器，可选）
   * @param status 状态过滤（可选）
   * @param group 分组过滤（可选）
   * @return 任务分页数据
   */
  PageResponse<List<JobVO>> page(int page, int size, String keyword, String status, String group);

  /**
   * 分页查询执行日志
   *
   * <p>支持按 {@code jobKey / status} 过滤，按触发时间倒序排列。
   *
   * @param page 页码
   * @param size 每页条数
   * @param jobKey 任务 KEY 过滤（可选）
   * @param status 状态过滤（可选）
   * @return 执行日志分页数据
   */
  PageResponse<List<JobLogVO>> pageLog(int page, int size, String jobKey, String status);

  /**
   * 应用启动时加载所有 NORMAL 任务
   *
   * <p>由 {@code CommandLineRunner} 或 {@code ApplicationReadyEvent} 监听器调用， 将 DB 中所有状态为 NORMAL
   * 的任务注册到调度器。
   */
  void loadOnStartup();
}
