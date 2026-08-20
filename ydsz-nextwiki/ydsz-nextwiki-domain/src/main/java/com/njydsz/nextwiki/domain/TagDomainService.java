package com.njydsz.nextwiki.domain.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.FileTagDTO;
import com.njydsz.nextwiki.domain.enums.NextwikiExceptionCode;
import com.njydsz.nextwiki.domain.vo.TagVO;

/**
 * 标签领域服务
 *
 * <p>封装标签管理的核心业务逻辑：标签创建、文件标签关联、标签合并。
 * 本服务为纯领域逻辑组件，不执行任何数据访问；数据由应用层加载后传入。
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
   * 构建标签实体（纯领域逻辑，不执行持久化）。
   *
   * @param name 标签名称
   * @param color 标签颜色（十六进制，如 "#FF5733"）
   * @param userId 创建人 ID
   * @return 构建完成的 {@link TagVO} 实例（未持久化）
   */
  public TagVO buildTag(String name, String color, String userId) {
    if (name == null || name.trim().isEmpty()) {
      throw new BusinessException(NextwikiExceptionCode.TAG_NAME_EMPTY);
    }

    TagVO tag = new TagVO();
    tag.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    tag.setName(name.trim());
    tag.setColor(color);
    tag.setCreatedBy(userId);
    return tag;
  }

  /**
   * 构建文件-标签关联（纯领域逻辑，不执行持久化）。
   *
   * @param fileId 文件 ID
   * @param tagId 标签 ID
   * @param userId 操作人 ID
   * @return 构建完成的 {@link FileTagDTO} 实例（未持久化）
   */
  public FileTagDTO buildFileTag(String fileId, String tagId, String userId) {
    FileTagDTO fileTag = new FileTagDTO();
    fileTag.setId(snowflakeIdGenerator.nextId());
    fileTag.setFileId(fileId);
    fileTag.setTagId(tagId);
    fileTag.setCreatedBy(userId);
    return fileTag;
  }

  /**
   * 计算需要新增和删除的标签关联（纯领域逻辑）。
   *
   * <p>根据当前标签 ID 列表与目标标签 ID 列表，计算差集。
   *
   * @param currentTagIds 当前标签 ID 列表（已由应用层加载）
   * @param targetTagIds 目标标签 ID 列表
   * @return 包含 toAdd 和 toRemove 两个列表的结果
   */
  public TagDiff calculateTagDiff(List<String> currentTagIds, List<String> targetTagIds) {
    Set<String> current = currentTagIds != null ? Set.copyOf(currentTagIds) : Set.of();
    Set<String> target = targetTagIds != null ? Set.copyOf(targetTagIds) : Set.of();

    List<String> toAdd = target.stream().filter(id -> !current.contains(id)).collect(Collectors.toList());
    List<String> toRemove =
        current.stream().filter(id -> !target.contains(id)).collect(Collectors.toList());

    return new TagDiff(toAdd, toRemove);
  }

  /** 标签差异结果 */
  public record TagDiff(List<String> toAdd, List<String> toRemove) {}
}
