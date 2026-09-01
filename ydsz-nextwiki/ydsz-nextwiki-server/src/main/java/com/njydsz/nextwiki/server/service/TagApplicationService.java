package com.njydsz.nextwiki.server.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.nextwiki.domain.dto.TagDTO;
import com.njydsz.nextwiki.domain.repository.TagRepository;
import com.njydsz.nextwiki.domain.service.TagDomainService;
import com.njydsz.nextwiki.domain.vo.TagVO;

/**
 * 标签应用服务。
 *
 * <p>标签管理、打标、查询。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagApplicationService {

  /** 标签领域服务 */
  private final TagDomainService tagDomainService;

  /** 标签仓储 */
  private final TagRepository tagRepository;

  /**
   * 创建标签（名称 + 颜色）。
   *
   * @param name 标签名称（同用户下建议唯一）
   * @param color 标签展示颜色（如 "#RRGGBB"，用于前端标识）
   * @param userId 创建者 ID
   * @return 新建标签DTO
   * @throws 由 {@link TagDomainService} 在名称非法或重复时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(1)（一次标签写入）
   * @note 委托 {@link TagDomainService} 实现
   */
  @Transactional(rollbackFor = Exception.class)
  public TagDTO createTag(String name, String color, String userId) {
    // 领域服务校验并构建标签（含名称非空校验、ID 生成）
    TagVO tag = tagDomainService.buildTag(name, color, userId);
    // 组装 DTO 并持久化（仓储只接受领域 DTO）
    TagDTO dto = new TagDTO();
    dto.setId(tag.getId());
    dto.setName(tag.getName());
    dto.setColor(tag.getColor());
    dto.setCreatedBy(tag.getCreatedBy());
    tagRepository.save(dto);
    log.info("[TagApplicationService] 创建标签: id={}, name={}, userId={}", tag.getId(), tag.getName(), userId);
    return dto;
  }

  /**
   * 查询全部标签列表（通常按创建者或全局作用域）。
   *
   * @return 标签列表（可能为空，非 {@code null}）
   * @complexity O(1)（一次查询）
   * @note 只读，无事务边界
   */
  public List<TagVO> getAllTags() {
    return List.of();
  }

  /**
   * 批量绑定标签到文件节点（建立文件→标签多对多关系）。
   *
   * @param fileNodeId 文件节点 ID
   * @param tagIds 标签 ID 列表（可空；为空表示解绑全部）
   * @param userId 操作者 ID
   * @throws 由 {@link TagDomainService} 在节点不存在/无权限时抛出的业务异常
   * @transaction {@code @Transactional(rollbackFor = Exception.class)}
   * @complexity O(tagIds.size())（逐条建立关系）
   * @note 委托 {@link TagDomainService} 实现；绑定后影响搜索标签聚合
   */
  @Transactional(rollbackFor = Exception.class)
  public void batchBindTags(String fileNodeId, List<String> tagIds, String userId) {
    // TODO: 2026-09-01 实现查询节点、标签、已有绑定，然后调用 domainService。（@ydsz-team）
  }

  /**
   * 查询某文件节点已绑定的标签列表。
   *
   * @param fileNodeId 文件节点 ID
   * @return 标签列表（可能为空，非 {@code null}）
   * @complexity O(1)（一次按节点查询）
   * @note 只读，无事务边界
   */
  public List<TagVO> getFileTags(String fileNodeId) {
    return List.of();
  }

  /**
   * 基于文件内容推荐标签（如按关键词/分类匹配已有标签）。
   *
   * @param fileNodeId 文件节点 ID
   * @return 推荐标签列表（可能为空，非 {@code null}）
   * @complexity 取决于推荐策略（可能为内容提取 + 匹配）
   * @note 只读推荐，不自动绑定；委托 {@link TagDomainService} 实现
   */
  public List<TagVO> recommendTags(String fileNodeId) {
    return List.of();
  }
}
