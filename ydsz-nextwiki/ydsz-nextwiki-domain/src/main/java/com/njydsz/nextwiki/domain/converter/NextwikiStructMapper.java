package com.njydsz.nextwiki.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import org.mapstruct.factory.Mappers;

import com.njydsz.nextwiki.domain.dto.FileAclDTO;
import com.njydsz.nextwiki.domain.dto.FileCommentDTO;
import com.njydsz.nextwiki.domain.dto.FileNodeDTO;
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
import com.njydsz.nextwiki.domain.entity.FileAcl;
import com.njydsz.nextwiki.domain.entity.FileComment;
import com.njydsz.nextwiki.domain.entity.FileNode;
import com.njydsz.nextwiki.domain.entity.FileTag;
import com.njydsz.nextwiki.domain.entity.FileVersion;
import com.njydsz.nextwiki.domain.entity.SearchIndex;
import com.njydsz.nextwiki.domain.entity.ShareAccessLog;
import com.njydsz.nextwiki.domain.entity.ShareLink;
import com.njydsz.nextwiki.domain.entity.ShareRecipient;
import com.njydsz.nextwiki.domain.entity.Space;
import com.njydsz.nextwiki.domain.entity.SpaceMember;
import com.njydsz.nextwiki.domain.entity.SpaceTemplate;
import com.njydsz.nextwiki.domain.entity.StorageQuota;
import com.njydsz.nextwiki.domain.entity.Tag;
import com.njydsz.nextwiki.domain.entity.TrashItem;
import com.njydsz.nextwiki.domain.entity.UserFavorite;
import com.njydsz.nextwiki.domain.entity.UserRecent;
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

