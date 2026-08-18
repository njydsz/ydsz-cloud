package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.vo.JobArtifactVO;

/**
 * 任务产物 Repository（domain 层契约）。
 *
 * <p>定义任务执行产物的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobArtifactVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobArtifactRepository {

  /**
   * 根据日志 ID 查询产物列表。
   *
   * @param logId 日志 ID
   * @return 产物 VO 列表
   */
  List<JobArtifactVO> findByLogId(String logId);

  /**
   * 清理过期产物记录。
   *
   * @param before 过期分界时间
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  int cleanExpired(LocalDateTime before, int limit);
}
