package com.njydsz.generator.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.generator.entity.GenHistory;

/**
 * 代码生成任务历史 Repository 接口。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
public interface GenHistoryRepository {

  /**
   * 保存或更新任务记录。
   *
   * @param history 实体
   * @return 保存后的实体
   */
  GenHistory save(GenHistory history);

  /**
   * 根据 ID 查询。
   *
   * @param id 主键
   * @return Optional 实体
   */
  Optional<GenHistory> findById(Long id);

  /**
   * 查询最近 N 条任务记录（按开始时间倒序）。
   *
   * @param limit 查询数量限制
   * @return 任务列表
   */
  List<GenHistory> findRecent(int limit);

  /**
   * 根据状态查询任务。
   *
   * @param statusCode 状态码
   * @return 任务列表
   */
  List<GenHistory> findByStatus(String statusCode);

  /**
   * 删除任务记录。
   *
   * @param id 主键
   */
  void deleteById(Long id);

  /**
   * 统计任务总数。
   *
   * @return 总数
   */
  long count();
}
