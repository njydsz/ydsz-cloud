package com.njydsz.generator.repository;

import com.njydsz.generator.entity.GenHistoryFile;

import java.util.List;

/**
 * 生成历史文件明细 Repository 接口。
 *
 * @author ydsz-team
 * @since 26.09.05
 */
public interface GenHistoryFileRepository {

  /**
   * 保存文件明细。
   *
   * @param file 实体
   * @return 保存后的实体
   */
  GenHistoryFile save(GenHistoryFile file);

  /**
   * 批量保存。
   *
   * @param files 实体集合
   * @return 保存后的实体集合
   */
  List<GenHistoryFile> saveAll(List<GenHistoryFile> files);

  /**
   * 查询任务全部文件明细。
   *
   * @param historyId 任务 ID
   * @return 文件列表
   */
  List<GenHistoryFile> findByHistoryId(Long historyId);

  /**
   * 删除任务全部文件明细。
   *
   * @param historyId 任务 ID
   */
  void deleteByHistoryId(Long historyId);
}
