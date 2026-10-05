package com.njydsz.nextwiki.server.service;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.nextwiki.domain.dto.FileCommentDTO;
import com.njydsz.nextwiki.domain.repository.FileCommentRepository;
import com.njydsz.nextwiki.domain.vo.FileCommentVO;

/**
 * 文件评论应用服务。
 *
 * <p>封装文件评论的 CRUD 操作和解决标记逻辑。
 * Controller 通过本 Service 访问评论数据，禁止直接依赖 FileCommentRepository。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileCommentService {

  private final FileCommentRepository commentRepository;

  /**
   * 查询文件的所有评论（按时间升序）。
   *
   * <p>返回所有未删除的评论，包括顶级评论和它们的回复。
   *
   * @param fileNodeId 文件节点 ID
   * @return 评论 VO 列表
   */
  public List<FileCommentVO> listComments(String fileNodeId) {
    return commentRepository.findByFileNodeId(fileNodeId);
  }

  /**
   * 添加评论或回复。
   *
   * <p>保存评论记录并返回持久化后的 VO（含生成的主键 ID）。
   *
   * @param comment 评论 DTO（需提前设置 id / fileNodeId / content / parentCommentId 等）
   * @return 保存后的评论 VO
   */
  @Transactional(rollbackFor = Exception.class)
  public FileCommentVO addComment(FileCommentDTO comment) {
    return commentRepository.save(comment);
  }

  /**
   * 删除评论（软删除）。
   *
   * @param commentId 评论 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void deleteComment(String commentId) {
    commentRepository.delete(commentId);
    log.info("[FileCommentService] 删除评论: commentId={}", commentId);
  }

  /**
   * 标记评论为已解决。
   *
   * @param commentId 评论 ID
   * @param userId 操作人 ID
   */
  @Transactional(rollbackFor = Exception.class)
  public void resolveComment(String commentId, String userId) {
    commentRepository.markResolved(commentId, userId);
    log.info("[FileCommentService] 标记评论已解决: commentId={}, userId={}", commentId, userId);
  }
}
