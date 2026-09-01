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
import com.njydsz.nextwiki.domain.dto.SpaceDTO;
import com.njydsz.nextwiki.domain.dto.SpaceMemberDTO;
import com.njydsz.nextwiki.domain.dto.SpaceTemplateDTO;
import com.njydsz.nextwiki.domain.dto.StorageQuotaDTO;
import com.njydsz.nextwiki.domain.dto.TagDTO;
import com.njydsz.nextwiki.domain.dto.TrashItemDTO;
import com.njydsz.nextwiki.domain.dto.UserFavoriteDTO;
import com.njydsz.nextwiki.domain.dto.UserRecentDTO;
import com.njydsz.nextwiki.domain.vo.FileAclVO;
import com.njydsz.nextwiki.domain.vo.FileCommentVO;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.vo.FileTagVO;
import com.njydsz.nextwiki.domain.vo.FileVersionVO;
import com.njydsz.nextwiki.domain.vo.SearchIndexVO;
import com.njydsz.nextwiki.domain.vo.ShareAccessLogVO;
import com.njydsz.nextwiki.domain.vo.ShareLinkVO;
import com.njydsz.nextwiki.domain.vo.ShareRecipientVO;
import com.njydsz.nextwiki.domain.vo.SpaceVO;
import com.njydsz.nextwiki.domain.vo.StorageQuotaVO;
import com.njydsz.nextwiki.domain.vo.TagVO;
import com.njydsz.nextwiki.domain.vo.TrashItemVO;
import com.njydsz.nextwiki.infra.entity.FileAcl;
import com.njydsz.nextwiki.infra.entity.FileComment;
import com.njydsz.nextwiki.infra.entity.FileNode;
import com.njydsz.nextwiki.infra.entity.FileTag;
import com.njydsz.nextwiki.infra.entity.FileVersion;
import com.njydsz.nextwiki.infra.entity.SearchIndex;
import com.njydsz.nextwiki.infra.entity.ShareAccessLog;
import com.njydsz.nextwiki.infra.entity.ShareLink;
import com.njydsz.nextwiki.infra.entity.ShareRecipient;
import com.njydsz.nextwiki.infra.entity.Space;
import com.njydsz.nextwiki.infra.entity.SpaceMember;
import com.njydsz.nextwiki.infra.entity.SpaceTemplate;
import com.njydsz.nextwiki.infra.entity.StorageQuota;
import com.njydsz.nextwiki.infra.entity.Tag;
import com.njydsz.nextwiki.infra.entity.TrashItem;
import com.njydsz.nextwiki.infra.entity.UserFavorite;
import com.njydsz.nextwiki.infra.entity.UserRecent;

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

  /** 转换器单例（MapStruct 编译期生成实现类） */
  NextwikiConverter INSTANT = Mappers.getMapper(NextwikiConverter.class);

  // ===== FileNode =====

  /**
   * 文件节点实体 → 文件节点 VO
   *
   * @param entity 文件节点实体
   * @return 文件节点 VO
   */
  FileNodeVO entityToVO(FileNode entity);

  /**
   * 文件节点实体列表 → 文件节点 VO 列表
   *
   * @param entities 文件节点实体列表
   * @return 文件节点 VO 列表
   */
  List<FileNodeVO> fileNodeListToVO(List<FileNode> entities);

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
  FileNode dtoToEntity(FileNodeDTO dto);

  /**
   * 文件节点 DTO（含 ID）→ 文件节点实体（更新场景）
   *
   * @param dto 文件节点 DTO（含 id）
   * @return 文件节点实体
   */
  @Mapping(target = "deleted", ignore = true)
  @Mapping(target = "revision", ignore = true)
  @Mapping(target = "updatedAt", ignore = true)
  FileNode dtoToEntityWithId(FileNodeDTO dto);

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
  List<FileNode> fileNodeDtosToEntities(List<FileNodeDTO> dtos);

  // ===== FileVersion =====

  /**
   * 文件版本实体 → 文件版本 VO
   *
   * @param entity 文件版本实体
   * @return 文件版本 VO
   */
  FileVersionVO entityToVO(FileVersion entity);

  /**
   * 文件版本实体列表 → 文件版本 VO 列表
   *
   * @param entities 文件版本实体列表
   * @return 文件版本 VO 列表
   */
  List<FileVersionVO> fileVersionListToVO(List<FileVersion> entities);

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
  FileVersion dtoToEntity(FileVersionDTO dto);

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
  FileVersion dtoToEntityWithId(FileVersionDTO dto);

  // ===== FileComment =====

  /**
   * 文件评论实体 → 文件评论 VO
   *
   * @param entity 文件评论实体
   * @return 文件评论 VO
   */
  FileCommentVO entityToVO(FileComment entity);

  /**
   * 文件评论实体列表 → 文件评论 VO 列表
   *
   * @param entities 文件评论实体列表
   * @return 文件评论 VO 列表
   */
  List<FileCommentVO> fileCommentListToVO(List<FileComment> entities);

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
  FileComment dtoToEntity(FileCommentDTO dto);

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
  FileComment dtoToEntityWithId(FileCommentDTO dto);

  // ===== FileAcl =====

  /**
   * 文件 ACL 实体 → 文件 ACL VO
   *
   * @param entity 文件 ACL 实体
   * @return 文件 ACL VO
   */
  FileAclVO entityToVO(FileAcl entity);

  /**
   * 文件 ACL 实体列表 → 文件 ACL VO 列表
   *
   * @param entities 文件 ACL 实体列表
   * @return 文件 ACL VO 列表
   */
  List<FileAclVO> fileAclListToVO(List<FileAcl> entities);

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
  FileAcl dtoToEntity(FileAclDTO dto);

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
  FileAcl dtoToEntityWithId(FileAclDTO dto);

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
  List<FileAcl> fileAclDtosToEntities(List<FileAclDTO> dtos);

  // ===== TrashItem =====

  /**
   * 回收站条目实体 → 回收站条目 VO
   *
   * @param entity 回收站条目实体
   * @return 回收站条目 VO
   */
  TrashItemVO entityToVO(TrashItem entity);

  /**
   * 回收站条目实体列表 → 回收站条目 VO 列表
   *
   * @param entities 回收站条目实体列表
   * @return 回收站条目 VO 列表
   */
  List<TrashItemVO> trashItemListToVO(
      List<TrashItem> entities);

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
  TrashItem dtoToEntity(
      TrashItemDTO dto);

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
  TrashItem dtoToEntityWithId(
      TrashItemDTO dto);

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
  List<TrashItem> trashItemDtosToEntities(
      List<TrashItemDTO> dtos);

  // ===== VO → DTO 转换（定时任务使用） =====

  /**
   * 回收站条目 VO → 回收站条目 DTO
   *
   * @param vo 回收站条目 VO
   * @return 回收站条目 DTO
   */
  TrashItemDTO voToTrashItemDTO(
      TrashItemVO vo);

  /**
   * 回收站条目 VO 列表 → 回收站条目 DTO 列表
   *
   * @param vos 回收站条目 VO 列表
   * @return 回收站条目 DTO 列表
   */
  List<TrashItemDTO> trashItemListToDTO(
      List<TrashItemVO> vos);

  /**
   * 分享链接 VO → 分享链接 DTO
   *
   * @param vo 分享链接 VO
   * @return 分享链接 DTO
   */
  ShareLinkDTO voToShareLinkDTO(
      ShareLinkVO vo);

  /**
   * 分享链接 VO 列表 → 分享链接 DTO 列表
   *
   * @param vos 分享链接 VO 列表
   * @return 分享链接 DTO 列表
   */
  List<ShareLinkDTO> shareLinkListToDTO(
      List<ShareLinkVO> vos);

