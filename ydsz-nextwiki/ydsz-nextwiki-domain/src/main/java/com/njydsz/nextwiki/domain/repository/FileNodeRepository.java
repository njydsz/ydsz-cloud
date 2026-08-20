package com.njydsz.nextwiki.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.nextwiki.domain.dto.FileNodeDTO;
import com.njydsz.nextwiki.domain.query.FileNodeQuery;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;

/**
 * 文件节点仓储接口
 *
 * <p>领域层定义接口契约，基础设施层提供 MyBatis-Plus 实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回领域 VO（{@link FileNodeVO}），非 DTO / infra 实体
 *   <li>查询入参使用领域 Query（{@link FileNodeQuery}）或具体字段
 *   <li>CUD 入参使用领域 DTO（{@link FileNodeDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FileNodeRepository {

  /**
   * 根据 ID 查询文件节点
   *
   * @param id 文件节点ID
   * @return 文件节点 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FileNodeVO> findById(String id);

  /**
   * 查询子节点列表
   *
   * @param parentId 父节点ID
   * @return 子节点 VO 列表
   */
  List<FileNodeVO> findChildren(String parentId);

  /**
   * 数据库分页查询子节点（支持类型过滤与排序）
   *
   * @param query 分页查询参数
   * @return 分页结果
   */
  PageResponse<List<FileNodeVO>> findPageChildren(FileNodeQuery query);

  /**
   * 根据路径前缀查询（用于递归操作）
   *
   * @param pathPrefix 路径前缀
   * @return 节点 VO 列表
   */
  List<FileNodeVO> findByPathPrefix(String pathPrefix);

  /**
   * 批量更新路径前缀（用于目录移动/重命名时递归更新子节点路径）
   *
   * @param oldPathPrefix 原路径前缀
   * @param newPathPrefix 新路径前缀
   * @param levelDelta 层级变化量
   * @param excludeId 排除的节点ID
   * @return 受影响行数
   */
  int batchUpdatePathPrefix(
      String oldPathPrefix, String newPathPrefix, int levelDelta, String excludeId);

  /**
   * 批量逻辑删除路径前缀下的所有节点
   *
   * @param pathPrefix 路径前缀
   * @param excludeId 排除的节点ID
   * @return 受影响行数
   */
  int batchSoftDeleteByPathPrefix(String pathPrefix, String excludeId);

  /**
   * 保存文件节点
   *
   * @param dto 文件节点 DTO
   * @return 持久化后的文件节点 VO
   */
  FileNodeVO save(FileNodeDTO dto);

  /**
   * 批量保存文件节点
   *
   * @param dtos 待保存的节点 DTO 列表
   * @return 实际插入条数
   */
  int saveBatch(List<FileNodeDTO> dtos);

  /**
   * 更新文件节点（带 revision 乐观锁）
   *
   * @param dto 文件节点 DTO
   */
  void update(FileNodeDTO dto);

  /**
   * 逻辑删除（移入回收站）
   *
   * @param id 文件节点ID
   * @param originalPath 原始路径
   */
  void softDelete(String id, String originalPath);

  /**
   * 恢复逻辑删除
   *
   * @param id 文件节点ID
   */
  void restore(String id);

  /**
   * 物理删除
   *
   * @param id 文件节点ID
   */
  void physicalDelete(String id);

  /**
   * 批量查询
   *
   * @param ids 文件节点ID列表
   * @return 文件节点 VO 列表
   */
  List<FileNodeVO> findByIds(List<String> ids);

  /**
   * 批量逻辑删除（移入回收站，用于批量删除场景）。
   *
   * <p>一次性更新多条记录，比逐条删除性能更优。
   *
   * @param ids 文件节点ID列表
   * @param originalPaths 原始路径列表（与ids一一对应）
   * @return 受影响行数
   */
  int batchSoftDelete(List<String> ids, List<String> originalPaths);

  /**
   * 批量更新父节点和路径（用于批量移动场景）。
   *
   * @param ids 文件节点ID列表
   * @param targetParentId 目标父节点ID
   * @param newPaths 新路径列表（与ids一一对应）
   * @param levelDeltas 层级变化量列表（与ids一一对应）
   * @return 受影响行数
   */
  int batchUpdateParentAndPath(
      List<String> ids, String targetParentId, List<String> newPaths, List<Integer> levelDeltas);

  /**
   * 更新存储用量（移动/删除时更新目录统计）
   *
   * @param id 文件节点ID
   * @param sizeDelta 大小变化量
   */
  void updateSize(String id, Long sizeDelta);

  /**
   * 统计用户文件数量
   *
   * @param userId 用户ID
   * @return 文件数量
   */
  int countByUser(String userId);

  /**
   * 统计用户文件夹数量
   *
   * @param userId 用户ID
   * @return 文件夹数量
   */
  int countFoldersByUser(String userId);

  /**
   * 查询用户文件总大小
   *
   * @param userId 用户ID
   * @return 文件总大小
   */
  long sumSizeByUser(String userId);

  /**
   * 查询用户大文件 Top-N
   *
   * @param userId 用户ID
   * @param limit 返回数量限制
   * @return 文件节点 VO 列表
   */
  List<FileNodeVO> findTopLargeFilesByUser(String userId, int limit);

  /**
   * 按后缀统计文件数量和大小
   *
   * @param userId 用户ID
   * @return 文件类型统计结果列表
   */
  List<FileTypeStat> statsBySuffixAndUser(String userId);

  /**
   * 查询用户根目录（不存在则创建）
   *
   * @param userId 用户ID
   * @return 根目录 VO
   */
  FileNodeVO findOrCreateRoot(String userId);

  /**
   * 按文件哈希查询（用于秒传去重）
   *
   * @param fileHash 文件哈希
   * @return 文件节点 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FileNodeVO> findByFileHash(String fileHash);

  /**
   * 按名称和父节点查询同名文件（防重复上传）
   *
   * @param name 文件名
   * @param parentId 父节点ID
   * @param createdBy 创建人
   * @return 文件节点 VO 列表
   */
  List<FileNodeVO> findByNameAndParent(String name, String parentId, String createdBy);

  /**
   * 查询文件夹下全部后代节点（按路径前缀匹配）
   *
   * @param folderPath 文件夹路径
   * @return 后代节点 VO 列表
   */
  List<FileNodeVO> findAllDescendantsByPath(String folderPath);

  /**
   * 分页查询文件夹的后代节点
   *
   * @param folderPath 文件夹路径
   * @param offset 偏移量
   * @param limit 每页大小
   * @return 后代节点 VO 列表
   */
  List<FileNodeVO> findDescendantsByPage(String folderPath, int offset, int limit);

  /**
   * 统计文件夹的后代节点数量
   *
   * @param folderPath 文件夹路径
   * @return 后代节点总数
   */
  int countDescendants(String folderPath);

  /**
   * 根据文件夹 ID 查询其下全部后代节点
   *
   * @param folderId 文件夹节点 ID
   * @return 后代节点 VO 列表
   */
  List<FileNodeVO> findAllDescendants(String folderId);

  /**
   * 查询冷数据候选（长期未访问的文件）
   *
   * @param threshold 时间阈值
   * @param excludeSuffixes 排除的后缀
   * @param limit 返回数量限制
   * @return 冷数据候选 VO 列表
   */
  List<FileNodeVO> findColdCandidates(LocalDateTime threshold, String excludeSuffixes, int limit);

  /**
   * 统计冷数据数量
   *
   * @param threshold 时间阈值
   * @return 冷数据数量
   */
  long countColdNodes(LocalDateTime threshold);

  /**
   * 查询全部未删除节点（用于全量索引重建等批量场景）
   *
   * @return 全部节点 VO 列表（含文件夹与文件）
   * @deprecated 全量查询可能导致内存溢出，建议使用 {@link #findAllWithPage} 分页批次处理
   */
  @Deprecated
  List<FileNodeVO> findAll();

  /**
   * 分页查询全部未删除节点（用于全量索引重建等批量场景）
   *
   * <p>相比 {@link #findAll()}，分页批次处理可避免一次性加载全部数据导致内存溢出。
   *
   * @param offset 偏移量
   * @param limit 每页数量
   * @return 分页结果
   */
  PageResponse<List<FileNodeVO>> findAllWithPage(int offset, int limit);

  /** 文件类型统计结果 */
  record FileTypeStat(String suffix, int fileCount, long totalSize) {}
}
