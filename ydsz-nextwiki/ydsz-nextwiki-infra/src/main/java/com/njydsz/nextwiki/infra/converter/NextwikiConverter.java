package com.njydsz.nextwiki.infra.converter;

import java.util.List;

import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import com.njydsz.nextwiki.domain.dto.FileAclDTO;
import com.njydsz.nextwiki.domain.dto.FileCommentDTO;
import com.njydsz.nextwiki.domain.dto.FileNodeDTO;
import com.njydsz.nextwiki.domain.dto.FileTagDTO;
import com.njydsz.nextwiki.domain.dto.FileVersionDTO;
import com.njydsz.nextwiki.domain.dto.SearchIndexDTO;
import com.njydsz.nextwiki.domain.dto.ShareAccessLogDTO;
import com.njydsz.nextwiki.domain.dto.ShareLinkDTO;
import com.njydsz.nextwiki.domain.dto.ShareRecipientDTO;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;
import com.njydsz.nextwiki.domain.dto.TagDTO;
import com.njydsz.nextwiki.domain.vo.FileAclVO;
import com.njydsz.nextwiki.domain.vo.FileCommentVO;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;
import com.njydsz.nextwiki.domain.vo.SearchIndexVO;
import com.njydsz.nextwiki.domain.vo.ShareAccessLogVO;
import com.njydsz.nextwiki.domain.vo.ShareLinkVO;
import com.njydsz.nextwiki.domain.vo.ShareRecipientVO;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;
import com.njydsz.nextwiki.domain.vo.TagVO;
import com.njydsz.nextwiki.domain.vo.FileTagVO;
import com.njydsz.nextwiki.infra.entity.FileAclDO;
import com.njydsz.nextwiki.infra.entity.FileCommentDO;
import com.njydsz.nextwiki.infra.entity.FileNodeDO;
import com.njydsz.nextwiki.infra.entity.FileTagDO;
import com.njydsz.nextwiki.infra.entity.FileVersionDO;
import com.njydsz.nextwiki.infra.entity.SearchIndexDO;
import com.njydsz.nextwiki.infra.entity.ShareAccessLogDO;
import com.njydsz.nextwiki.infra.entity.ShareLinkDO;
import com.njydsz.nextwiki.infra.entity.ShareRecipientDO;
import com.njydsz.nextwiki.infra.entity.StorageQuotaDO;
import com.njydsz.nextwiki.infra.entity.TagDO;

