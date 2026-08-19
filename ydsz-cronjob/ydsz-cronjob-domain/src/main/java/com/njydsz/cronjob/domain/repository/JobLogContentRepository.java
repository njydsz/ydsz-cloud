package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.vo.JobLogContentVO;

/**
 * 任务日志内容 Repository（domain 层契约）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobLogContentRepository {

  /**
   * 根据日志 ID 分页查询日志内容。
   */
  List<JobLogContentVO> findByLogId(String logId, int offset, int limit);

  /**
   * 查询指定行号之后的日志内容。
   */
  List<JobLogContentVO> findAfterLine(String logId, int fromLineNo);

  /**
   * 统计日志内容的总行数。
   */
  int countByLogId(String logId);

  /**
   * 根据关键字搜索日志内容。
   */
  List<JobLogContentVO> findByLogIdAndKeyword(String logId, String keyword, int offset, int limit);

  /**
   * 清理过期日志内容。
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);

  /**
   * 新增日志内容。
   *
   * @param vo 日志内容 VO
   * @return 新记录 ID
   */
  String insert(JobLogContentVO vo);
}
