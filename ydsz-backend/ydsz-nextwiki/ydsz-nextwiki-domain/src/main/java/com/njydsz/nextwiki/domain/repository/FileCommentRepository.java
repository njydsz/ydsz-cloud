package com.njydsz.nextwiki.domain.repository;

import java.util.List;

import com.njydsz.nextwiki.domain.entity.FileComment;

/**
 * 文件评论仓储接口（P1-5）
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public interface FileCommentRepository {

    FileComment save(FileComment comment);

    FileComment findById(String id);

    List<FileComment> findByFileNodeId(String fileNodeId);

    List<FileComment> findReplies(String parentCommentId);

    void update(FileComment comment);

    void delete(String id);

    void markResolved(String id, String userId);
}
