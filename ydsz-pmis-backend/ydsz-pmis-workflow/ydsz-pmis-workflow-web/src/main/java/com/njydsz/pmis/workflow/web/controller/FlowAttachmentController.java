package com.njydsz.pmis.workflow.web.controller.integration;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.workflow.domain.dto.FlowAttachmentPreviewVO;
import com.njydsz.pmis.workflow.domain.entity.FlowAttachmentDO;
import com.njydsz.pmis.workflow.server.service.FlowAttachmentService;
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

    /** 审批附件服务，负责附件元数据管理与在线预览 */
    private final FlowAttachmentService attachmentService;

    /**
     * 查询任务附件。
     *
     * @param taskId 任务 ID
     * @return 附件列表
     */
    @GetMapping("/attachment/task/{taskId}")
    public BaseResponse<List<FlowAttachmentDO>> listByTask(@PathVariable String taskId) {
        return BaseResponse.ok(attachmentService.listByTask(taskId));
    }

    /**
     * 查询实例附件。
     *
     * @param instanceId 流程实例 ID
     * @return 附件列表
     */
    @GetMapping("/attachment/instance/{instanceId}")
    public BaseResponse<List<FlowAttachmentDO>> listByInstance(@PathVariable String instanceId) {
        return BaseResponse.ok(attachmentService.listByInstance(instanceId));
    }

    /**
     * 删除附件（逻辑删除）。
     *
     * @param attachmentId 附件 ID
     * @param operatorId   操作人 ID
     * @return 空响应
     */
    @Idempotent(key = "flowAttachment:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/attachment/{attachmentId}")
    public BaseResponse<Void> delete(@PathVariable String attachmentId,
                               @RequestParam String operatorId) {
        attachmentService.delete(attachmentId, operatorId);
        return BaseResponse.ok();
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
    public BaseResponse<FlowAttachmentPreviewVO> preview(@PathVariable String attachmentId) {
        return BaseResponse.ok(attachmentService.previewAttachment(attachmentId));
    }
}
