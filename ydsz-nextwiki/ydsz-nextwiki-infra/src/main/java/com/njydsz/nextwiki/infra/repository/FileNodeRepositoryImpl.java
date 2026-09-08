package com.njydsz.nextwiki.infra.repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.context.TenantContextHolder;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.FileNodeDTO;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.query.FileNodeQuery;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.FileStatVO;
import com.njydsz.nextwiki.infra.mapper.FileNodeMapper;

/**
 * 文件节点仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 * <li>通过 {@link NextwikiStructMapper} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiStructMapper} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class FileNodeRepositoryImpl implements FileNodeRepository {

  /** 分布式 ID 生成器（Snowflake 算法，生成节点唯一 ID） */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /** 文件节点 MyBatis Mapper（数据库 CRUD 原始操作） */
  private final FileNodeMapper fileNodeMapper;

  /** DTO/VO/DO 转换器（实体与视图对象之间的映射） */
  private final NextwikiStructMapper mapper;

  @Override
  public Optional<FileNodeVO> findById(String id) {
    return Optional.ofNullable(fileNodeMapper.selectById(id)).map(mapper::fileNodeToVO);
  }

  @Override
  public List<FileNodeVO> findChildren(String parentId) {
    return mapper.fileNodeListToVO(
        fileNodeMapper.selectChildren(parentId, TenantContextHolder.getTenantId()));
  }

  @Override
  public PageResponse<List<FileNodeVO>> findPageChildren(FileNodeQuery query) {
    Page<FileNode> pageParam = new Page<>(query.getPage(), query.getPageSize());
    IPage<FileNode> result =
        fileNodeMapper.selectPageByParentId(
            pageParam,
            query.getParentId(),
            query.getNodeType(),
            query.getSortBy(),
            query.getSortDir(),
            TenantContextHolder.getTenantId());
    List<FileNodeVO> vos = mapper.fileNodeListToVO(result.getRecords());
    Page<FileNodeVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
    voPage.setRecords(vos);
    return PageResponses.success(voPage);
  }

  /**
   * 按路径前缀查找文件节点（用于目录树遍历）。
   *
   * @param pathPrefix 路径前缀
   * @return 文件节点视图列表
   */
  @Override
  public List<FileNodeVO> findByPathPrefix(String pathPrefix) {
    return mapper.fileNodeListToVO(
        fileNodeMapper.selectByPathPrefix(pathPrefix, TenantContextHolder.getTenantId()));
  }

  /**
   * 批量更新路径前缀（移动文件夹时更新所有子节点路径）。
   *
   * @param oldPathPrefix 原路径前缀
   * @param newPathPrefix 新路径前缀
   * @param levelDelta 层级变化量
   * @param excludeId 排除的节点 ID（通常是移动的根节点本身）
   * @return 更新记录数
   */
  @Override
  public int batchUpdatePathPrefix(
      String oldPathPrefix, String newPathPrefix, int levelDelta, String excludeId) {
    return fileNodeMapper.batchUpdatePathPrefix(
        oldPathPrefix, newPathPrefix, levelDelta, excludeId, TenantContextHolder.getTenantId());
  }

  /**
   * 按路径前缀批量软删除（文件夹删除时级联软删除子节点）。
   *
   * @param pathPrefix 路径前缀
   * @param excludeId 排除的节点 ID
   * @return 更新记录数
   */
  @Override
  public int batchSoftDeleteByPathPrefix(String pathPrefix, String excludeId) {
    return fileNodeMapper.batchSoftDeleteByPathPrefix(
        pathPrefix, excludeId, TenantContextHolder.getTenantId());
  }

  /**
   * 保存文件节点（新增首版本或新建目录）。
   *
   * @param dto 文件节点数据传输对象
   * @return 保存后的文件节点视图对象
   */
  @Override
  public FileNodeVO save(FileNodeDTO dto) {
    FileNode entity = mapper.fileNodeToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    fileNodeMapper.insert(entity);
    return mapper.fileNodeToVO(entity);
  }

  /**
   * 批量新增文件节点（带审计字段回填）。
   *
   * @param dtos 文件节点数据传输对象列表
   * @return 插入记录数
   */
  @Override
  public int saveBatch(List<FileNodeDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return 0;
    }
    List<FileNode> entities = mapper.fileNodeListToEntity(dtos);
    LocalDateTime now = LocalDateTime.now();
    String tenantId = TenantContextHolder.getTenantId();
    for (FileNode entity : entities) {
      if (entity.getId() == null || entity.getId().isEmpty()) {
        entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
      }
      // P1-3: XML 批量插入绕过 MP 自动填充，此处手动补全审计字段
      if (entity.getStatus() == null) {
        entity.setStatus("active");
      }
    if (entity.getIsDeleted() == null) {
      entity.setIsDeleted(false);
    }
      if (entity.getRevision() == null) {
        entity.setRevision(0);
      }
      if (entity.getCreatedAt() == null) {
        entity.setCreatedAt(now);
      }
      if (entity.getUpdatedAt() == null) {
        entity.setUpdatedAt(now);
      }
      if (entity.getTenantId() == null) {
        entity.setTenantId(tenantId);
      }
    }
    return fileNodeMapper.insertBatch(entities);
  }

  /**
   * 更新文件节点（含乐观锁版本控制）。
   *
   * @param dto 文件节点数据传输对象（带 revision 时走乐观锁更新）
   * @throws org.springframework.dao.OptimisticLockingFailureException 版本不一致时抛出
   */
  @Override
  public void update(FileNodeDTO dto) {
    FileNode entity = mapper.fileNodeToEntity(dto);
    if (entity.getRevision() == null) {
      fileNodeMapper.updateById(entity);
      return;
    }
    int affected = fileNodeMapper.updateWithRevision(entity);
    if (affected == 0) {
      throw new OptimisticLockingFailureException(
          "FileNode 乐观锁更新失败，id=" + entity.getId() + ", revision=" + entity.getRevision());
    }
    entity.setRevision(entity.getRevision() + 1);
  }

  /**
   * 软删除文件节点（移入回收站）。
   *
   * @param id 节点 ID
   * @param originalPath 原始路径（恢复时使用）
   */
  @Override
  public void softDelete(String id, String originalPath) {
    fileNodeMapper.softDelete(id, originalPath);
  }

  /**
   * 恢复软删除的文件节点（从回收站还原）。
   *
   * @param id 节点 ID
   */
  @Override
  public void restore(String id) {
    fileNodeMapper.restore(id);
  }

  /**
   * 物理删除文件节点（永久删除）。
   *
   * @param id 节点 ID
   */
  @Override
  public void physicalDelete(String id) {
    fileNodeMapper.deleteById(id);
  }

  /**
   * 按 ID 列表批量查询文件节点。
   *
   * @param ids 节点 ID 列表
   * @return 文件节点视图列表
   */
  @Override
  public List<FileNodeVO> findByIds(List<String> ids) {
    return mapper.fileNodeListToVO(fileNodeMapper.selectBatchIds(ids));
  }

  /**
   * 批量软删除（批量删除时优化性能）。
   *
   * @param ids 节点 ID 列表
   * @param originalPaths 原始路径列表（用于恢复）
   * @return 更新记录数
   */
  @Override
  public int batchSoftDelete(List<String> ids, List<String> originalPaths) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    return fileNodeMapper.batchSoftDelete(ids, originalPaths);
  }

  /**
   * 批量更新节点的父目录和路径（批量移动）。
   *
   * @param ids 节点 ID 列表
   * @param targetParentId 目标父目录 ID
   * @param newPaths 新路径列表
   * @param levelDeltas 层级变化量列表
   * @return 更新记录数
   */
  @Override
  public int batchUpdateParentAndPath(
      List<String> ids, String targetParentId, List<String> newPaths, List<Integer> levelDeltas) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    return fileNodeMapper.batchUpdateParentAndPath(ids, targetParentId, newPaths, levelDeltas);
  }

  /**
   * 更新文件大小（上传/删除文件中的版本时调用）。
   *
   * @param id 节点 ID
   * @param sizeDelta 大小变化量（字节）
   */
  @Override
  public void updateSize(String id, Long sizeDelta) {
    fileNodeMapper.updateSize(id, sizeDelta);
  }

  /**
   * 统计用户拥有的文件节点总数。
   *
   * @param userId 用户 ID
   * @return 文件节点数量
   */
  @Override
  public int countByUser(String userId) {
    return fileNodeMapper.countByUser(userId);
  }

  /**
   * 统计用户拥有的文件夹数量。
   *
   * @param userId 用户 ID
   * @return 文件夹数量
   */
  @Override
  public int countFoldersByUser(String userId) {
    return fileNodeMapper.countFoldersByUser(userId);
  }

  /**
   * 统计用户文件总大小（存储分析使用）。
   *
   * @param userId 用户 ID
   * @return 文件总大小（字节）
   */
  @Override
  public long sumSizeByUser(String userId) {
    Long sum = fileNodeMapper.sumSizeByUser(userId);
    return sum != null ? sum : 0L;
  }

  /**
   * 查询用户最大的 N 个文件（存储分析使用）。
   *
   * @param userId 用户 ID
   * @param limit 返回数量上限
   * @return 文件节点视图列表
   */
  @Override
  public List<FileNodeVO> findTopLargeFilesByUser(String userId, int limit) {
    return mapper.fileNodeListToVO(fileNodeMapper.findTopLargeFilesByUser(userId, limit));
  }

  /**
   * 按文件后缀分组统计用户的文件数量与大小。
   *
   * @param userId 用户 ID
   * @return 文件统计视图列表（含后缀、数量、总大小）
   */
  @Override
  public List<FileStatVO> statsBySuffixAndUser(String userId) {
    return fileNodeMapper.statsBySuffixAndUser(userId);
  }

  /**
   * 查询或创建用户的根目录（首次访问时创建）。
   *
   * @param userId 用户 ID
   * @return 根目录节点视图
   */
  @Override
  public FileNodeVO findOrCreateRoot(String userId) {
    FileNode root = fileNodeMapper.selectRootByUser(userId, TenantContextHolder.getTenantId());
    if (root != null) {
      return mapper.fileNodeToVO(root);
    }

    root =
        FileNode.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()))
            .parentId("0")
            .name("root")
            .nodeType(FileNode.TYPE_FOLDER)
            .size(0L)
            .path("/")
            .level(0)
            .sort(0)
            .currentVersion(0)
