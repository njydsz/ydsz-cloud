package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.TagDTO;
import com.njydsz.nextwiki.domain.entity.FileTag;
import com.njydsz.nextwiki.domain.entity.Tag;
import com.njydsz.nextwiki.domain.repository.TagRepository;
import com.njydsz.nextwiki.domain.vo.FileTagVO;
import com.njydsz.nextwiki.domain.vo.TagVO;
import com.njydsz.nextwiki.infra.mapper.TagMapper;

/**
 * 标签仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 * <li>通过 {@link NextwikiStructMapper} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiStructMapper} 转换为 DO 后执行数据库操作
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TagRepositoryImpl implements TagRepository {

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final TagMapper tagMapper;
  private final NextwikiStructMapper mapper;

  /**
   * 保存标签。
   *
   * @param dto 标签数据传输对象
   * @return 保存后的标签视图对象
   */
  @Override
  public TagVO save(TagDTO dto) {
    Tag entity = mapper.tagToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    tagMapper.insert(entity);
    return mapper.tagToVO(entity);
  }

  /**
   * 按 ID 查询标签。
   *
   * @param id 标签 ID
   * @return 标签视图对象（可能为空）
   */
  @Override
  public Optional<TagVO> findById(String id) {
    return Optional.ofNullable(tagMapper.selectById(id)).map(mapper::tagToVO);
  }

  /**
   * 按名称精确查询标签（校验唯一性时使用）。
   *
   * @param name 标签名称
   * @return 标签视图对象（可能为空）
   */
  @Override
  public Optional<TagVO> findByName(String name) {
    return Optional.ofNullable(tagMapper.selectByName(name)).map(mapper::tagToVO);
  }

  /**
   * 按文件节点 ID 查询已绑定的标签列表。
   *
   * @param fileNodeId 文件节点 ID
   * @return 标签视图对象列表
   */
  @Override
  public List<TagVO> findByFileNodeId(String fileNodeId) {
    return mapper.tagListToVO(tagMapper.selectByFileNodeId(fileNodeId));
  }

  /**
   * 为文件绑定标签（插入 file_tag 中间表）。
   *
   * @param fileNodeId 文件节点 ID
   * @param tagId 标签 ID
   */
  @Override
  public void bindTag(String fileNodeId, String tagId) {
    FileTag fileTag =
        FileTag.builder()
            .id(String.valueOf(snowflakeIdGenerator.nextId()))
            .fileNodeId(fileNodeId)
            .tagId(tagId)
            .build();
    tagMapper.insertFileTag(fileTag);
  }

  /**
   * 解绑文件的指定标签。
   *
   * @param fileNodeId 文件节点 ID
   * @param tagId 标签 ID
   */
  @Override
  public void unbindTag(String fileNodeId, String tagId) {
    tagMapper.deleteFileTag(fileNodeId, tagId);
  }

  /**
   * 解绑文件的所有标签（文件删除时调用）。
   *
   * @param fileNodeId 文件节点 ID
   */
  @Override
  public void unbindAllByFileNodeId(String fileNodeId) {
    tagMapper.deleteAllFileTags(fileNodeId);
  }

  /**
   * 查询文件的标签关联详情（含绑定时间等）。
   *
   * @param fileNodeId 文件节点 ID
   * @return 文件标签关联视图列表
   */
  @Override
  public List<FileTagVO> findFileTagsByFileNodeId(String fileNodeId) {
    return mapper.fileTagListToVO(tagMapper.selectFileTagsByFileNodeId(fileNodeId));
  }

  /**
   * 递增标签使用次数（绑定标签时调用）。
   *
   * @param tagId 标签 ID
   */
  @Override
  public void incrementUsage(String tagId) {
    tagMapper.incrementUsage(tagId);
  }

  /**
   * 递减标签使用次数（解绑标签时调用）。
   *
   * @param tagId 标签 ID
   */
  @Override
  public void decrementUsage(String tagId) {
    tagMapper.decrementUsage(tagId);
  }

  /**
   * 按标签名称搜索已绑定该标签的文件 ID 列表。
   *
   * @param tagName 标签名称
   * @return 文件节点 ID 列表
   */
  @Override
  public List<String> findFileNodeIdsByTagName(String tagName) {
    return tagMapper.findFileNodeIdsByTagName(tagName);
  }
}
