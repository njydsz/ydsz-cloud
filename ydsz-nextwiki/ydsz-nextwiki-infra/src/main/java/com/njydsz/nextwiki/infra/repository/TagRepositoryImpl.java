package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.dto.TagDTO;
import com.njydsz.nextwiki.domain.repository.TagRepository;
import com.njydsz.nextwiki.domain.vo.FileTagVO;
import com.njydsz.nextwiki.domain.vo.TagVO;
import com.njydsz.nextwiki.domain.converter.NextwikiConverter;
import com.njydsz.nextwiki.domain.entity.FileTag;
import com.njydsz.nextwiki.domain.entity.Tag;
import com.njydsz.nextwiki.infra.mapper.TagMapper;

/**
 * 标签仓储实现
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>所有数据访问通过本类的语义方法，禁止暴露 Mapper
 *   <li>通过 {@link NextwikiConverter} 将 DO 转换为 VO 后返回
 *   <li>CUD 入参 DTO 通过 {@link NextwikiConverter} 转换为 DO 后执行数据库操作
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
  private final NextwikiConverter converter;

  @Override
  public TagVO save(TagDTO dto) {
    Tag entity = converter.dtoToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    tagMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public Optional<TagVO> findById(String id) {
    return Optional.ofNullable(tagMapper.selectById(id)).map(converter::entityToVO);
  }

  @Override
  public Optional<TagVO> findByName(String name) {
    return Optional.ofNullable(tagMapper.selectByName(name)).map(converter::entityToVO);
  }

  @Override
  public List<TagVO> findByFileNodeId(String fileNodeId) {
    return converter.tagListToVO(tagMapper.selectByFileNodeId(fileNodeId));
  }

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

  @Override
  public void unbindTag(String fileNodeId, String tagId) {
    tagMapper.deleteFileTag(fileNodeId, tagId);
  }

  @Override
  public void unbindAllByFileNodeId(String fileNodeId) {
    tagMapper.deleteAllFileTags(fileNodeId);
  }

  @Override
  public List<FileTagVO> findFileTagsByFileNodeId(String fileNodeId) {
    return converter.fileTagListToVO(tagMapper.selectFileTagsByFileNodeId(fileNodeId));
  }

  @Override
  public void incrementUsage(String tagId) {
    tagMapper.incrementUsage(tagId);
  }

  @Override
  public void decrementUsage(String tagId) {
    tagMapper.decrementUsage(tagId);
  }

  @Override
  public List<String> findFileNodeIdsByTagName(String tagName) {
    return tagMapper.findFileNodeIdsByTagName(tagName);
  }
}
