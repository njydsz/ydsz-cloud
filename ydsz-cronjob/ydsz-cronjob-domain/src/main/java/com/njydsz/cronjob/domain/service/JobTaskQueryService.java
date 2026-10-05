package com.njydsz.cronjob.domain.service;

import java.util.List;
import java.util.Map;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.cronjob.domain.vo.JobTaskVO;

/**
 * MapReduce 子任务查询 Service 接口（domain 层）。
 *
 * <p>封装 JobTaskController 所需的子任务分页和进度查询能力，
 * 遵循 DDD 分层：Controller → Service → Repository。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface JobTaskQueryService {

  /**
   * 查询指定执行日志的子任务列表。
   *
   * @param logId 执行日志 ID
   * @return 子任务列表（按 created_at 升序）
   */
  List<JobTaskVO> findByLogId(String logId);

  /**
   * 分页查询指定执行日志的子任务列表。
   *
   * @param logId 执行日志 ID
   * @param page 页码（从 1 开始）
   * @param size 每页条数
   * @return 子任务分页数据
   */
  PageResponse<List<JobTaskVO>> pageByLogId(String logId, int page, int size);

  /**
   * 查询子任务执行进度。
   *
   * @param logId 执行日志 ID
   * @return 进度汇总（total/pending/running/success/failed/progressPercent）
   */
  Map<String, Object> getProgress(String logId);
}
