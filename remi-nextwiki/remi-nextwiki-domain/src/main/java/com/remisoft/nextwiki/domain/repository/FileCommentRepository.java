package com.remisoft.nextwiki.domain.repository;

import java.util.List;

import com.remisoft.nextwiki.domain.entity.FileComment;

/**
 * 文件评论仓储接口（P1-5）
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface FileCommentRepository {

    /**
     * 保存评论记录（新增或更新）。
     *
     * @param comment 待持久化的评论实体（含内容、关联节点、父评论等）
     * @return 持久化后的评论（回填主键）
     */
    FileComment save(FileComment comment);

    /**
     * 按 ID 查询单条评论。
     *
     * @param id 评论 ID
     * @return 评论实体，不存在时返回 null
     */
    FileComment findById(String id);

    /**
     * 查询某文件节点下的全部顶级评论（不含回复）。
     *
     * @param fileNodeId 文件节点 ID
     * @return 该节点的评论列表，无记录时返回空列表
     */
    List<FileComment> findByFileNodeId(String fileNodeId);

    /**
     * 查询某条评论下的全部回复（二级评论）。
     *
     * @param parentCommentId 父评论 ID
     * @return 回复列表，无记录时返回空列表
     */
    List<FileComment> findReplies(String parentCommentId);

    /**
     * 更新评论内容（编辑场景）。
     *
     * @param comment 待更新的评论实体（需含主键）
     */
    void update(FileComment comment);

    /**
     * 删除单条评论（级联删除其回复）。
     *
     * @param id 评论 ID
     */
    void delete(String id);

    /**
     * 将评论标记为已解决（用于批注/待办场景）。
     *
     * @param id     评论 ID
     * @param userId 操作人 ID
     */
    void markResolved(String id, String userId);
}
