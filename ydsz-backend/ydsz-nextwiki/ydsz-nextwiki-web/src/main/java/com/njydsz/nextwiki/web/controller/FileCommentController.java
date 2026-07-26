package com.njydsz.nextwiki.web.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.nextwiki.domain.entity.FileComment;
import com.njydsz.nextwiki.domain.repository.FileCommentRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.lock.annotation.Idempotent;

/**
 * 文件评论 REST API（P1-5）
 * <p>
 * 支持文件级别的评论、回复、标记已解决。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/comments")
@RequiredArgsConstructor
@Tag(name = "文件评论", description = "文件级评论、回复、批注")
public class FileCommentController {

    private final FileCommentRepository commentRepository;

    @GetMapping("/file/{fileNodeId}")
    @Operation(summary = "查询文件的评论列表")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VIEW)
    public BaseResponse<List<FileComment>> listComments(@PathVariable String fileNodeId) {
        return BaseResponse.success(commentRepository.findByFileNodeId(fileNodeId));
    }

    @Audit(module = "文件评论", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'addComment'")
    @Idempotent(key = "nextwiki:comment:addComment", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    @Operation(summary = "添加评论/回复")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<FileComment> addComment(
            @RequestBody AddCommentRequest request,
            @RequestHeader("X-User-Id") String userId) {

        FileComment comment = FileComment.builder()
                .id(UUID.randomUUID().toString().replace("-", ""))
                .fileNodeId(request.getFileNodeId())
                .content(request.getContent())
                .parentCommentId(request.getParentCommentId())
                .position(request.getPosition())
                .resolved(false)
                .edited(false)
                .revision(0)
                .deleted(0)
                .build();
        comment.setCreatedBy(userId);
        comment.setCreatedAt(LocalDateTime.now());
        comment.setUpdatedBy(userId);
        comment.setUpdatedAt(LocalDateTime.now());

        FileComment saved = commentRepository.save(comment);
        log.info("[FileCommentController] 添加评论: fileNodeId={}, commentId={}",
                request.getFileNodeId(), saved.getId());
        return BaseResponse.success(saved);
    }

    @Audit(module = "文件评论", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'deleteComment'")
    @Idempotent(key = "nextwiki:comment:deleteComment", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{commentId}")
    @Operation(summary = "删除评论")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_DELETE)
    public BaseResponse<Void> deleteComment(
            @PathVariable String commentId,
            @RequestHeader("X-User-Id") String userId) {
        commentRepository.delete(commentId);
        return BaseResponse.success();
    }

    @Audit(module = "文件评论", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'resolveComment'")
    @Idempotent(key = "nextwiki:comment:resolveComment", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{commentId}/resolve")
    @Operation(summary = "标记评论已解决")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<Void> resolveComment(
            @PathVariable String commentId,
            @RequestHeader("X-User-Id") String userId) {
        commentRepository.markResolved(commentId, userId);
        return BaseResponse.success();
    }

    /**
     * 添加评论请求
     */
    public static class AddCommentRequest {
        private String fileNodeId;
        private String content;
        private String parentCommentId;
        private String position;

        public String getFileNodeId() { return fileNodeId; }
        public void setFileNodeId(String fileNodeId) { this.fileNodeId = fileNodeId; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getParentCommentId() { return parentCommentId; }
        public void setParentCommentId(String parentCommentId) { this.parentCommentId = parentCommentId; }
        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }
    }
}