.isPreviewReady(false)
.isStarred(false)
            .shareStatus("private")
            .status("active")
            .tenantId(TenantContextHolder.getTenantId())
            .isDeleted(false)
            .revision(0)
            .build();

    root.setCreatedBy(userId);
    root.setUpdatedBy(userId);

    fileNodeMapper.insert(root);
    log.info("[FileNodeRepositoryImpl] 创建用户根目录: userId={}, rootId={}", userId, root.getId());
    return mapper.fileNodeToVO(root);
  }

  /**
   * 按文件哈希查询（秒传去重）。
   *
   * @param fileHash 文件 SHA-256 哈希
   * @return 文件节点视图对象（可能为空）
   */
  @Override
  public Optional<FileNodeVO> findByFileHash(String fileHash) {
    if (fileHash == null || fileHash.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(
            fileNodeMapper.findByFileHash(fileHash, TenantContextHolder.getTenantId()))
        .map(mapper::fileNodeToVO);
  }

  /**
   * 按文件名和父目录查询（同目录查重）。
   *
   * @param name 文件名
   * @param parentId 父目录 ID
   * @param createdBy 创建人 ID
   * @return 文件节点视图列表
   */
  @Override
  public List<FileNodeVO> findByNameAndParent(String name, String parentId, String createdBy) {
    return mapper.fileNodeListToVO(
        fileNodeMapper.findByNameAndParent(
            name, parentId, createdBy, TenantContextHolder.getTenantId()));
  }

  /**
   * 分页查询目录下所有后代文件（不包含目录本身）。
   *
   * @param folderPath 目录路径前缀
   * @param offset 分页偏移量
   * @param limit 每页条数
   * @return 文件节点视图列表
   */
  @Override
  public List<FileNodeVO> findDescendantsByPage(String folderPath, int offset, int limit) {
    if (folderPath == null || folderPath.isEmpty()) {
      return new ArrayList<>(0);
    }
    List<FileNode> list =
        fileNodeMapper.selectDescendantsByPage(
            folderPath, offset, limit, TenantContextHolder.getTenantId());
    return list == null ? Collections.emptyList() : mapper.fileNodeListToVO(list);
  }

  /**
   * 统计目录下所有后代的文件数量。
   *
   * @param folderPath 目录路径前缀
   * @return 后代文件数量
   */
  @Override
  public int countDescendants(String folderPath) {
    if (folderPath == null || folderPath.isEmpty()) {
      return 0;
    }
    return fileNodeMapper.countDescendantsByPath(folderPath, TenantContextHolder.getTenantId());
  }

  /**
   * 查询目录下所有后代文件（包含所有层级）。
   *
   * @param folderId 目录节点 ID
   * @return 后代文件节点视图列表
   */
  @Override
  public List<FileNodeVO> findAllDescendants(String folderId) {
    if (folderId == null || folderId.isEmpty()) {
      return new ArrayList<>(0);
    }
    FileNode folder = fileNodeMapper.selectById(folderId);
    if (folder == null || folder.getPath() == null) {
      return new ArrayList<>(0);
    }
    return mapper.fileNodeListToVO(
        fileNodeMapper.selectAllDescendantsByPath(folder.getPath(), TenantContextHolder.getTenantId()));
  }

  /**
   * 查询冷数据候选文件（用于归档）。
   *
   * @param threshold 时间阈值（早于该时间的文件为冷数据）
   * @param excludeSuffixes 排除的后缀列表（逗号分隔）
   * @param limit 返回数量上限
   * @return 冷数据候选文件视图列表
   */
  @Override
  public List<FileNodeVO> findColdCandidates(LocalDateTime threshold, String excludeSuffixes, int limit) {
    List<String> excludeSuffixesList = null;
    if (excludeSuffixes != null && !excludeSuffixes.isEmpty()) {
      excludeSuffixesList = List.of(excludeSuffixes.split(","));
    }
    return mapper.fileNodeListToVO(
        fileNodeMapper.selectColdCandidates(threshold, excludeSuffixes, excludeSuffixesList, limit));
  }

  /**
   * 统计冷数据文件总大小（用于归档分析）。
   *
   * @param threshold 时间阈值
   * @return 冷数据节点数量
   */
  @Override
  public long countColdNodes(LocalDateTime threshold) {
    return fileNodeMapper.countColdNodes(threshold);
  }

  /**
   * 分页查询所有文件节点（运维管理用）。
   *
   * @param offset 分页偏移量
   * @param limit 每页条数
   * @return 分页文件节点视图列表
   */
  @Override
  public PageResponse<List<FileNodeVO>> findAllWithPage(int offset, int limit) {
    Page<FileNode> pageParam = new Page<>(offset / limit + 1, limit);
    IPage<FileNode> result = fileNodeMapper.selectAllWithPage(pageParam);
    List<FileNodeVO> vos = mapper.fileNodeListToVO(result.getRecords());
    Page<FileNodeVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
    voPage.setRecords(vos);
    return PageResponses.success(voPage);
  }
}

