package com.njydsz.cronjob.server.service.log;

import java.util.List;

import com.njydsz.cronjob.domain.entity.log.JobLogContent;

/**
 * 任务执行日志内容 Service。
 *
 * <p>管理 {@code ydsz_job_log_content} 表的行级日志明细， 与 {@code ydsz_job_log} 1:1 关联，解决大字段（TEXT/GZIP）导致的
 * 列表查询 IO 性能问题：日志列表只查询主表，大字段按需懒加载。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface JobLogContentService {

  /**
   * 批量写入日志内容。
   *
   * @param lines 待写入的日志行（非空）
   */
  void batchSave(List<JobLogContent> lines);

  /**
   * 按日志 ID 分页查询日志内容（按行号升序）。
   *
   * @param logId 日志 ID
   * @param page 页码（从 1 开始）
   * @param size 每页条数
   * @return 日志内容列表
   */
  List<JobLogContent> pageByLogId(String logId, int page, int size);

  /**
   * 查询指定行号之后的日志内容（增量拉取，供 SSE 实时滚动）。
   *
   * @param logId 日志 ID
   * @param fromLineNo 起始行号（不含）
   * @return 日志内容列表
   */
  List<JobLogContent> listAfterLine(String logId, int fromLineNo);

  /**
   * 统计日志总行数。
   *
   * @param logId 日志 ID
   * @return 行数
   */
  int countByLogId(String logId);

  /**
   * 按关键词搜索日志内容。
   *
   * @param logId 日志 ID
   * @param keyword 搜索关键词
   * @param page 页码（从 1 开始）
   * @param size 每页条数
   * @return 匹配的日志内容列表；参数为空时返回空列表
   */
  List<JobLogContent> searchByKeyword(String logId, String keyword, int page, int size);
}
