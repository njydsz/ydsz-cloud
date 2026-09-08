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

  /**
   * 保存文件评论/回复。
   *
   * @param dto 评论数据传输对象
   * @return 保存后的评论视图对象
   */
  @Override
  public FileCommentVO save(FileCommentDTO dto) {
    FileComment entity = mapper.fileCommentToEntity(dto);
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    }
    fileCommentMapper.insertFileComment(entity);
    return mapper.fileCommentToVO(entity);
  }

  /**
   * 按评论 ID 查询单条评论。
   *
   * @param id 评论 ID
   * @return 评论视图对象（可能为空）
   */
  @Override
  public Optional<FileCommentVO> findById(String id) {
    return Optional.ofNullable(fileCommentMapper.selectFileCommentById(id)).map(mapper::fileCommentToVO);
  }

  /**
   * 按文件节点 ID 查询所有顶级评论。
   *
   * @param fileNodeId 文件节点 ID
   * @return 评论视图对象列表
   */
  @Override
  public List<FileCommentVO> findByFileNodeId(String fileNodeId) {
    return mapper.fileCommentListToVO(fileCommentMapper.selectFileCommentsByFileNodeId(fileNodeId));
  }

  /**
   * 查询指定评论的所有回复。
   *
   * @param parentCommentId 父评论 ID
   * @return 回复评论视图对象列表
   */
  @Override
  public List<FileCommentVO> findReplies(String parentCommentId) {
    return mapper.fileCommentListToVO(fileCommentMapper.selectFileCommentReplies(parentCommentId));
  }

  /**
   * 更新评论内容（编辑评论）。
   *
   * @param dto 评论数据传输对象
   */
  @Override
  public void update(FileCommentDTO dto) {
    FileComment entity = mapper.fileCommentToEntity(dto);
    fileCommentMapper.updateFileComment(entity);
  }

  /**
   * 删除评论（软删除）。
   *
   * @param id 评论 ID
   */
  @Override
  public void delete(String id) {
    fileCommentMapper.deleteFileComment(id);
  }

  /**
   * 标记评论为已解决。
   *
   * @param id 评论 ID
   * @param userId 操作人 ID
   */
  @Override
  public void markResolved(String id, String userId) {
    fileCommentMapper.markFileCommentResolved(id, userId);
  }
}
