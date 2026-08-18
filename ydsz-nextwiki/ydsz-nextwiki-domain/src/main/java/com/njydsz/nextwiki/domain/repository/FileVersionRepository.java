package com.njydsz.nextwiki.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.nextwiki.domain.dto.FileVersionDTO;
import com.njydsz.nextwiki.domain.query.FileVersionQuery;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;

/**
 * 文件版本仓储接口
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回领域 VO（{@link FileVersionVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link FileVersionQuery}）或具体字段
 *   <li>CUD 入参使用领域 DTO（{@link FileVersionDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FileVersionRepository {

  /**
   * 保存版本记录
   *
   * @param dto 文件版本 DTO
   * @return 持久化后的文件版本 VO
   */
  FileVersionVO save(FileVersionDTO dto);

  /**
   * 更新版本记录（带 revision 乐观锁）
   *
   * @param dto 文件版本 DTO
   */
  void update(FileVersionDTO dto);

  /**
   * 查询文件的版本历史
   *
   * @param fileNodeId 文件节点ID
   * @return 版本 VO 列表
   */
  List<FileVersionVO> findByFileNodeId(String fileNodeId);

  /**
   * 查询指定版本
   *
   * @param query 版本查询参数（含 fileNodeId 和 versionNumber）
   * @return 版本 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FileVersionVO> findByFileNodeIdAndVersion(FileVersionQuery query);

  /**
   * 查询当前活跃版本
   *
   * @param fileNodeId 文件节点ID
   * @return 活跃版本 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FileVersionVO> findActiveVersion(String fileNodeId);

  /**
   * 设置活跃版本
   *
   * @param fileNodeId 文件节点ID
   * @param versionNumber 版本号
   */
  void setActiveVersion(String fileNodeId, Integer versionNumber);

  /**
   * 删除版本
   *
   * @param id 版本ID
   */
  void deleteById(String id);

  /**
   * 批量删除超出保留数量的旧版本
   *
   * @param fileNodeId 文件节点ID
   * @param keepCount 保留的版本数量
   * @return 实际删除的版本数
   */
  int deleteExcessVersions(String fileNodeId, int keepCount);

  /**
   * 统计版本数
   *
   * @param fileNodeId 文件节点ID
   * @return 版本数量
   */
  int countByFileNodeId(String fileNodeId);

  /**
   * 查询最旧的版本（用于超限清理）
   *
   * @param fileNodeId 文件节点ID
   * @param limit 返回数量限制
   * @return 版本 VO 列表
   */
  List<FileVersionVO> findOldestVersions(String fileNodeId, int limit);
}
