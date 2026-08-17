package com.njydsz.nextwiki.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.infra.entity.FileNodeDO;
import com.njydsz.nextwiki.infra.entity.FileTagDO;
import com.njydsz.nextwiki.infra.entity.TagDO;
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
@Service
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
  public TagDO createTag(String name, String color, String userId, TagDO existingTag) {
    if (name == null || name.trim().isEmpty()) {
      throw new BusinessException(NextwikiExceptionCode.TAG_NAME_EMPTY);
    }

    if (existingTag != null) {
      throw BusinessException.of(NextwikiExceptionCode.TAG_ALREADY_EXISTS).data("name", name);
    }

    TagDO TagDO =
        TagDO.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
            .name(name.trim())
            .color(color != null ? color : "#1890ff")
            .type("manual")
            .usageCount(0)
            .revision(0)
            .deleted(0)
            .build();

    TagDO.setCreatedBy(userId);
    TagDO.setUpdatedBy(userId);

    log.info("[TagDomainService] 创建标签: name={}, userId={}", name, userId);
    return TagDO;
  }

  /**
   * 获取所有标签。
   *
   * <p>server 层查询后传入，本方法可作为领域级过滤钩子（当前直接返回）。
   *
   * @param allTags server 层查询到的全量标签列表
   * @return 标签列表
   */
  public List<TagDO> getAllTags(List<TagDO> allTags) {
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
  public List<TagDO> getFileTags(String fileNodeId, List<TagDO> fileTags) {
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
   * @param FileNodeDO server 层查询到的文件节点实体
   * @param tags server 层查询到的标签实体列表
   * @param existingFileTags server 层查询到的文件已有标签关联列表
   * @return 待创建的 FileTagDO 关联实体列表（由 server 层持久化并更新使用计数）
   */
  public List<FileTagDO> batchBindTags(
      String fileNodeId,
      List<String> tagIds,
      String userId,
      FileNodeDO FileNodeDO,
      List<TagDO> tags,
      List<FileTagDO> existingFileTags) {
    if (tagIds == null || tagIds.isEmpty()) {
      return List.of();
    }

    if (FileNodeDO == null) {
      throw BusinessException.of(NextwikiExceptionCode.FILE_NOT_FOUND)
          .data("fileNodeId", fileNodeId);
    }

    List<String> existingTagIds =
        existingFileTags != null
            ? existingFileTags.stream().map(FileTagDO::getTagId).collect(Collectors.toList())
            : List.of();

    List<FileTagDO> toCreate = new ArrayList<>();

    for (String tagId : tagIds) {
      if (existingTagIds.contains(tagId)) {
        continue;
      }

      TagDO TagDO =
          tags != null
              ? tags.stream().filter(t -> tagId.equals(t.getId())).findFirst().orElse(null)
              : null;
      if (TagDO == null) {
        log.warn("[TagDomainService] 标签不存在，跳过: tagId={}", tagId);
        continue;
      }

      FileTagDO FileTagDO =
          FileTagDO.builder().fileNodeId(fileNodeId).tagId(tagId).build();
      FileTagDO.setCreatedBy(userId);
      FileTagDO.setUpdatedBy(userId);
      toCreate.add(FileTagDO);
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
   * @param FileNodeDO server 层查询到的文件节点实体
   * @param allTags server 层查询到的全量标签列表
   * @param existingTags server 层查询到的文件已有标签列表
   * @return 推荐标签列表（最多 5 个）
   */
  public List<TagDO> recommendTags(
      String fileNodeId, FileNodeDO FileNodeDO, List<TagDO> allTags, List<TagDO> existingTags) {
    if (FileNodeDO == null) {
      return List.of();
    }

    List<TagDO> all = allTags != null ? allTags : List.of();
    List<TagDO> recommended = new ArrayList<>();

    String fileName = FileNodeDO.getName() != null ? FileNodeDO.getName().toLowerCase() : "";
    String suffix = FileNodeDO.getSuffix() != null ? FileNodeDO.getSuffix().toLowerCase() : "";

    for (TagDO TagDO : all) {
      String tagName = TagDO.getName() != null ? TagDO.getName().toLowerCase() : "";
      if (fileName.contains(tagName) || tagName.contains(suffix)) {
        recommended.add(TagDO);
      }
    }

    if (recommended.size() < 5) {
      List<String> existingIds =
          existingTags != null
              ? existingTags.stream().map(TagDO::getId).collect(Collectors.toList())
              : List.of();

      for (TagDO TagDO : all) {
        if (recommended.size() >= 5) {
          break;
        }
        if (!existingIds.contains(TagDO.getId()) && !recommended.contains(TagDO)) {
          if (TagDO.getUsageCount() != null && TagDO.getUsageCount() > 0) {
            recommended.add(TagDO);
          }
        }
      }
    }

    log.info(
        "[TagDomainService] 推荐标签: fileNodeId={}, count={}", fileNodeId, recommended.size());
    return recommended;
  }
}
