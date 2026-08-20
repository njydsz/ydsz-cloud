package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.dto.dag.JobDagSaveDTO;
import com.njydsz.cronjob.domain.vo.JobDagVO;

/**
 * DAG 工作流定义 Repository（domain 层契约）。
 *
 * <p>定义 DAG 工作流定义的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobDagVO}），非 DTO / infra 实体
 *   <li>CUD 入参使用 DTO
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobDagRepository {

  /**
   * 根据 dagKey 查询 DAG 定义。
   *
   * @param dagKey DAG KEY
   * @return DAG 定义 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobDagVO> findByDagKey(String dagKey);

  /**
   * 根据 ID 查询 DAG 定义。
   *
   * @param dagId DAG ID
   * @return DAG 定义 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<JobDagVO> findById(String dagId);

  /**
   * 查询所有启用 CRON 触发的 DAG 定义。
   *
   * @return CRON 触发的 DAG VO 列表
   */
  List<JobDagVO> findCronEnabledDags();

  /**
   * 查询所有启用状态的 DAG 定义。
   *
   * @return 启用的 DAG VO 列表
   */
  List<JobDagVO> findEnabledDags();

  /**
   * 更新 DAG 触发统计字段。
   *
   * @param dagId DAG ID
   * @param lastFireTime 上次触发时间
   * @param nextFireTime 下次触发时间
   * @return 受影响行数
   */
  int updateFireStats(String dagId, LocalDateTime lastFireTime, LocalDateTime nextFireTime);

  /**
   * 更新 DAG 执行结果统计字段。
   *
   * @param dagId DAG ID
   * @param success 是否成功
   * @return 受影响行数
   */
  int updateResultStats(String dagId, boolean success);

  /**
   * 新增 DAG。
   *
   * @param dto DAG 保存 DTO
   * @return 新 DAG ID
   */
  String insert(JobDagSaveDTO dto);

  /**
   * 按 ID 更新 DAG。
   *
   * @param dto DAG 保存 DTO（必须含 id）
   * @return 受影响行数
   */
  int update(JobDagSaveDTO dto);

  /**
   * 按 ID 删除 DAG（逻辑删除）。
   *
   * @param dagId DAG ID
   * @return 受影响行数
   */
  int deleteById(String dagId);

  /**
   * 按 ID 更新 DAG（直接更新 VO 字段，供内部服务使用）。
   *
   * @param vo DAG VO（必须含 id）
   * @return 受影响行数
   */
  int updateById(JobDagVO vo);

  /**
   * 新增 DAG（内部服务使用，接受 VO 参数）。
   *
   * @param vo DAG VO
   * @return 新 DAG ID
   */
  String insert(JobDagVO vo);
}
