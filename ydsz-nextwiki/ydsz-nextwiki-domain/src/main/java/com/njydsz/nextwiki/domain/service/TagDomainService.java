package com.njydsz.nextwiki.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.vo.FileNodeVO;
import com.njydsz.nextwiki.domain.dto.FileTagDTO;
import com.njydsz.nextwiki.domain.dto.TagDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;

/**
 * NextWiki 标签领域服务。
 *
 * <p>纯领域逻辑：标签创建、绑定/解绑逻辑、推荐算法。数据访问由 server 层负责，本服务不注入任何 Repository。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class TagDomainService {

  /** 分布式 ID 生成器 */
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  /**
   * 创建标签。
   *
   * <p>server 层需预先按名称查询 existingTag，若已存在则传入非 null 值，本方法将抛出重复异常。 本方法仅构建领域实体并返回，持久化由 server 层负责。
   *
   * @param name 标签名称
   * @param color 标签颜色
   * @param userId 操作用户ID
   * @param existingTag server 层按名称查询到的已存在标签，为 null 表示不存在
   * @return 新建的标签实体（由 server 层持久化）
   */
  public TagDTO createTag(String name, String color, String userId, TagDTO existingTag) {
    if (name == null || name.trim().isEmpty()) {
      throw new BusinessException(NextwikiExceptionCode.TAG_NAME_EMPTY);
    }

    if (existingTag != null) {
      throw BusinessException.of(NextwikiExceptionCode.TAG_ALREADY_EXISTS).data("name", name);
    }

    TagDTO tagDTO =
        TagDTO.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()))
            .name(name.trim())
            .color(color != null ? color : "#1890ff")
            .type("manual")
            .usageCount(0)
            .build();

    tagDTO.setCreatedBy(userId);
    tagDTO.setUpdatedBy(userId);

    log.info("[TagDomainService] 创建标签: name={}, userId={}", name, userId);
    return tagDTO;
  }

  /**
   * 获取所有标签。
   *
   * <p>server 层查询后传入，本方法可作为领域级过滤钩子（当前直接返回）。
   *
   * @param allTags server 层查询到的全量标签列表
   * @return 标签列表
   */
  public List<TagDTO> getAllTags(List<TagDTO> allTags) {
    return allTags != null ? allTags : List.of();
  }

  /**
   * 获取文件的标签列表。
   *
   * <p>server 层查询后传入，本方法可作为领域级过滤/排序钩子（当前直接返回）。
   *
   * @param fileNodeId 文件节点ID
   * @param fileTags server 层查询到的文件标签列表
   * @return 标签列表
   */
  public List<TagDTO> getFileTags(String fileNodeId, List<TagDTO> fileTags) {
    return fileTags != null ? fileTags : List.of();
  }

  /**
   * 批量绑定标签到文件。
   *
   * <p>纯领域逻辑：校验文件存在性、过滤已绑定标签、生成待创建的关联实体。 返回的 FileTagDO 列表由 server 层持久化并更新标签使用计数。
   *
   * @param fileNodeId 文件节点ID
   * @param tagIds 待绑定的标签ID列表
   * @param userId 操作用户ID
   * @param node server 层查询到的文件节点 VO
   * @param tags server 层查询到的标签实体列表
   * @param existingFileTags server 层查询到的文件已有标签关联列表
   * @return 待创建的 FileTagDO 关联实体列表（由 server 层持久化并更新使用计数）
   */
  public List<FileTagDTO> batchBindTags(
      String fileNodeId,
      List<String> tagIds,
      String userId,
      FileNodeVO node,
      List<TagDTO> tags,
      List<FileTagDTO> existingFileTags) {
    if (tagIds == null || tagIds.isEmpty()) {
      return List.of();
    }

    if (node == null) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND)
          .data("fileNodeId", fileNodeId);
    }

    List<String> existingTagIds =
        existingFileTags != null
            ? existingFileTags.stream().map(FileTagDTO::getTagId).collect(Collectors.toList())
            : List.of();

    List<FileTagDTO> toCreate = new ArrayList<>();

    for (String tagId : tagIds) {
      if (existingTagIds.contains(tagId)) {
        continue;
      }

      TagDTO tagDTO =
          tags != null
              ? tags.stream().filter(t -> tagId.equals(t.getId())).findFirst().orElse(null)
              : null;
      if (tagDTO == null) {
        log.warn("[TagDomainService] 标签不存在，跳过: tagId={}", tagId);
        continue;
      }

      FileTagDTO fileTagDTO =
          FileTagDTO.builder().fileNodeId(fileNodeId).tagId(tagId).build();
      fileTagDTO.setCreatedBy(userId);
      fileTagDTO.setUpdatedBy(userId);
      toCreate.add(fileTagDTO);
    }

    log.info(
        "[TagDomainService] 批量绑定标签: fileNodeId={}, bindCount={}", fileNodeId, toCreate.size());
    return toCreate;
  }

  /**
   * 推荐标签（基于文件名和后缀）。
   *
   * <p>纯领域逻辑：优先匹配文件名/_suffix 包含关系，不足 5 个时按使用频率补充已有标签。
   *
   * @param fileNodeId 文件节点ID
   * @param node server 层查询到的文件节点 VO
   * @param allTags server 层查询到的全量标签列表
   * @param existingTags server 层查询到的文件已有标签列表
   * @return 推荐标签列表（最多 5 个）
   */
  public List<TagDTO> recommendTags(
      String fileNodeId, FileNodeVO node, List<TagDTO> allTags, List<TagDTO> existingTags) {
    if (node == null) {
      return List.of();
    }

    List<TagDTO> all = allTags != null ? allTags : List.of();
    List<TagDTO> recommended = new ArrayList<>();

    String fileName = node.getName() != null ? node.getName().toLowerCase() : "";
    String suffix = node.getSuffix() != null ? node.getSuffix().toLowerCase() : "";

    for (TagDTO tagDTO : all) {
      String tagName = tagDTO.getName() != null ? tagDTO.getName().toLowerCase() : "";
      if (fileName.contains(tagName) || tagName.contains(suffix)) {
        recommended.add(tagDTO);
      }
    }

    if (recommended.size() < 5) {
      List<String> existingIds =
          existingTags != null
              ? existingTags.stream().map(TagDTO::getId).collect(Collectors.toList())
              : List.of();

      for (TagDTO tagDTO : all) {
        if (recommended.size() >= 5) {
          break;
        }
        if (!existingIds.contains(tagDTO.getId()) && !recommended.contains(tagDTO)) {
          if (tagDTO.getUsageCount() != null && tagDTO.getUsageCount() > 0) {
            recommended.add(tagDTO);
          }
        }
      }
    }

    log.info(
        "[TagDomainService] 推荐标签: fileNodeId={}, count={}", fileNodeId, recommended.size());
    return recommended;
  }
}
