package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.workflow.entity.FlowAttachmentDO;
import com.njydsz.pmis.workflow.service.FlowAttachmentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审批附件 Controller
 *
 * <p>P1-6 (GAP-51): 审批附件的查询与删除接口。
 * 文件二进制上传由统一文件服务（OSS/MinIO）处理，此处仅管理附件元数据。
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
}