/**
 * NextWiki 统一 MapStruct 转换器。
 *
 * <p>替代已被弃用的 {@link NextwikiConverter}，承担所有 Entity ↔ VO、DTO ↔ Entity、
 * VO ↔ DTO 双向转换。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>使用 MapStruct 编译期生成实现类（{@code NextwikiStructMapperImpl}），性能优于反射
 *   <li>字段名一致时自动映射，无需 {@code @Mapping} 注解
 *   <li>集合转换（{@code List<X>}）由 MapStruct 自动派生
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.06
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface NextwikiStructMapper {

  /** 静态备用实例（与 {@code @Autowired} 注入互斥，仅用于非 Spring 上下文）。 */
  NextwikiStructMapper INSTANCE = Mappers.getMapper(NextwikiStructMapper.class);

  // ==================== FileTag 转换 ====================

  /**
   * 将 {@link FileTag} 实体转换为 {@link FileTagVO}。
   *
   * @param entity 文件标签实体
   * @return 文件标签 VO；入参为 {@code null} 时返回 {@code null}
   */
  FileTagVO fileTagToVO(FileTag entity);

  /**
   * 将 {@link FileTag} 实体列表转换为 {@link FileTagVO} 列表。
   *
   * @param entities 文件标签实体列表
   * @return 文件标签 VO 列表
   */
  List<FileTagVO> fileTagListToVO(List<FileTag> entities);

  // ==================== FileNode 转换 ====================

  /**
   * 将 {@link FileNode} 实体转换为 {@link FileNodeVO}。
   *
   * @param entity 文件节点实体
   * @return 文件节点 VO；入参为 {@code null} 时返回 {@code null}
   */
  FileNodeVO fileNodeToVO(FileNode entity);

  /**
   * 将 {@link FileNode} 实体列表批量转换为 {@link FileNodeVO} 列表。
   *
   * @param entities 文件节点实体列表
   * @return 文件节点 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  List<FileNodeVO> fileNodeListToVO(List<FileNode> entities);

  /**
   * 将 {@link FileNodeDTO} 转换为 {@link FileNode} 实体。
   *
   * @param dto 文件节点 DTO
   * @return 文件节点实体；入参为 {@code null} 时返回 {@code null}
   */
  FileNode fileNodeToEntity(FileNodeDTO dto);

  /**
   * 将 {@link FileNodeDTO} 列表批量转换为 {@link FileNode} 实体列表。
   *
   * @param dtos 文件节点 DTO 列表
   * @return 文件节点实体列表；入参为 {@code null} 或空时返回空列表
   */
  List<FileNode> fileNodeListToEntity(List<FileNodeDTO> dtos);

  /**
   * 将 {@link FileNodeVO} 转换为 {@link FileNodeDTO}。
   *
   * @param vo 文件节点 VO
   * @return 文件节点 DTO；入参为 {@code null} 时返回 {@code null}
   */
  FileNodeDTO fileNodeVOToDTO(FileNodeVO vo);

  /**
   * 将 {@link FileNodeVO} 列表批量转换为 {@link FileNodeDTO} 列表。
   *
   * @param vos 文件节点 VO 列表
   * @return 文件节点 DTO 列表；入参为 {@code null} 或空时返回空列表
   */
  List<FileNodeDTO> fileNodeListVOToDTO(List<FileNodeVO> vos);

  /**
   * 将 {@link FileNodeDTO} 直接转换为 {@link FileNodeVO}（DTO → VO 一步到位）。
   *
   * @param dto 文件节点 DTO
   * @return 文件节点 VO；入参为 {@code null} 时返回 {@code null}
   */
  FileNodeVO fileNodeDTOtoVO(FileNodeDTO dto);

  /**
   * 将 {@link FileNodeDTO} 列表直接批量转换为 {@link FileNodeVO} 列表。
   *
   * @param dtos 文件节点 DTO 列表
   * @return 文件节点 VO 列表；入参为 {@code null} 或空时返回空列表
   */
  List<FileNodeVO> fileNodeListDTOtoVO(List<FileNodeDTO> dtos);

  // ==================== FileVersion 转换 ====================

  FileVersionVO fileVersionToVO(FileVersion entity);

  List<FileVersionVO> fileVersionListToVO(List<FileVersion> entities);

  FileVersion fileVersionToEntity(FileVersionDTO dto);

  List<FileVersion> fileVersionListToEntity(List<FileVersionDTO> dtos);

  FileVersionDTO fileVersionVOToDTO(FileVersionVO vo);

  List<FileVersionDTO> fileVersionListVOToDTO(List<FileVersionVO> vos);

  /**
   * 将 {@link FileVersionDTO} 直接转换为 {@link FileVersionVO}（DTO → VO 一步到位）。
   *
   * @param dto 文件版本 DTO
   * @return 文件版本 VO；入参为 {@code null} 时返回 {@code null}
   */
  FileVersionVO fileVersionDTOtoVO(FileVersionDTO dto);

  /**
   * 将 {@link FileVersion} 实体列表批量转换为 {@link FileVersionDTO} 列表。
   *
   * @param entities 文件版本实体列表（如 {@code versionRepository.findByFileNodeId(...)} 返回值）
   * @return 文件版本 DTO 列表；入参为 {@code null} 或空时返回空列表
   */
  List<FileVersionDTO> fileVersionListToDTO(List<FileVersion> entities);

  // ==================== FileComment 转换 ====================

  FileCommentVO fileCommentToVO(FileComment entity);

  List<FileCommentVO> fileCommentListToVO(List<FileComment> entities);

  FileComment fileCommentToEntity(FileCommentDTO dto);

  List<FileComment> fileCommentListToEntity(List<FileCommentDTO> dtos);

  // ==================== SearchIndex 转换 ====================

  SearchIndexVO searchIndexToVO(SearchIndex entity);

  /**
   * 将 {@link SearchIndex} 实体直接转换为 {@link SearchIndexDTO}（Entity → DTO 一步到位）。
   *
   * @param entity 搜索索引实体
   * @return 搜索索引 DTO；入参为 {@code null} 时返回 {@code null}
   */
  SearchIndexDTO searchIndexToDTO(SearchIndex entity);

  List<SearchIndexVO> searchIndexListToVO(List<SearchIndex> entities);

  SearchIndex searchIndexToEntity(SearchIndexDTO dto);

  List<SearchIndex> searchIndexListToEntity(List<SearchIndexDTO> dtos);

  // ==================== ShareAccessLog 转换 ====================

  ShareAccessLogVO shareAccessLogToVO(ShareAccessLog entity);

  List<ShareAccessLogVO> shareAccessLogListToVO(List<ShareAccessLog> entities);

  ShareAccessLog shareAccessLogToEntity(ShareAccessLogDTO dto);

  List<ShareAccessLog> shareAccessLogListToEntity(List<ShareAccessLogDTO> dtos);

  // ==================== ShareLink 转换 ====================

  ShareLinkVO shareLinkToVO(ShareLink entity);

  List<ShareLinkVO> shareLinkListToVO(List<ShareLink> entities);

  ShareLink shareLinkToEntity(ShareLinkDTO dto);

  List<ShareLink> shareLinkListToEntity(List<ShareLinkDTO> dtos);

  ShareLinkDTO shareLinkVOToDTO(ShareLinkVO vo);

  List<ShareLinkDTO> shareLinkListVOToDTO(List<ShareLinkVO> vos);

  // ==================== StorageQuota 转换 ====================

  StorageQuotaVO storageQuotaToVO(StorageQuota entity);

  List<StorageQuotaVO> storageQuotaListToVO(List<StorageQuota> entities);

  StorageQuota storageQuotaToEntity(StorageQuotaDTO dto);

  List<StorageQuota> storageQuotaListToEntity(List<StorageQuotaDTO> dtos);

  // ==================== Tag 转换 ====================

  TagVO tagToVO(Tag entity);

  List<TagVO> tagListToVO(List<Tag> entities);

  Tag tagToEntity(TagDTO dto);

  List<Tag> tagListToEntity(List<TagDTO> dtos);

  // ==================== TrashItem 转换 ====================

  TrashItemVO trashItemToVO(TrashItem entity);

  List<TrashItemVO> trashItemListToVO(List<TrashItem> entities);

  TrashItem trashItemToEntity(TrashItemDTO dto);

  List<TrashItem> trashItemListToEntity(List<TrashItemDTO> dtos);

  TrashItemDTO trashItemVOToDTO(TrashItemVO vo);

  List<TrashItemDTO> trashItemListVOToDTO(List<TrashItemVO> vos);

  // ==================== FileAcl 转换 ====================

  FileAclVO fileAclToVO(FileAcl entity);

  List<FileAclVO> fileAclListToVO(List<FileAcl> entities);

  FileAcl fileAclToEntity(FileAclDTO dto);

  List<FileAcl> fileAclListToEntity(List<FileAclDTO> dtos);

  // ==================== ShareRecipient 转换 ====================

  ShareRecipientVO shareRecipientToVO(ShareRecipient entity);

  List<ShareRecipientVO> shareRecipientListToVO(List<ShareRecipient> entities);

  ShareRecipient shareRecipientToEntity(ShareRecipientDTO dto);

  List<ShareRecipient> shareRecipientListToEntity(List<ShareRecipientDTO> dtos);

  // ==================== Space 转换 ====================

  SpaceVO spaceToVO(Space entity);

  List<SpaceVO> spaceListToVO(List<Space> entities);

  Space spaceToEntity(SpaceDTO dto);

  List<Space> spaceListToEntity(List<SpaceDTO> dtos);

  // ==================== SpaceMember 转换 ====================

  SpaceMemberDTO spaceMemberToDTO(SpaceMember entity);

  SpaceMember spaceMemberToEntity(SpaceMemberDTO dto);

  // ==================== SpaceTemplate 转换 ====================

  SpaceTemplateDTO spaceTemplateToDTO(SpaceTemplate entity);

  SpaceTemplate spaceTemplateToEntity(SpaceTemplateDTO dto);

  // ==================== UserFavorite 转换 ====================

  UserFavoriteDTO userFavoriteToDTO(UserFavorite entity);

  UserFavorite userFavoriteToEntity(UserFavoriteDTO dto);

  // ==================== UserRecent 转换 ====================

  UserRecentDTO userRecentToDTO(UserRecent entity);

  UserRecent userRecentToEntity(UserRecentDTO dto);
}
