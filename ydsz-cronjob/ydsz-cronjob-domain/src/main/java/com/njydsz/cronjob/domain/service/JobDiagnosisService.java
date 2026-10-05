package com.njydsz.cronjob.domain.service;

import java.util.List;
import java.util.Optional;

import com.njydsz.cronjob.domain.vo.JobLogVO;

/**
 * 任务诊断查询 Service 接口（domain 层）。
 *
 * <p>封装 JobDiagnosisController 所需的日志查询能力，
 * 遵循 DDD 分层：Controller → Service → Repository。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface JobDiagnosisService {

  /**
   * 查询指定任务 KEY 的最新一条执行日志。
   *
   * @param jobKey 任务 KEY
   * @return 最新执行日志；无记录返回 {@code Optional.empty()}
   */
  Optional<JobLogVO> findLatestByJobKey(String jobKey);

  /**
   * 查询指定任务 KEY 的执行日志列表（按创建时间倒序）。
   *
   * @param jobKey 任务 KEY
   * @param limit 最多返回条数
   * @return 执行日志 VO 列表
   */
  List<JobLogVO> findByJobKey(String jobKey, int limit);
}