/**
 * Nextwiki 模块统一 MapStruct 转换器
 *
 * <p>承担 Nextwiki 模块所有 Entity ↔ VO / DTO → Entity 的双向转换。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类
 *   <li>通过 {@link #INSTANT} 单例访问
 *   <li>同名字段自动映射；不同名字段通过 {@code @Mapping} 显式标注
 *   <li>MpBaseEntity 自动填充字段通过 {@code @Mapping(ignore = true)} 忽略
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface NextwikiConverter {

  NextwikiConverter INSTANT = Mappers.getMapper(NextwikiConverter.class);

  // ===== FileNode =====

  /**
   * 文件节点实体 → 文件节点 VO
   *
   * @param entity 文件节点实体
   * @return 文件节点 VO
   */
  FileNodeVO entityToVO(FileNodeDO entity);

  /**
   * 文件节点实体列表 → 文件节点 VO 列表
   *
   * @param entities 文件节点实体列表
   * @return 文件节点 VO 列表
   */
  List<FileNodeVO> fileNodeListToVO(List<FileNodeDO> entities);

  /**
   * 文件节点 DTO → 文件节点实体（创建场景）
   *
   * @param dto 文件节点 DTO
   * @return 文件节点实体
   */
  @Named("create")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FileNodeDO dtoToEntity(FileNodeDTO dto);

  /**
   * 文件节点 DTO（含 ID）→ 文件节点实体（更新场景）
   *
   * @param dto 文件节点 DTO（含 id）
   * @return 文件节点实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FileNodeDO dtoToEntityWithId(FileNodeDTO dto);

  /**
   * 文件节点 DTO 列表 → 文件节点实体列表
   *
   * @param dtos 文件节点 DTO 列表
   * @return 文件节点实体列表
   */
  @IterableMapping(qualifiedByName = "create")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  List<FileNodeDO> fileNodeDtosToEntities(List<FileNodeDTO> dtos);

  // ===== FileVersion =====

  /**
   * 文件版本实体 → 文件版本 VO
   *
   * @param entity 文件版本实体
   * @return 文件版本 VO
   */
  FileVersionVO entityToVO(FileVersionDO entity);

  /**
   * 文件版本实体列表 → 文件版本 VO 列表
   *
   * @param entities 文件版本实体列表
   * @return 文件版本 VO 列表
   */
  List<FileVersionVO> fileVersionListToVO(List<FileVersionDO> entities);

  /**
   * 文件版本 DTO → 文件版本实体（创建场景）
   *
   * @param dto 文件版本 DTO
   * @return 文件版本实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FileVersionDO dtoToEntity(FileVersionDTO dto);

  /**
   * 文件版本 DTO（含 ID）→ 文件版本实体（更新场景）
   *
   * @param dto 文件版本 DTO（含 id）
   * @return 文件版本实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FileVersionDO dtoToEntityWithId(FileVersionDTO dto);

  // ===== FileComment =====

  /**
   * 文件评论实体 → 文件评论 VO
   *
   * @param entity 文件评论实体
   * @return 文件评论 VO
   */
  FileCommentVO entityToVO(FileCommentDO entity);

  /**
   * 文件评论实体列表 → 文件评论 VO 列表
   *
   * @param entities 文件评论实体列表
   * @return 文件评论 VO 列表
   */
  List<FileCommentVO> fileCommentListToVO(List<FileCommentDO> entities);

  /**
   * 文件评论 DTO → 文件评论实体（创建场景）
   *
   * @param dto 文件评论 DTO
   * @return 文件评论实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FileCommentDO dtoToEntity(FileCommentDTO dto);

  /**
   * 文件评论 DTO（含 ID）→ 文件评论实体（更新场景）
   *
   * @param dto 文件评论 DTO（含 id）
   * @return 文件评论实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FileCommentDO dtoToEntityWithId(FileCommentDTO dto);

  // ===== FileAcl =====

  /**
   * 文件 ACL 实体 → 文件 ACL VO
   *
   * @param entity 文件 ACL 实体
   * @return 文件 ACL VO
   */
  FileAclVO entityToVO(FileAclDO entity);

  /**
   * 文件 ACL 实体列表 → 文件 ACL VO 列表
   *
   * @param entities 文件 ACL 实体列表
   * @return 文件 ACL VO 列表
   */
  List<FileAclVO> fileAclListToVO(List<FileAclDO> entities);

  /**
   * 文件 ACL DTO → 文件 ACL 实体（创建场景）
   *
   * @param dto 文件 ACL DTO
   * @return 文件 ACL 实体
   */
  @Named("create")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FileAclDO dtoToEntity(FileAclDTO dto);

  /**
   * 文件 ACL DTO（含 ID）→ 文件 ACL 实体（更新场景）
   *
   * @param dto 文件 ACL DTO（含 id）
   * @return 文件 ACL 实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FileAclDO dtoToEntityWithId(FileAclDTO dto);

  /**
   * 文件 ACL DTO 列表 → 文件 ACL 实体列表
   *
   * @param dtos 文件 ACL DTO 列表
   * @return 文件 ACL 实体列表
   */
  @IterableMapping(qualifiedByName = "create")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  List<FileAclDO> fileAclDtosToEntities(List<FileAclDTO> dtos);

  // ===== TrashItem =====

  /**
   * 回收站条目实体 → 回收站条目 VO
   *
   * @param entity 回收站条目实体
   * @return 回收站条目 VO
   */
  com.njydsz.nextwiki.domain.vo.TrashItemVO entityToVO(com.njydsz.nextwiki.infra.entity.TrashItemDO entity);

  /**
   * 回收站条目实体列表 → 回收站条目 VO 列表
   *
   * @param entities 回收站条目实体列表
   * @return 回收站条目 VO 列表
   */
  List<com.njydsz.nextwiki.domain.vo.TrashItemVO> trashItemListToVO(
      List<com.njydsz.nextwiki.infra.entity.TrashItemDO> entities);

  /**
   * 回收站条目 DTO → 回收站条目实体（创建场景）
   *
   * @param dto 回收站条目 DTO
   * @return 回收站条目实体
   */
  @Named("create")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  com.njydsz.nextwiki.infra.entity.TrashItemDO dtoToEntity(
      com.njydsz.nextwiki.domain.dto.TrashItemDTO dto);

  /**
   * 回收站条目 DTO（含 ID）→ 回收站条目实体（更新场景）
   *
   * @param dto 回收站条目 DTO（含 id）
   * @return 回收站条目实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  com.njydsz.nextwiki.infra.entity.TrashItemDO dtoToEntityWithId(
      com.njydsz.nextwiki.domain.dto.TrashItemDTO dto);

  /**
   * 回收站条目 DTO 列表 → 回收站条目实体列表（批量创建场景）
   *
   * @param dtos 回收站条目 DTO 列表
   * @return 回收站条目实体列表
   */
  @IterableMapping(qualifiedByName = "create")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  List<com.njydsz.nextwiki.infra.entity.TrashItemDO> trashItemDtosToEntities(
      List<com.njydsz.nextwiki.domain.dto.TrashItemDTO> dtos);

