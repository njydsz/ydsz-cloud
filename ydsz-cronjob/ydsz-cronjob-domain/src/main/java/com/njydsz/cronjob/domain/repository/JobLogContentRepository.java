package com.njydsz.cronjob.domain.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.vo.JobLogContentVO;

/**
 * 任务日志内容 Repository（domain 层契约）。
 *
 * <p>定义任务执行逐行日志的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link JobLogContentVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 * </ul>
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
   * @return 日志内容 VO 列表
   */
  List<JobLogContentVO> findByLogId(String logId, int offset, int limit);

  /**
   * 查询指定行号之后的日志内容。
   *
   * @param logId 日志 ID
   * @param fromLineNo 起始行号
   * @return 日志内容 VO 列表
   */
  List<JobLogContentVO> findAfterLine(String logId, int fromLineNo);

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
   * @return 匹配的日志内容 VO 列表
   */
  List<JobLogContentVO> findByLogIdAndKeyword(String logId, String keyword, int offset, int limit);

  /**
   * 清理过期日志内容。
   *
   * @param before 过期分界时间
   * @param limit 单批最多删除条数
   * @return 实际删除条数
   */
  int cleanExpiredLogs(LocalDateTime before, int limit);
}
