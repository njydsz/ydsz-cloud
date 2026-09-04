package com.njydsz.nextwiki.infra.repository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.jdbc.support.PageResponses;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.FileNodeDTO;
import com.njydsz.nextwiki.domain.query.FileNodeQuery;
import com.njydsz.nextwiki.domain.repository.FileNodeRepository;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.FileStatVO;
import com.njydsz.nextwiki.domain.converter.NextwikiConverter;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.infra.mapper.FileNodeMapper;

/**
 * 文件节点仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link NextwikiConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiConverter} 转换为 DO 后执行数据库操作
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
  private final NextwikiConverter converter;

  @Override
  public Optional<FileNodeVO> findById(String id) {
    return Optional.ofNullable(fileNodeMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public List<FileNodeVO> findChildren(String parentId) {
    return converter.fileNodeListToVO(
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
    List<FileNodeVO> vos = converter.fileNodeListToVO(result.getRecords());
    Page<FileNodeVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
    voPage.setRecords(vos);
    return PageResponses.success(voPage);
  }

  @Override
  public List<FileNodeVO> findByPathPrefix(String pathPrefix) {
    return converter.fileNodeListToVO(
        fileNodeMapper.selectByPathPrefix(pathPrefix, TenantContextHolder.getTenantId()));
  }

  @Override
  public int batchUpdatePathPrefix(
      String oldPathPrefix, String newPathPrefix, int levelDelta, String excludeId) {
    return fileNodeMapper.batchUpdatePathPrefix(
        oldPathPrefix, newPathPrefix, levelDelta, excludeId, TenantContextHolder.getTenantId());
  }

  @Override
  public int batchSoftDeleteByPathPrefix(String pathPrefix, String excludeId) {
    return fileNodeMapper.batchSoftDeleteByPathPrefix(
        pathPrefix, excludeId, TenantContextHolder.getTenantId());
  }

  @Override
  public FileNodeVO save(FileNodeDTO dto) {
    FileNode entity = converter.dtoToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    fileNodeMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public int saveBatch(List<FileNodeDTO> dtos) {
    if (dtos == null || dtos.isEmpty()) {
      return 0;
    }
    List<FileNode> entities = converter.fileNodeDtosToEntities(dtos);
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
      if (entity.getDeleted() == null) {
        entity.setDeleted(0);
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

  @Override
  public void update(FileNodeDTO dto) {
    FileNode entity = converter.dtoToEntityWithId(dto);
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

  @Override
  public void softDelete(String id, String originalPath) {
    fileNodeMapper.softDelete(id, originalPath);
  }

  @Override
  public void restore(String id) {
    fileNodeMapper.restore(id);
  }

  @Override
  public void physicalDelete(String id) {
    fileNodeMapper.deleteById(id);
  }

  @Override
  public List<FileNodeVO> findByIds(List<String> ids) {
    return converter.fileNodeListToVO(fileNodeMapper.selectBatchIds(ids));
  }

  @Override
  public int batchSoftDelete(List<String> ids, List<String> originalPaths) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    return fileNodeMapper.batchSoftDelete(ids, originalPaths);
  }

  @Override
  public int batchUpdateParentAndPath(
      List<String> ids, String targetParentId, List<String> newPaths, List<Integer> levelDeltas) {
    if (ids == null || ids.isEmpty()) {
      return 0;
    }
    return fileNodeMapper.batchUpdateParentAndPath(ids, targetParentId, newPaths, levelDeltas);
  }

  @Override
  public void updateSize(String id, Long sizeDelta) {
    fileNodeMapper.updateSize(id, sizeDelta);
  }

  @Override
  public int countByUser(String userId) {
    return fileNodeMapper.countByUser(userId);
  }

  @Override
  public int countFoldersByUser(String userId) {
    return fileNodeMapper.countFoldersByUser(userId);
  }

  @Override
  public long sumSizeByUser(String userId) {
    Long sum = fileNodeMapper.sumSizeByUser(userId);
    return sum != null ? sum : 0L;
  }

  @Override
  public List<FileNodeVO> findTopLargeFilesByUser(String userId, int limit) {
    return converter.fileNodeListToVO(fileNodeMapper.findTopLargeFilesByUser(userId, limit));
  }

  @Override
  public List<FileStatVO> statsBySuffixAndUser(String userId) {
    return fileNodeMapper.statsBySuffixAndUser(userId);
  }

  @Override
  public FileNodeVO findOrCreateRoot(String userId) {
    FileNode root = fileNodeMapper.selectRootByUser(userId, TenantContextHolder.getTenantId());
    if (root != null) {
      return converter.entityToVO(root);
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
            .previewReady(false)
            .starred(false)
            .shareStatus("private")
            .status("active")
            .tenantId(TenantContextHolder.getTenantId())
            .deleted(0)
            .revision(0)
            .build();

    root.setCreatedBy(userId);
    root.setUpdatedBy(userId);

    fileNodeMapper.insert(root);
    log.info("[FileNodeRepositoryImpl] 创建用户根目录: userId={}, rootId={}", userId, root.getId());
    return converter.entityToVO(root);
  }

  @Override
  public Optional<FileNodeVO> findByFileHash(String fileHash) {
    if (fileHash == null || fileHash.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(
            fileNodeMapper.findByFileHash(fileHash, TenantContextHolder.getTenantId()))
        .map(converter::entityToVO);
  }

  @Override
  public List<FileNodeVO> findByNameAndParent(String name, String parentId, String createdBy) {
    return converter.fileNodeListToVO(
        fileNodeMapper.findByNameAndParent(
            name, parentId, createdBy, TenantContextHolder.getTenantId()));
  }

  @Override
  public List<FileNodeVO> findDescendantsByPage(String folderPath, int offset, int limit) {
    if (folderPath == null || folderPath.isEmpty()) {
      return new ArrayList<>(0);
}
