package com.njydsz.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.nextwiki.infra.entity.FileTagDO;
import com.njydsz.nextwiki.infra.entity.TagDO;

/**
 * 标签仓储接口
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TagRepository {

  /**
   * 保存标签（新增或更新）。
   *
   * @param tag 待持久化的标签实体（含名称、颜色、类型等）
   * @return 持久化后的标签（回填主键）
   */
  TagDO save(TagDO tag);

  /**
   * 按 ID 查询标签。
   *
   * @param id 标签 ID
   * @return 标签实体，不存在时返回 null
   */
  TagDO findById(String id);

  /**
   * 按名称精确查询标签（用于创建时查重）。
   *
   * @param name 标签名称
   * @return 匹配的标签实体，不存在时返回 null
   */
  TagDO findByName(String name);

  /**
   * 查询全部标签（用于标签库展示）。
   *
   * @return 标签列表，无记录时返回空列表
   */
  List<TagDO> findAll();

  /**
   * 查询某文件节点已绑定的全部标签。
   *
   * @param fileNodeId 文件节点 ID
   * @return 标签列表，无记录时返回空列表
   */
  List<TagDO> findByFileNodeId(String fileNodeId);

  /**
   * 绑定标签到文件（写入 nw_file_tag 关联记录）。
   *
   * @param fileNodeId 文件节点 ID
   * @param tagId 标签 ID
   */
  void bindTag(String fileNodeId, String tagId);

  /**
   * 解绑文件上的单个标签（同时递减标签使用计数）。
   *
   * @param fileNodeId 文件节点 ID
   * @param tagId 标签 ID
   */
  void unbindTag(String fileNodeId, String tagId);

  /**
   * 解绑某文件上的全部标签（删除文件时级联清理关联）。
   *
   * @param fileNodeId 文件节点 ID
   */
  void unbindAllByFileNodeId(String fileNodeId);

  /**
   * 查询某文件节点的全部标签关联记录（含中间表）。
   *
   * @param fileNodeId 文件节点 ID
   * @return 文件-标签关联列表，无记录时返回空列表
   */
  List<FileTagDO> findFileTagsByFileNodeId(String fileNodeId);

  /**
   * 递增标签使用计数（绑定标签时调用）。
   *
   * @param tagId 标签 ID
   */
  void incrementUsage(String tagId);

  /**
   * 递减标签使用计数（解绑标签时调用）。
   *
   * @param tagId 标签 ID
   */
  void decrementUsage(String tagId);

  /** 按标签名搜索关联的文件节点ID */
  List<String> findFileNodeIdsByTagName(String tagName);
}
