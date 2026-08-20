package com.njydsz.nextwiki.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.nextwiki.domain.dto.TagDTO;
import com.njydsz.nextwiki.domain.vo.FileTagVO;
import com.njydsz.nextwiki.domain.vo.TagVO;

/**
 * 标签仓储接口
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回领域 VO（{@link TagVO} / {@link FileTagVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 *   <li>CUD 入参使用领域 DTO（{@link TagDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TagRepository {

  /**
   * 保存标签（新增或更新）
   *
   * @param dto 标签 DTO
   * @return 持久化后的标签 VO
   */
  TagVO save(TagDTO dto);

  /**
   * 按 ID 查询标签
   *
   * @param id 标签ID
   * @return 标签 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<TagVO> findById(String id);

  /**
   * 按名称精确查询标签（用于创建时查重）
   *
   * @param name 标签名称
   * @return 标签 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<TagVO> findByName(String name);

  /**
   * 查询某文件节点已绑定的全部标签
   *
   * @param fileNodeId 文件节点ID
   * @return 标签 VO 列表
   */
  List<TagVO> findByFileNodeId(String fileNodeId);

  /**
   * 绑定标签到文件
   *
   * @param fileNodeId 文件节点ID
   * @param tagId 标签ID
   */
  void bindTag(String fileNodeId, String tagId);

  /**
   * 解绑文件上的单个标签
   *
   * @param fileNodeId 文件节点ID
   * @param tagId 标签ID
   */
  void unbindTag(String fileNodeId, String tagId);

  /**
   * 解绑某文件上的全部标签
   *
   * @param fileNodeId 文件节点ID
   */
  void unbindAllByFileNodeId(String fileNodeId);

  /**
   * 查询某文件节点的全部标签关联记录
   *
   * @param fileNodeId 文件节点ID
   * @return 文件-标签关联 VO 列表
   */
  List<FileTagVO> findFileTagsByFileNodeId(String fileNodeId);

  /**
   * 递增标签使用计数
   *
   * @param tagId 标签ID
   */
  void incrementUsage(String tagId);

  /**
   * 递减标签使用计数
   *
   * @param tagId 标签ID
   */
  void decrementUsage(String tagId);

  /**
   * 按标签名搜索关联的文件节点ID
   *
   * @param tagName 标签名
   * @return 文件节点ID列表
   */
  List<String> findFileNodeIdsByTagName(String tagName);
}
