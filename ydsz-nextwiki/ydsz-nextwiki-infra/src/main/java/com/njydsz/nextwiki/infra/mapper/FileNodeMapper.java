package com.njydsz.nextwiki.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;

/**
 * 文件节点 Mapper
 *
 * <p>对应数据表 <code>ydsz_file_node</code>。
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
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.nextwiki.domain.entity.FileNode 文件节点实体
 * @see com.njydsz.nextwiki.server.service.FileNodeService 文件节点 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface FileNodeMapper extends BaseMapper<FileNode> {

  /** 查询子节点（未删除） */
  List<FileNode> selectChildren(
      @Param("parentId") String parentId, @Param("tenantId") String tenantId);

  /** 带 revision 乐观锁的更新（更新失败返回 0） */
  int updateWithRevision(@Param("node") FileNode node);

  /** 统计子节点数量（未删除），避免全量加载 */
  @Select("SELECT COUNT(*) FROM nw_file_node WHERE parent_id = #{parentId} AND deleted = 0")
  int countChildren(@Param("parentId") String parentId);

  /** 按路径前缀查询（用于递归操作） */
  List<FileNode> selectByPathPrefix(@Param("pathPrefix") String pathPrefix);

  /**
   * 数据库分页查询子节点（支持类型过滤与排序）
   *
   * @param page MyBatis-Plus 分页对象
   * @param parentId 父目录ID
   * @param nodeType 节点类型过滤（file/folder，null 表示不过滤）
   * @param sortBy 排序字段：name / size / time
   * @param sortDir 排序方向：asc / desc
   * @return 分页结果
   */
  IPage<FileNode> selectPageByParentId(
      IPage<FileNode> page,
      @Param("parentId") String parentId,
      @Param("nodeType") String nodeType,
      @Param("sortBy") String sortBy,
      @Param("sortDir") String sortDir);

  /**
   * 批量更新路径前缀（用于目录移动/重命名时递归更新子节点路径）
   *
   * @param oldPathPrefix 原路径前缀
   * @param newPathPrefix 新路径前缀
   * @param levelDelta 层级变化量（正负均可）
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_file_node SET path = CONCAT(#{newPathPrefix}, SUBSTRING(path, LENGTH(#{oldPathPrefix}) + 1)), "
          + "level = level + #{levelDelta}, updated_at = NOW() "
          + "WHERE path LIKE CONCAT(#{oldPathPrefix}, '%') AND deleted = 0 AND id <> #{excludeId}")
  int batchUpdatePathPrefix(
      @Param("oldPathPrefix") String oldPathPrefix,
      @Param("newPathPrefix") String newPathPrefix,
      @Param("levelDelta") int levelDelta,
      @Param("excludeId") String excludeId);

  /**
   * 批量逻辑删除路径前缀下的所有节点（用于目录删除时递归逻辑删除子节点）
   *
   * @param pathPrefix 路径前缀
   * @param excludeId 需要排除的节点ID（目录自身已单独删除）
   * @return 受影响行数
   */
  @Update(
      "UPDATE nw_file_node SET deleted = 1, deleted_time = NOW(), updated_at = NOW() "
          + "WHERE path LIKE CONCAT(#{pathPrefix}, '%') AND deleted = 0 AND id <> #{excludeId}")
  int batchSoftDeleteByPathPrefix(
      @Param("pathPrefix") String pathPrefix, @Param("excludeId") String excludeId);

  /** 逻辑删除（设置 deleted=1 + deleted_time） */
  @Update(
      "UPDATE nw_file_node SET deleted = 1, deleted_time = NOW(), "
          + "original_path = #{originalPath}, updated_at = NOW() WHERE id = #{id}")
  int softDelete(@Param("id") String id, @Param("originalPath") String originalPath);

  /** 恢复逻辑删除 */
  @Update(
      "UPDATE nw_file_node SET deleted = 0, deleted_time = NULL, updated_at = NOW() WHERE id = #{id}")
  int restore(@Param("id") String id);

  /** 更新大小 */
  @Update("UPDATE nw_file_node SET size = size + #{sizeDelta}, updated_at = NOW() WHERE id = #{id}")
  int updateSize(@Param("id") String id, @Param("sizeDelta") Long sizeDelta);

  /** 搜索文件名（LIKE） */
  List<FileNode> searchByName(
      @Param("keyword") String keyword,
      @Param("createdBy") String createdBy,
      @Param("tenantId") String tenantId);

  /** 查询用户根目录 */
  FileNode selectRootByUser(
      @Param("createdBy") String createdBy, @Param("tenantId") String tenantId);

  /** 统计用户文件数量 */
  @Select(
      "SELECT COUNT(*) FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file'")
  int countByUser(@Param("userId") String userId);

  /** 统计用户文件夹数量 */
  @Select(
      "SELECT COUNT(*) FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'folder'")
  int countFoldersByUser(@Param("userId") String userId);

  /** 查询用户文件总大小 */
  @Select(
      "SELECT COALESCE(SUM(size), 0) FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file'")
  Long sumSizeByUser(@Param("userId") String userId);

  /** 查询用户大文件 Top-N */
  @Select(
      "SELECT * FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file' "
          + "ORDER BY size DESC LIMIT #{limit}")
  List<FileNode> findTopLargeFilesByUser(@Param("userId") String userId, @Param("limit") int limit);

  /** 按后缀统计文件数量和大小 */
  @Select(
      "SELECT suffix, COUNT(*) AS file_count, COALESCE(SUM(size), 0) AS total_size "
          + "FROM nw_file_node WHERE created_by = #{userId} AND deleted = 0 AND node_type = 'file' "
          + "GROUP BY suffix ORDER BY total_size DESC")
  List<FileNodeRepository.FileTypeStat> statsBySuffixAndUser(@Param("userId") String userId);

  /** 按文件哈希查询（用于秒传去重） */
  @Select(
      "SELECT * FROM nw_file_node WHERE file_hash = #{fileHash} "
          + "AND tenant_id = #{tenantId} AND deleted = 0 AND node_type = 'file' LIMIT 1")
  FileNode findByFileHash(@Param("fileHash") String fileHash, @Param("tenantId") String tenantId);

  /** 按 createdBy + parentId 查询同名文件 */
  @Select(
      "SELECT * FROM nw_file_node WHERE name = #{name} AND parent_id = #{parentId} "
          + "AND created_by = #{createdBy} AND tenant_id = #{tenantId} AND deleted = 0")
  List<FileNode> findByNameAndParent(
      @Param("name") String name,
      @Param("parentId") String parentId,
      @Param("createdBy") String createdBy,
      @Param("tenantId") String tenantId);

  /**
   * 查询文件夹下全部后代节点（按路径前缀匹配，不含文件夹自身）。
   *
   * <p>利用 LIKE 前缀匹配：路径为 {@code /root/documents/} 时， 所有后代节点路径均以该前缀开头。结果按 level 升序、sort 升序排列。
   *
   * @param folderPath 文件夹路径（需以 {@code /} 结尾）
   * @return 全部后代节点列表，按层级升序排列
   */
  @Select(
      "SELECT * FROM nw_file_node WHERE path LIKE CONCAT(#{folderPath}, '%') "
          + "AND deleted = 0 ORDER BY level ASC, sort ASC")
  List<FileNode> selectAllDescendantsByPath(@Param("folderPath") String folderPath);

  /**
   * 查询冷数据候选（长期未访问的文件）。
   *
   * @param threshold 时间阈值（updated_at 早于此时间的文件）
   * @param excludeSuffixes 排除的后缀（逗号分隔，可为空）
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
}
