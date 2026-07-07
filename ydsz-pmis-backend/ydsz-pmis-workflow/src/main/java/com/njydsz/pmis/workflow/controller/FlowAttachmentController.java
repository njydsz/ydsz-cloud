package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.workflow.dto.FlowAttachmentPreviewVO;
import com.njydsz.pmis.workflow.entity.FlowAttachmentDO;
import com.njydsz.pmis.workflow.service.FlowAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审批附件 Controller
 *
 * <p>P1-6 (GAP-51): 审批附件的查询与删除接口。
 * 文件二进制上传由统一文件服务（OSS/MinIO）处理，此处仅管理附件元数据。
 *
 * <p>P2-3: 新增在线预览接口，根据文件类型返回预览策略与预览 URL。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "workflow-attachment", description = "工作流审批附件接口")
@RequestMapping("/workflow/engine")
@RequiredArgsConstructor
public class FlowAttachmentController {

    private final FlowAttachmentService attachmentService;

    /**
     * 查询任务附件
     */
    @GetMapping("/attachment/task/{taskId}")
    public Result<List<FlowAttachmentDO>> listByTask(@PathVariable String taskId) {
        return Result.ok(attachmentService.listByTask(taskId));
    }

    /**
     * 查询实例附件
     */
    @GetMapping("/attachment/instance/{instanceId}")
    public Result<List<FlowAttachmentDO>> listByInstance(@PathVariable String instanceId) {
        return Result.ok(attachmentService.listByInstance(instanceId));
    }

    /**
     * 删除附件（逻辑删除）
     */
    @DeleteMapping("/attachment/{attachmentId}")
    public Result<Void> delete(@PathVariable String attachmentId,
                               @RequestParam String operatorId) {
        attachmentService.delete(attachmentId, operatorId);
        return Result.ok();
    }

    /**
     * P2-3: 附件在线预览 — 根据文件类型返回预览策略与预览 URL。
     *
     * <p>前端根据 {@code previewType} 选择渲染方式：
     * <ul>
     *   <li>IMAGE → {@code <img src=previewUrl>}</li>
     *   <li>PDF → {@code <iframe src=previewUrl>} 或 PDF.js</li>
     *   <li>VIDEO → {@code <video src=previewUrl>}</li>
     *   <li>TEXT → fetch 后 {@code <pre>} 渲染</li>
     *   <li>OFFICE → {@code <iframe src=previewUrl>}（外部预览服务）</li>
     *   <li>UNSUPPORTED → 引导下载（downloadUrl）</li>
     * </ul>
     *
     * @param attachmentId 附件 ID
     * @return 统一响应结果，包含预览 VO
     */
    @GetMapping("/attachment/{attachmentId}/preview")
    @Operation(summary = "附件在线预览（根据文件类型返回预览策略）")
    public Result<FlowAttachmentPreviewVO> preview(@PathVariable String attachmentId) {
        return Result.ok(attachmentService.previewAttachment(attachmentId));
    }
}