// ===== Tag =====

  /**
   * 标签实体 → 标签 VO
   *
   * @param entity 标签实体
   * @return 标签 VO
   */
  TagVO entityToVO(Tag entity);

  /**
   * 标签实体列表 → 标签 VO 列表
   *
   * @param entities 标签实体列表
   * @return 标签 VO 列表
   */
  List<TagVO> tagListToVO(List<Tag> entities);

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
  Tag dtoToEntity(TagDTO dto);

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
  Tag dtoToEntityWithId(TagDTO dto);

  // ===== FileTag =====

  /**
   * 文件-标签关联实体 → 文件-标签关联 VO
   *
   * @param entity 文件-标签关联实体
   * @return 文件-标签关联 VO
   */
  FileTagVO entityToVO(FileTag entity);

  /**
   * 文件-标签关联实体列表 → 文件-标签关联 VO 列表
   *
   * @param entities 文件-标签关联实体列表
   * @return 文件-标签关联 VO 列表
   */
  List<FileTagVO> fileTagListToVO(List<FileTag> entities);

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
  FileTag dtoToEntity(FileTagDTO dto);

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
  FileTag dtoToEntityWithId(FileTagDTO dto);

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
  List<FileTag> fileTagDtosToEntities(List<FileTagDTO> dtos);

  // ===== StorageQuota =====

  /**
   * 存储配额实体 → 存储配额 VO
   *
   * @param entity 存储配额实体
   * @return 存储配额 VO
   */
  StorageQuotaVO entityToVO(StorageQuota entity);

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
  StorageQuota dtoToEntity(StorageQuotaDTO dto);

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
  StorageQuota dtoToEntityWithId(StorageQuotaDTO dto);

  // ===== ShareLink =====

  /**
   * 分享链接实体 → 分享链接 VO
   *
   * @param entity 分享链接实体
   * @return 分享链接 VO
   */
  ShareLinkVO entityToVO(ShareLink entity);

  /**
   * 分享链接实体列表 → 分享链接 VO 列表
   *
   * @param entities 分享链接实体列表
   * @return 分享链接 VO 列表
   */
  List<ShareLinkVO> shareLinkListToVO(List<ShareLink> entities);

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
  ShareLink dtoToEntity(ShareLinkDTO dto);

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
  ShareLink dtoToEntityWithId(ShareLinkDTO dto);

  // ===== ShareRecipient =====

  /**
   * 分享目标用户实体 → 分享目标用户 VO
   *
   * @param entity 分享目标用户实体
   * @return 分享目标用户 VO
   */
  ShareRecipientVO entityToVO(ShareRecipient entity);

  /**
   * 分享目标用户实体列表 → 分享目标用户 VO 列表
   *
   * @param entities 分享目标用户实体列表
   * @return 分享目标用户 VO 列表
   */
  List<ShareRecipientVO> shareRecipientListToVO(List<ShareRecipient> entities);

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
  ShareRecipient dtoToEntity(ShareRecipientDTO dto);

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
  List<ShareRecipient> shareRecipientDtosToEntities(List<ShareRecipientDTO> dtos);

  // ===== ShareAccessLog =====

  /**
   * 分享访问日志实体 → 分享访问日志 VO
   *
   * @param entity 分享访问日志实体
   * @return 分享访问日志 VO
   */
  ShareAccessLogVO entityToVO(ShareAccessLog entity);

  /**
   * 分享访问日志实体列表 → 分享访问日志 VO 列表
   *
   * @param entities 分享访问日志实体列表
   * @return 分享访问日志 VO 列表
   */
  List<ShareAccessLogVO> shareAccessLogListToVO(List<ShareAccessLog> entities);

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
  ShareAccessLog dtoToEntity(ShareAccessLogDTO dto);

  // ===== SearchIndex =====

  /**
   * 搜索索引实体 → 搜索索引 VO
   *
   * @param entity 搜索索引实体
   * @return 搜索索引 VO
   */
  SearchIndexVO entityToVO(SearchIndex entity);

  /**
   * 搜索索引实体列表 → 搜索索引 VO 列表
   *
   * @param entities 搜索索引实体列表
   * @return 搜索索引 VO 列表
   */
  List<SearchIndexVO> searchIndexListToVO(List<SearchIndex> entities);

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
  SearchIndex dtoToEntity(SearchIndexDTO dto);

  // ===== UserFavorite / UserRecent（S2-P1-06） =====

  /**
   * 用户收藏夹实体 → 用户收藏夹 DTO
   *
   * @param entity 用户收藏夹实体
   * @return 用户收藏夹 DTO
   */
  UserFavoriteDTO toUserFavoriteDTO(
      UserFavorite entity);

  /**
   * 用户收藏夹 DTO → 用户收藏夹实体
   *
   * @param dto 用户收藏夹 DTO
   * @return 用户收藏夹实体
   */
  UserFavorite toUserFavorite(
      UserFavoriteDTO dto);

  /**
   * 用户最近访问实体 → 用户最近访问 DTO
   *
   * @param entity 用户最近访问实体
   * @return 用户最近访问 DTO
   */
  UserRecentDTO toUserRecentDTO(
      UserRecent entity);

  /**
   * 用户最近访问 DTO → 用户最近访问实体
   *
   * @param dto 用户最近访问 DTO
   * @return 用户最近访问实体
   */
  UserRecent toUserRecent(
      UserRecentDTO dto);

  // ===== Space / SpaceMember（S3-P2-01） =====

  /**
   * 空间 DTO → 空间实体
   *
   * @param dto 空间 DTO
   * @return 空间实体
   */
  Space toSpace(
      SpaceDTO dto);

  /**
   * 空间实体 → 空间 DTO
   *
   * @param entity 空间实体
   * @return 空间 DTO
   */
  SpaceDTO toSpaceDTO(
      Space entity);

  /**
   * 空间实体 → 空间 VO
   *
   * @param entity 空间实体
   * @return 空间 VO
   */
  SpaceVO entityToVO(Space entity);

  /**
   * 空间实体列表 → 空间 VO 列表
   *
   * @param entities 空间实体列表
   * @return 空间 VO 列表
   */
  List<SpaceVO> spaceListToVO(List<Space> entities);

  /**
   * 空间成员 DTO → 空间成员实体
   *
   * @param dto 空间成员 DTO
   * @return 空间成员实体
   */
  SpaceMember toSpaceMember(
      SpaceMemberDTO dto);

  /**
   * 空间成员实体 → 空间成员 DTO
   *
   * @param entity 空间成员实体
   * @return 空间成员 DTO
   */
  SpaceMemberDTO toSpaceMemberDTO(
      SpaceMember entity);

  // ===== SpaceTemplate（S4-P3-02） =====

  /**
   * 空间模板实体 → 空间模板 DTO
   *
   * @param entity 空间模板实体
   * @return 空间模板 DTO
   */
  SpaceTemplateDTO toSpaceTemplateDTO(
      SpaceTemplate entity);

  /**
   * 空间模板 DTO → 空间模板实体
   *
   * @param dto 空间模板 DTO
   * @return 空间模板实体
   */
  SpaceTemplate toSpaceTemplate(
      SpaceTemplateDTO dto);
}
