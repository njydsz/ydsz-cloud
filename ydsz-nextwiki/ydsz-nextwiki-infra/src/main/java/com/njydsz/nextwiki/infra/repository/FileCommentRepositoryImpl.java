package com.njydsz.nextwiki.infra.repository;

import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.converter.NextwikiStructMapper;
import com.njydsz.nextwiki.domain.dto.FileCommentDTO;
import com.njydsz.nextwiki.domain.entity.FileComment;
import com.njydsz.nextwiki.domain.repository.FileCommentRepository;
import com.njydsz.nextwiki.domain.vo.FileCommentVO;
import com.njydsz.nextwiki.infra.mapper.FileCommentMapper;

/**
 * 文件评论仓储实现
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
public class FileCommentRepositoryImpl implements FileCommentRepository {

  private final SnowflakeIdGenerator snowflakeIdGenerator;
  private final FileCommentMapper fileCommentMapper;
  private final NextwikiStructMapper mapper;

  @Override
  public FileCommentVO save(FileCommentDTO dto) {
    FileComment entity = mapper.fileCommentToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    fileCommentMapper.insertFileComment(entity);
    return mapper.fileCommentToVO(entity);
  }

  @Override
  public Optional<FileCommentVO> findById(String id) {
    return Optional.ofNullable(fileCommentMapper.selectFileCommentById(id)).map(mapper::fileCommentToVO);
  }

  @Override
  public List<FileCommentVO> findByFileNodeId(String fileNodeId) {
    return mapper.fileCommentListToVO(fileCommentMapper.selectFileCommentsByFileNodeId(fileNodeId));
  }

  @Override
  public List<FileCommentVO> findReplies(String parentCommentId) {
    return mapper.fileCommentListToVO(fileCommentMapper.selectFileCommentReplies(parentCommentId));
  }

  @Override
  public void update(FileCommentDTO dto) {
    FileComment entity = mapper.fileCommentToEntity(dto);
    fileCommentMapper.updateFileComment(entity);
  }

  @Override
  public void delete(String id) {
    fileCommentMapper.deleteFileComment(id);
  }

  @Override
  public void markResolved(String id, String userId) {
    fileCommentMapper.markFileCommentResolved(id, userId);
  }
}
