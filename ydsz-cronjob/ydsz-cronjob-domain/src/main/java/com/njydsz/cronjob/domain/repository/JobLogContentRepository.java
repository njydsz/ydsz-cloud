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
   *
   * @param logId 任务执行日志 ID
   * @param offset 偏移量（从 0 开始）
   * @param limit 每页返回条数
   * @return 日志内容 VO 列表（按行号升序）
   */
  List<JobLogContentVO> findByLogId(String logId, int offset, int limit);

  /**
   * 查询指定行号之后的日志内容。
   *
   * @param logId 任务执行日志 ID
   * @param fromLineNo 起始行号（不含此行）
   * @return 行号大于 fromLineNo 的日志内容 VO 列表
   */
  List<JobLogContentVO> findAfterLine(String logId, int fromLineNo);

  /**
   * 统计日志内容的总行数。
   *
   * @param logId 任务执行日志 ID
   * @return 该日志的总行数
   */
  int countByLogId(String logId);

  /**
   * 根据关键字搜索日志内容。
   *
   * @param logId 任务执行日志 ID
   * @param keyword 搜索关键词（大小写不敏感）
   * @param offset 偏移量（从 0 开始）
   * @param limit 每页返回条数
   * @return 匹配关键词的日志内容 VO 列表
   */
  List<JobLogContentVO> findByLogIdAndKeyword(String logId, String keyword, int offset, int limit);

  /**
   * 清理过期日志内容。
   *
   * @param before 过期时间分界点（删除此时间之前的记录）
   * @param limit 单批最多删除条数
   * @return 实际删除的日志内容条数
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