// ===== Tag =====

  /**
   * 标签实体 → 标签 VO
   *
   * @param entity 标签实体
   * @return 标签 VO
   */
  TagVO entityToVO(TagDO entity);

  /**
   * 标签实体列表 → 标签 VO 列表
   *
   * @param entities 标签实体列表
   * @return 标签 VO 列表
   */
  List<TagVO> tagListToVO(List<TagDO> entities);

  /**
   * 标签 DTO → 标签实体（创建场景）
   *
   * @param dto 标签 DTO
   * @return 标签实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  TagDO dtoToEntity(TagDTO dto);

  /**
   * 标签 DTO（含 ID）→ 标签实体（更新场景）
   *
   * @param dto 标签 DTO（含 id）
   * @return 标签实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  TagDO dtoToEntityWithId(TagDTO dto);

  // ===== FileTag =====

  /**
   * 文件-标签关联实体 → 文件-标签关联 VO
   *
   * @param entity 文件-标签关联实体
   * @return 文件-标签关联 VO
   */
  FileTagVO entityToVO(FileTagDO entity);

  /**
   * 文件-标签关联实体列表 → 文件-标签关联 VO 列表
   *
   * @param entities 文件-标签关联实体列表
   * @return 文件-标签关联 VO 列表
   */
  List<FileTagVO> fileTagListToVO(List<FileTagDO> entities);

  /**
   * 文件-标签关联 DTO → 文件-标签关联实体（创建场景）
   *
   * @param dto 文件-标签关联 DTO
   * @return 文件-标签关联实体
   */
  @Named("create")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FileTagDO dtoToEntity(FileTagDTO dto);

  /**
   * 文件-标签关联 DTO（含 ID）→ 文件-标签关联实体（更新场景）
   *
   * @param dto 文件-标签关联 DTO（含 id）
   * @return 文件-标签关联实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FileTagDO dtoToEntityWithId(FileTagDTO dto);

  /**
   * 文件-标签关联 DTO 列表 → 文件-标签关联实体列表
   *
   * @param dtos 文件-标签关联 DTO 列表
   * @return 文件-标签关联实体列表
   */
  @IterableMapping(qualifiedByName = "create")
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  List<FileTagDO> fileTagDtosToEntities(List<FileTagDTO> dtos);

  // ===== StorageQuota =====

  /**
   * 存储配额实体 → 存储配额 VO
   *
   * @param entity 存储配额实体
   * @return 存储配额 VO
   */
  StorageQuotaVO entityToVO(StorageQuotaDO entity);

  /**
   * 存储配额 DTO → 存储配额实体（创建场景）
   *
   * @param dto 存储配额 DTO
   * @return 存储配额实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  StorageQuotaDO dtoToEntity(StorageQuotaDTO dto);

  /**
   * 存储配额 DTO（含 ID）→ 存储配额实体（更新场景）
   *
   * @param dto 存储配额 DTO（含 id）
   * @return 存储配额实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  StorageQuotaDO dtoToEntityWithId(StorageQuotaDTO dto);

  // ===== ShareLink =====

  /**
   * 分享链接实体 → 分享链接 VO
   *
   * @param entity 分享链接实体
   * @return 分享链接 VO
   */
  ShareLinkVO entityToVO(ShareLinkDO entity);

  /**
   * 分享链接实体列表 → 分享链接 VO 列表
   *
   * @param entities 分享链接实体列表
   * @return 分享链接 VO 列表
   */
  List<ShareLinkVO> shareLinkListToVO(List<ShareLinkDO> entities);

  /**
   * 分享链接 DTO → 分享链接实体（创建场景）
   *
   * @param dto 分享链接 DTO
   * @return 分享链接实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  ShareLinkDO dtoToEntity(ShareLinkDTO dto);

  /**
   * 分享链接 DTO（含 ID）→ 分享链接实体（更新场景）
   *
   * @param dto 分享链接 DTO（含 id）
   * @return 分享链接实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  ShareLinkDO dtoToEntityWithId(ShareLinkDTO dto);

  // ===== ShareRecipient =====

  /**
   * 分享目标用户实体 → 分享目标用户 VO
   *
   * @param entity 分享目标用户实体
   * @return 分享目标用户 VO
   */
  ShareRecipientVO entityToVO(ShareRecipientDO entity);

  /**
   * 分享目标用户实体列表 → 分享目标用户 VO 列表
   *
   * @param entities 分享目标用户实体列表
   * @return 分享目标用户 VO 列表
   */
  List<ShareRecipientVO> shareRecipientListToVO(List<ShareRecipientDO> entities);

  /**
   * 分享目标用户 DTO → 分享目标用户实体（创建场景）
   *
   * @param dto 分享目标用户 DTO
   * @return 分享目标用户实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  ShareRecipientDO dtoToEntity(ShareRecipientDTO dto);

  /**
   * 分享目标用户 DTO 列表 → 分享目标用户实体列表
   *
   * @param dtos 分享目标用户 DTO 列表
   * @return 分享目标用户实体列表
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  List<ShareRecipientDO> shareRecipientDtosToEntities(List<ShareRecipientDTO> dtos);

  // ===== ShareAccessLog =====

  /**
   * 分享访问日志实体 → 分享访问日志 VO
   *
   * @param entity 分享访问日志实体
   * @return 分享访问日志 VO
   */
  ShareAccessLogVO entityToVO(ShareAccessLogDO entity);

  /**
   * 分享访问日志实体列表 → 分享访问日志 VO 列表
   *
   * @param entities 分享访问日志实体列表
   * @return 分享访问日志 VO 列表
   */
  List<ShareAccessLogVO> shareAccessLogListToVO(List<ShareAccessLogDO> entities);

  /**
   * 分享访问日志 DTO → 分享访问日志实体（创建场景）
   *
   * @param dto 分享访问日志 DTO
   * @return 分享访问日志实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  ShareAccessLogDO dtoToEntity(ShareAccessLogDTO dto);

  // ===== SearchIndex =====

  /**
   * 搜索索引实体 → 搜索索引 VO
   *
   * @param entity 搜索索引实体
   * @return 搜索索引 VO
   */
  SearchIndexVO entityToVO(SearchIndexDO entity);

  /**
   * 搜索索引实体列表 → 搜索索引 VO 列表
   *
   * @param entities 搜索索引实体列表
   * @return 搜索索引 VO 列表
   */
  List<SearchIndexVO> searchIndexListToVO(List<SearchIndexDO> entities);

  /**
   * 搜索索引 DTO → 搜索索引实体（创建场景）
   *
   * @param dto 搜索索引 DTO
   * @return 搜索索引实体
   */
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "tenantId", ignore = true)
  @Mapping(target = "createdBy", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "updatedBy", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  SearchIndexDO dtoToEntity(SearchIndexDTO dto);
}
