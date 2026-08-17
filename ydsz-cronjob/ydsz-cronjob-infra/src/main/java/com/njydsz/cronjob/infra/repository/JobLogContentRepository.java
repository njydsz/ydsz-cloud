package com.njydsz.cronjob.infra.repository;

import java.util.List;

import com.njydsz.cronjob.domain.entity.log.JobLogContent;

/**
 * 任务日志内容 Repository。
 *
 * <p>封装 {@code ydsz_job_log_content} 表的数据访问，提供业务语义化的查询方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobLogContentRepository {

  /**
   * 根据日志 ID 分页查询日志内容。
   *
   * @param logId 日志 ID
   * @param offset 偏移量
   * @param limit 每页条数
   * @return 日志内容列表
   */
  List<JobLogContent> selectByLogId(String logId, int offset, int limit);

  /**
   * 查询指定行号之后的日志内容。
   *
   * @param logId 日志 ID
   * @param fromLineNo 起始行号
   * @return 日志内容列表
   */
  List<JobLogContent> selectAfterLine(String logId, int fromLineNo);

  /**
   * 统计日志内容的总行数。
   *
   * @param logId 日志 ID
   * @return 总行数
   */
  int countByLogId(String logId);

  /**
   * 根据关键字搜索日志内容。
   *
   * @param logId 日志 ID
   * @param keyword 搜索关键字
   * @param offset 偏移量
   * @param limit 每页条数
   * @return 匹配的日志内容列表
   */
  List<JobLogContent> selectByLogIdAndKeyword(String logId, String keyword, int offset, int limit);

  /**
   * 清理过期日志内容。
   *
   * @param before 过期分界时间
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  int cleanExpiredLogs(java.time.LocalDateTime before, int limit);
}
