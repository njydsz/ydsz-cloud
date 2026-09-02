package com.njydsz.nextwiki.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.nextwiki.domain.dto.FileCommentDTO;
import com.njydsz.nextwiki.domain.vo.FileCommentVO;

/**
 * 文件评论仓储接口
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>返回领域 VO（{@link FileCommentVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段
 *   <li>CUD 入参使用领域 DTO（{@link FileCommentDTO}），禁止接受 infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FileCommentRepository {

  /**
   * 保存评论记录（新增或更新）
   *
   * @param dto 文件评论 DTO
   * @return 持久化后的评论 VO
   */
  FileCommentVO save(FileCommentDTO dto);

  /**
   * 按 ID 查询单条评论
   *
   * @param id 评论ID
   * @return 评论 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FileCommentVO> findById(String id);

  /**
   * 查询某文件节点下的全部顶级评论（不含回复）
   *
   * @param fileNodeId 文件节点ID
   * @return 评论 VO 列表
   */
  List<FileCommentVO> findByFileNodeId(String fileNodeId);

  /**
   * 查询某条评论下的全部回复（二级评论）
   *
   * @param parentCommentId 父评论ID
   * @return 回复 VO 列表
   */
  List<FileCommentVO> findReplies(String parentCommentId);

  /**
   * 更新评论内容（编辑场景）
   *
   * @param dto 文件评论 DTO
   */
  void update(FileCommentDTO dto);

  /**
   * 删除单条评论（级联删除其回复）
   *
   * @param id 评论ID
   */
  void delete(String id);

  /**
   * 将评论标记为已解决
   *
   * @param id 评论ID
   * @param userId 操作人ID
   */
  void markResolved(String id, String userId);
}
