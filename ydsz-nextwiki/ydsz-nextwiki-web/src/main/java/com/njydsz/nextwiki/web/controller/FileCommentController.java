package com.njydsz.nextwiki.web.controller;

import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.auth.constant.AuthHeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.nextwiki.domain.entity.FileComment;
import com.njydsz.nextwiki.domain.repository.FileCommentRepository;

/**
 * 文件评论 REST API Controller（P1-5）。
 *
 * <p>提供网盘文件的评论、回复、批注、解决标记能力，是网盘"协作审阅"特性的核心接口：
 * <ul>
 *   <li>{@code GET /comments/file/{fileNodeId}} - 查询文件的所有评论（含回复）</li>
 *   <li>{@code POST /comments} - 添加评论或回复（支持 {@code parentCommentId} 嵌套回复）</li>
 *   <li>{@code DELETE /comments/{id}} - 删除评论</li>
 *   <li>{@code POST /comments/{id}/resolve} - 标记评论已解决</li>
 * </ul>
 *
 * <h3>核心能力</h3>
 * <ul>
 *   <li>嵌套回复：通过 {@code parentCommentId} 实现评论的树形回复结构</li>
 *   <li>位置批注：{@code position} 字段支持文档内位置定位（行号/偏移量），用于精确批注</li>
 *   <li>解决状态：评论可标记为 resolved（已解决），便于审阅流程闭环</li>
 *   <li>编辑追踪：{@code edited} / {@code revision} 字段记录编辑历史</li>
 * </ul>
 *
 * <h3>安全与稳定性</h3>
 * <ul>
 *   <li>所有写操作均加 {@link Idempotent} 防重（5s TTL）</li>
 *   <li>所有写操作均加 {@link Audit} 异步落库审计日志</li>
 *   <li>所有接口加 {@link AuthApiPermission} 权限码校验（NEXTWIKI_FILE_*）</li>
 *   <li>评论软删除（{@code deleted=1}），保留审计追溯</li>
 * </ul>
 *
 * <h3>接口路径</h3>
 * <pre>
 *   GET    /api/v1/nextwiki/comments/file/{fileNodeId} - 评论列表
 *   POST   /api/v1/nextwiki/comments                  - 添加评论/回复
 *   DELETE /api/v1/nextwiki/comments/{id}             - 删除评论
 *   POST   /api/v1/nextwiki/comments/{id}/resolve     - 标记已解决
 * </pre>
 *
 * <h3>架构位置</h3>
 * <pre>
 *   前端 (PC Web) → ydsz-gateway → ydsz-nextwiki-web (本 Controller)
 *                                            ↓
 *                                   ydsz-nextwiki-domain.FileCommentRepository
 *                                            ↓
 *                                   ydsz-nextwiki-infra Mapper
 *                                            ↓
 *                                   ydsz_file_comment
 * </pre>
 *
 * <h3>实现状态</h3>
 * <p>当前为 stub 实现，infra 层 {@link FileCommentRepository} 仅有空壳 Bean，
 * 所有接口写操作会返回 501 错误。详见 P1-5 待排期。
 *
 * TODO: 待接入 {@code nw_file_comment} 表 + FileCommentMapper 后启用完整评论能力
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/nextwiki/comments")
@RequiredArgsConstructor
@Tag(name = "文件评论", description = "文件级评论、回复、批注、解决标记")
public class FileCommentController {

    /** 分布式 ID 生成器 */
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    /** 文件评论仓储（封装评论的 CRUD + 解决标记） */
    private final FileCommentRepository commentRepository;

    /**
     * 查询文件的所有评论（按时间升序）。
     *
     * <p>返回所有未删除的评论，包括顶级评论和它们的回复（前端自行组装树形结构）。
     *
     * @param fileNodeId 文件节点 ID
     * @return 统一响应结果，data 为 {@link FileComment} 列表
     */
    @GetMapping("/file/{fileNodeId}")
    @Operation(summary = "查询文件的评论列表")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_VIEW)
    public BaseResponse<List<FileComment>> listComments(@PathVariable String fileNodeId) {
        return BaseResponse.success(commentRepository.findByFileNodeId(fileNodeId));
    }

    /**
     * 添加评论或回复。
     *
     * <p>支持两种用法：
     * <ul>
     *   <li>顶级评论：不传 {@code parentCommentId}</li>
     *   <li>回复评论：传 {@code parentCommentId} 指向被回复的评论</li>
     * </ul>
     * {@code position} 字段支持文档内位置定位（如行号 10、字符偏移 100）。
     *
     * @param request 评论请求（fileNodeId / content / parentCommentId / position）
     * @param userId  评论人 ID
     * @return 统一响应结果，data 为保存后的 {@link FileComment}
     */
    @Audit(module = "文件评论", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'addComment'")
    @Idempotent(key = "ydsz:nextwiki:FileCommentController:addComment:lock", ttlSeconds = 5)
    @PostMapping
    @Operation(summary = "添加评论/回复")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<FileComment> addComment(
            @RequestBody AddCommentRequest request,
            @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {

        FileComment comment = FileComment.builder()
                .id(String.valueOf(snowflakeIdGenerator.nextId()).replace("-", ""))
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

    /**
     * 删除评论（软删除，{@code deleted=1}）。
     *
     * <p>软删除而非物理删除，便于审计追溯和上下文保留。
     * 子评论（回复）不会被级联删除，但会随父评论在列表中隐藏。
     *
     * @param commentId 评论 ID
     * @param userId    操作人 ID
     * @return 统一响应结果
     */
    @Audit(module = "文件评论", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'deleteComment'")
    @Idempotent(key = "ydsz:nextwiki:FileCommentController:deleteComment:lock", ttlSeconds = 5)
    @DeleteMapping("/{commentId}")
    @Operation(summary = "删除评论")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_DELETE)
    public BaseResponse<Void> deleteComment(
            @PathVariable String commentId,
            @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
        commentRepository.delete(commentId);
        return BaseResponse.success();
    }

    /**
     * 标记评论为已解决。
     *
     * <p>由评论作者或文件所有者触发；标记后评论会从"未解决"列表中移除。
     * 解决操作记录 resolvedBy / resolvedAt 字段。
     *
     * @param commentId 评论 ID
     * @param userId    操作人 ID
     * @return 统一响应结果
     */
    @Audit(module = "文件评论", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'resolveComment'")
    @Idempotent(key = "ydsz:nextwiki:FileCommentController:resolveComment:lock", ttlSeconds = 5)
    @PostMapping("/{commentId}/resolve")
    @Operation(summary = "标记评论已解决")
    @AuthApiPermission(apiCodes = PermissionCodes.NEXTWIKI_FILE_UPLOAD)
    public BaseResponse<Void> resolveComment(
            @PathVariable String commentId,
            @RequestHeader(AuthHeaderConstants.X_USER_ID) String userId) {
        commentRepository.markResolved(commentId, userId);
        return BaseResponse.success();
    }

    /**
     * 添加评论请求体（顶级评论 / 回复）。
     */
    public static class AddCommentRequest {
        /** 文件节点 ID */
        private String fileNodeId;
        /** 评论内容（纯文本/Markdown） */
        private String content;
        /** 父评论 ID（回复时填写，顶级评论为空） */
        private String parentCommentId;
        /** 文档内位置（如行号/偏移量，可选） */
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
