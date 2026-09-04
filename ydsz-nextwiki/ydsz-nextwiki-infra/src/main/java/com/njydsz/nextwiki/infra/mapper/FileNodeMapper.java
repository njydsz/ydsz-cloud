package com.njydsz.nextwiki.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.nextwiki.domain.vo.FileStatVO;
import com.njydsz.nextwiki.domain.entity.FileNode;

/**
 * 文件节点 Mapper
 *
 * <p>对应数据表 <code>nw_file_node</code>。
 *
 * <p>文件树节点是知识库的核心数据（文件夹/文件/文档），按父子层级组织，支持版本/分享/ACL。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_node_id — 节点 ID 唯一索引
 *   <li>idx_parent_id — 父子层级索引
 *   <li>idx_tenant_id — 租户隔离索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件（对 MP 自动生成的 SQL 生效）；
 * 注解/XML 手写 SQL 需显式携带 {@code tenant_id}（见 P1-7 修复，路径类查询已显式过滤）。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.nextwiki.infra.entity.FileNode 文件节点实体
 * @see com.njydsz.nextwiki.server.service.FileApplicationService 文件应用服务
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FileNodeMapper extends BaseMapper<FileNode> {

  /**
   * 批量插入节点（P1-3：单条 SQL 批量写入，替代循环 insert，适用于文件夹批量复制场景）。
   *
   * @param entities 节点实体列表
   * @return 受影响行数
   */
  int insertBatch(@Param("list") List<FileNode> entities);

  /**
   * 查询子节点（未删除）。
   *
   * @param parentId 父目录 ID
   * @param tenantId 租户 ID
   * @return 子节点列表
   */
  List<FileNode> selectChildren(
      @Param("parentId") String parentId, @Param("tenantId") String tenantId);

  /**
   * 带 revision 乐观锁的更新（更新失败返回 0）。
   *
   * @param node 待更新的节点实体（含 revision）
   * @return 受影响行数
   */
  int updateWithRevision(@Param("node") FileNode node);

  /**
   * 按路径前缀查询（用于递归操作，P1-7：显式带租户过滤）。
   *
   * @param pathPrefix 路径前缀
   * @param tenantId 租户 ID
   * @return 命中的节点列表
   */
  List<FileNode> selectByPathPrefix(
      @Param("pathPrefix") String pathPrefix, @Param("tenantId") String tenantId);

  /**
   * 数据库分页查询子节点（支持类型过滤与排序）
   *
   * @param page MyBatis-Plus 分页对象
   * @param parentId 父目录ID
   * @param nodeType 节点类型过滤（file/folder，null 表示不过滤）
   * @param sortBy 排序字段：name / size / time
   * @param sortDir 排序方向：asc / desc
   * @param tenantId 租户 ID（P1-7：显式租户过滤，注解/XML SQL 不受 MP 租户拦截器增强）
   * @return 分页结果
   */
  IPage<FileNode> selectPageByParentId(
      IPage<FileNode> page,
      @Param("parentId") String parentId,
      @Param("nodeType") String nodeType,
      @Param("sortBy") String sortBy,
      @Param("sortDir") String sortDir,
      @Param("tenantId") String tenantId);

  /**
   * 批量更新路径前缀（用于目录移动/重命名时递归更新子节点路径）
   *
   * @param oldPathPrefix 原路径前缀
   * @param newPathPrefix 新路径前缀
   * @param levelDelta 层级变化量（正负均可）
   * @param excludeId 需要排除的节点 ID
   * @param tenantId 租户 ID（P1-7：显式租户过滤）
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_file_node SET path = CONCAT(#{newPathPrefix}, SUBSTRING(path, LENGTH(#{oldPathPrefix}) + 1)), "
          + "level = level + #{levelDelta}, updated_at = NOW() "
          + "WHERE path LIKE CONCAT(#{oldPathPrefix}, '%') AND deleted = 0 AND id <> #{excludeId} "
          + "AND tenant_id = #{tenantId}")
  int batchUpdatePathPrefix(
      @Param("oldPathPrefix") String oldPathPrefix,
      @Param("newPathPrefix") String newPathPrefix,
      @Param("levelDelta") int levelDelta,
      @Param("excludeId") String excludeId,
      @Param("tenantId") String tenantId);

  /**
   * 批量逻辑删除路径前缀下的所有节点（用于目录删除时递归逻辑删除子节点）
   *
   * @param pathPrefix 路径前缀
   * @param excludeId 需要排除的节点ID（目录自身已单独删除）
   * @param tenantId 租户 ID（P1-7：显式租户过滤）
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_file_node SET deleted = 1, deleted_time = NOW(), updated_at = NOW() "
          + "WHERE path LIKE CONCAT(#{pathPrefix}, '%') AND deleted = 0 AND id <> #{excludeId} "
          + "AND tenant_id = #{tenantId}")
  int batchSoftDeleteByPathPrefix(
      @Param("pathPrefix") String pathPrefix,
      @Param("excludeId") String excludeId,
      @Param("tenantId") String tenantId);

  /**
   * 逻辑删除（设置 deleted=1 + deleted_time）。
   *
   * @param id 节点 ID
   * @param originalPath 删除前的原始路径（供回收站还原）
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_file_node SET deleted = 1, deleted_time = NOW(), "
          + "original_path = #{originalPath}, updated_at = NOW() WHERE id = #{id}")
  int softDelete(@Param("id") String id, @Param("originalPath") String originalPath);

  /**
   * 批量逻辑删除（移入回收站，用于批量删除场景）。
   *
   * <p>使用 CASE WHEN 一次性更新多条记录，比逐条删除性能更优。
   *
   * @param ids 文件节点ID列表
   * @param originalPaths 原始路径列表（与ids一一对应）
   * @return 受影响行数
   */
  @Update(
      "<script>UPDATE nw_file_node SET deleted = 1, deleted_time = NOW(), updated_at = NOW(), "
          + "original_path = CASE id "
          + "<foreach collection='ids' item='id' separator=' '> "
          + "  WHEN #{id} THEN #{originalPaths[${index}]} "
          + "</foreach> "
          + "END "
          + "WHERE id IN "
          + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
          + "AND deleted = 0</script>")
  int batchSoftDelete(
      @Param("ids") List<String> ids, @Param("originalPaths") List<String> originalPaths);

  /**
   * 批量更新父节点和路径（用于批量移动场景）。
   *
   * <p>使用 CASE WHEN 一次性更新多条记录的路径和层级。
   *
   * @param ids 文件节点ID列表
   * @param targetParentId 目标父节点ID
   * @param newPaths 新路径列表（与ids一一对应）
   * @param levels 新层级列表（与ids一一对应）
   * @return 受影响行数
   */
  @Update(
      "<script>UPDATE nw_file_node SET parent_id = #{targetParentId}, updated_at = NOW(), "
          + "path = CASE id "
          + "<foreach collection='ids' item='id' separator=' '> "
          + "  WHEN #{id} THEN #{newPaths[${index}]} "
          + "</foreach> END, "
          + "level = CASE id "
          + "<foreach collection='ids' item='id' separator=' '> "
          + "  WHEN #{id} THEN #{levels[${index}]} "
          + "</foreach> END "
          + "WHERE id IN "
          + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
          + "AND deleted = 0</script>")
  int batchUpdateParentAndPath(
      @Param("ids") List<String> ids,
      @Param("targetParentId") String targetParentId,
      @Param("newPaths") List<String> newPaths,
      @Param("levels") List<Integer> levels);

  /**
   * 恢复逻辑删除。
   *
   * @param id 节点 ID
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_file_node SET deleted = 0, deleted_time = NULL, updated_at = NOW() WHERE id = #{id}")
  int restore(@Param("id") String id);

  /**
   * 更新大小（P1-11：限制仅更新未删除节点，防误更新回收站条目）。
   *
   * @param id 节点 ID
   * @param sizeDelta 大小变化量（正负均可）
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_file_node SET size = size + #{sizeDelta}, updated_at = NOW() "
          + "WHERE id = #{id} AND deleted = 0")
  int updateSize(@Param("id") String id, @Param("sizeDelta") Long sizeDelta);

  /**
   * 查询用户根目录。
   *
   * @param createdBy 创建者用户 ID
   * @param tenantId 租户 ID
   * @return 根目录节点（不存在时为 null）
   */
  FileNode selectRootByUser(
      @Param("createdBy") String createdBy, @Param("tenantId") String tenantId);

  /**
   * 统计用户文件数量。
   *
   * @param userId 用户 ID
   * @return 文件数量
   */
  @Select(
      "SELECT COUNT(*) FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file'")
  int countByUser(@Param("userId") String userId);

  /**
   * 统计用户文件夹数量。
   *
   * @param userId 用户 ID
   * @return 文件夹数量
   */
  @Select(
      "SELECT COUNT(*) FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'folder'")
  int countFoldersByUser(@Param("userId") String userId);

  /**
   * 查询用户文件总大小。
   *
   * @param userId 用户 ID
   * @return 文件总大小（字节，无文件时为 0）
   */
  @Select(
      "SELECT COALESCE(SUM(size), 0) FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file'")
  Long sumSizeByUser(@Param("userId") String userId);

  /**
   * 查询用户大文件 Top-N。
   *
   * @param userId 用户 ID
   * @param limit 返回数量上限
   * @return 按大小降序排列的文件列表
   */
  @Select(
      "SELECT * FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file' "
          + "ORDER BY size DESC LIMIT #{limit}")
  List<FileNode> findTopLargeFilesByUser(@Param("userId") String userId, @Param("limit") int limit);

  /**
   * 按后缀统计文件数量和大小。
   *
   * @param userId 用户 ID
   * @return 后缀统计列表（按总大小降序）
   */
  @Select(
      "SELECT suffix, COUNT(*) AS file_count, COALESCE(SUM(size), 0) AS total_size "
          + "FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file' "
          + "GROUP BY suffix ORDER BY total_size DESC")
  List<FileStatVO> statsBySuffixAndUser(@Param("userId") String userId);

  /**
   * 按文件哈希查询（用于秒传去重）。
   *
   * @param fileHash 文件哈希
   * @param tenantId 租户 ID
   * @return 命中的文件节点（不存在时为 null）
   */
  @Select(
      "SELECT * FROM nw_file_node WHERE file_hash = #{fileHash} "
          + "AND tenant_id = #{tenantId} AND deleted = 0 AND node_type = 'file' LIMIT 1")
  FileNode findByFileHash(@Param("fileHash") String fileHash, @Param("tenantId") String tenantId);

  /**
   * 按 createdBy + parentId 查询同名文件。
   *
   * @param name 文件名
   * @param parentId 父目录 ID
   * @param createdBy 创建者用户 ID
   * @param tenantId 租户 ID
   * @return 同名文件列表
   */
  @Select(
      "SELECT * FROM nw_file_node WHERE name = #{name} AND parent_id = #{parentId} "
          + "AND created_by = #{createdBy} AND tenant_id = #{tenantId} AND deleted = 0")
  List<FileNode> findByNameAndParent(
      @Param("name") String name,
      @Param("parentId") String parentId,
      @Param("createdBy") String createdBy,
      @Param("tenantId") String tenantId);

  /**
   * 分页查询文件夹的后代节点（用于分批复制场景，避免一次全量加载 OOM）。
   *
   * <p>结果按 level 升序、sort 升序排列，确保父节点先于子节点返回。
   *
   * @param folderPath 文件夹路径（需以 {@code /} 结尾）
   * @param offset 偏移量（从 0 开始）
   * @param limit 每页大小
   * @param tenantId 租户 ID（P1-7：显式租户过滤）
   * @return 后代节点分页列表
   */
  @Select(
      "SELECT * FROM nw_file_node WHERE path LIKE CONCAT(#{folderPath}, '%') "
          + "AND deleted = 0 AND tenant_id = #{tenantId} "
          + "ORDER BY level ASC, sort ASC LIMIT #{limit} OFFSET #{offset}")
  List<FileNode> selectDescendantsByPage(
      @Param("folderPath") String folderPath,
      @Param("offset") int offset,
      @Param("limit") int limit,
      @Param("tenantId") String tenantId);

  /**
   * 统计文件夹的后代节点数量（不含文件夹自身）。
   *
   * @param folderPath 文件夹路径
   * @param tenantId 租户 ID（P1-7：显式租户过滤）
   * @return 后代节点总数
   */
  @Select(
      "SELECT COUNT(*) FROM nw_file_node WHERE path LIKE CONCAT(#{folderPath}, '%') "
          + "AND deleted = 0 AND tenant_id = #{tenantId}")
  int countDescendantsByPath(
      @Param("folderPath") String folderPath, @Param("tenantId") String tenantId);

  /**
   * 查询文件夹的全部后代节点（不含分页）。
   *
   * @param folderPath 文件夹路径（需以 {@code /} 结尾）
   * @param tenantId 租户 ID（P1-7：显式租户过滤）
   * @return 后代节点全量列表
   */
  @Select(
      "SELECT * FROM nw_file_node WHERE path LIKE CONCAT(#{folderPath}, '%') "
          + "AND deleted = 0 AND tenant_id = #{tenantId}")
  List<FileNode> selectAllDescendantsByPath(
      @Param("folderPath") String folderPath, @Param("tenantId") String tenantId);

  /**
   * 查询冷数据候选（长期未访问的文件）。
   *
   * @param threshold 时间阈值（updated_at 早于此时间的文件）
   * @param excludeSuffixes 排除的后缀（逗号分隔，可为空）
   * @param excludeSuffixesList 排除的后缀列表（由 excludeSuffixes 拆分而来，供 foreach 使用）
   * @param limit 返回数量限制
   * @return 冷数据候选列表
   */
  @Select(
      "<script>"
          + "SELECT * FROM nw_file_node WHERE node_type = 'file' AND deleted = 0 "
          + "AND updated_at &lt; #{threshold} "
          + "AND (storage_class IS NULL OR storage_class = 'STANDARD') "
          + "<if test='excludeSuffixes != null and excludeSuffixes != \"\"'>"
          + "  AND suffix NOT IN "
          + "  <foreach item='suffix' collection='excludeSuffixesList' open='(' separator=',' close=')'>"
          + "    #{suffix}"
          + "  </foreach>"
          + "</if>"
          + "ORDER BY updated_at ASC LIMIT #{limit}"
          + "</script>")
  List<FileNode> selectColdCandidates(
      @Param("threshold") LocalDateTime threshold,
      @Param("excludeSuffixes") String excludeSuffixes,
      @Param("excludeSuffixesList") List<String> excludeSuffixesList,
      @Param("limit") int limit);

  /**
   * 统计冷数据数量。
   *
   * @param threshold 时间阈值
   * @return 冷数据数量
   */
  @Select(
      "SELECT COUNT(*) FROM nw_file_node WHERE node_type = 'file' AND deleted = 0 "
          + "AND updated_at &lt; #{threshold} "
          + "AND (storage_class IS NULL OR storage_class = 'STANDARD')")
  long countColdNodes(@Param("threshold") LocalDateTime threshold);

  /**
   * 分页查询全部未删除节点（用于全量索引重建等批量场景）。
   *
   * <p>通过 MyBatis-Plus Page 对象自动添加 LIMIT/OFFSET，避免一次性全量加载导致 OOM。
   *
   * @param page MyBatis-Plus 分页对象
   * @return 分页结果
   */
  IPage<FileNode> selectAllWithPage(IPage<FileNode> page);
}
