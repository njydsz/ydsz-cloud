package com.njydsz.nextwiki.infra.repository;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.njydsz.nextwiki.domain.entity.FileComment;
import com.njydsz.nextwiki.domain.repository.FileCommentRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 文件评论仓储 stub 实现（P1-5 占位）。
 *
 * <p>当前评论功能尚未完整实现，本 stub 仅保证 Spring 容器启动成功。
 * 所有写操作抛出 {@link UnsupportedOperationException}，读操作返回空结果。
 *
 * <p>TODO: 待完整实现后替换为真实的 MyBatis-Mapper 驱动实现：
 * <ul>
 *   <li>新建 {@code FileCommentMapper} 接口 + 对应 XML</li>
 *   <li>新建数据表 {@code nw_file_comment} DDL</li>
 *   <li>实现树形回复查询、解决标记、软删除级联等逻辑</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Repository
public class FileCommentRepositoryImpl implements FileCommentRepository {

    @Override
    public FileComment save(FileComment comment) {
        log.warn("[FileCommentRepositoryImpl] 评论功能未实现，save 调用被拒绝: fileNodeId={}",
                comment.getFileNodeId());
        throw new UnsupportedOperationException("文件评论功能尚未实现");
    }

    @Override
    public FileComment findById(String id) {
        return null;
    }

    @Override
    public List<FileComment> findByFileNodeId(String fileNodeId) {
        return Collections.emptyList();
    }

    @Override
    public List<FileComment> findReplies(String parentCommentId) {
        return Collections.emptyList();
    }

    @Override
    public void update(FileComment comment) {
        log.warn("[FileCommentRepositoryImpl] 评论功能未实现，update 调用被拒绝: id={}",
                comment.getId());
        throw new UnsupportedOperationException("文件评论功能尚未实现");
    }

    @Override
    public void delete(String id) {
        log.warn("[FileCommentRepositoryImpl] 评论功能未实现，delete 调用被拒绝: id={}", id);
        throw new UnsupportedOperationException("文件评论功能尚未实现");
    }

    @Override
    public void markResolved(String id, String userId) {
        log.warn("[FileCommentRepositoryImpl] 评论功能未实现，markResolved 调用被拒绝: id={}", id);
        throw new UnsupportedOperationException("文件评论功能尚未实现");
    }
}
