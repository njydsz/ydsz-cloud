package com.njydsz.pmis.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.workflow.dto.DeployProcessDTO;
import com.njydsz.pmis.workflow.dto.StartProcessDTO;
import com.njydsz.pmis.workflow.dto.TaskOperateDTO;
import com.njydsz.pmis.workflow.entity.WorkflowBusinessDO;
import com.njydsz.pmis.workflow.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 工作流核心 Controller
 */
@Tag(name = "工作流 - 核心")
@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    // ==================== 流程定义 ====================

    @Operation(summary = "部署流程（XML 字符串）")
    @PostMapping("/definition/deploy")
    public R<String> deploy(@RequestBody DeployProcessDTO dto) {
        return R.ok(workflowService.deploy(dto));
    }

    @Operation(summary = "部署流程（上传 BPMN 文件）")
    @PostMapping("/definition/deploy/file")
    public R<String> deployFile(@RequestPart("file") MultipartFile file,
                                 @RequestParam(required = false) String category) throws Exception {
        try (InputStream in = file.getInputStream()) {
            DeployProcessDTO dto = new DeployProcessDTO();
            dto.setName(file.getOriginalFilename());
            dto.setCategory(category);
            byte[] bytes = in.readAllBytes();
            dto.setBpmnXml(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            return R.ok(workflowService.deploy(dto));
        }
    }

    @Operation(summary = "分页查询流程定义")
    @GetMapping("/definition/page")
    public R<Page<ProcessDefinition>> pageDefinitions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String key) {
        return R.ok(workflowService.pageDefinitions(page, size, category, key));
    }

    @Operation(summary = "获取最新版本流程定义")
    @GetMapping("/definition/latest")
    public R<ProcessDefinition> latestDefinition(@RequestParam String processKey) {
        return R.ok(workflowService.getLatestDefinition(processKey));
    }

    @Operation(summary = "挂起流程定义")
    @PostMapping("/definition/{id}/suspend")
    public R<Void> suspendDefinition(@PathVariable("id") String processDefinitionId) {
        workflowService.suspendDefinition(processDefinitionId);
        return R.ok();
    }

    @Operation(summary = "激活流程定义")
    @PostMapping("/definition/{id}/activate")
    public R<Void> activateDefinition(@PathVariable("id") String processDefinitionId) {
        workflowService.activateDefinition(processDefinitionId);
        return R.ok();
    }

    @Operation(summary = "删除流程定义（部署级）")
    @DeleteMapping("/definition/deployment/{deploymentId}")
    public R<Void> deleteDefinition(@PathVariable String deploymentId,
                                    @RequestParam(defaultValue = "false") boolean cascade) {
        workflowService.deleteDefinition(deploymentId, cascade);
        return R.ok();
    }

    // ==================== 流程实例 ====================

    @Operation(summary = "启动流程")
    @PostMapping("/instance/start")
    public R<String> startProcess(@RequestBody @Valid StartProcessDTO dto) {
        return R.ok(workflowService.startProcess(dto));
    }

    @Operation(summary = "挂起流程实例")
    @PostMapping("/instance/{id}/suspend")
    public R<Void> suspendInstance(@PathVariable("id") String processInstanceId) {
        workflowService.suspendInstance(processInstanceId);
        return R.ok();
    }

    @Operation(summary = "激活流程实例")
    @PostMapping("/instance/{id}/activate")
    public R<Void> activateInstance(@PathVariable("id") String processInstanceId) {
        workflowService.activateInstance(processInstanceId);
        return R.ok();
    }

    @Operation(summary = "终止流程实例")
    @DeleteMapping("/instance/{id}")
    public R<Void> terminateInstance(@PathVariable("id") String processInstanceId,
                                     @RequestParam(required = false) String reason) {
        workflowService.terminateInstance(processInstanceId, reason);
        return R.ok();
    }

    @Operation(summary = "查询流程变量")
    @GetMapping("/instance/{id}/variables")
    public R<Map<String, Object>> getInstanceVariables(@PathVariable("id") String processInstanceId) {
        return R.ok(workflowService.getInstanceVariables(processInstanceId));
    }

    // ==================== 任务 ====================

    @Operation(summary = "待办任务")
    @GetMapping("/task/todo")
    public R<List<Map<String, Object>>> todoTasks(
            @Parameter(description = "用户 ID") @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(workflowService.listTodoTasks(userId, page, size));
    }

    @Operation(summary = "已办任务")
    @GetMapping("/task/done")
    public R<List<Map<String, Object>>> doneTasks(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(workflowService.listDoneTasks(userId, page, size));
    }

    @Operation(summary = "完成任务（审批通过）")
    @PostMapping("/task/complete")
    public R<Void> completeTask(@RequestBody TaskOperateDTO dto) {
        workflowService.completeTask(dto);
        return R.ok();
    }

    @Operation(summary = "签收任务")
    @PostMapping("/task/{id}/claim")
    public R<Void> claimTask(@PathVariable("id") String taskId,
                              @RequestParam Long userId) {
        workflowService.claimTask(taskId, userId);
        return R.ok();
    }

    @Operation(summary = "退回任务")
    @PostMapping("/task/reject")
    public R<Void> rejectTask(@RequestBody TaskOperateDTO dto) {
        workflowService.rejectTask(dto);
        return R.ok();
    }

    @Operation(summary = "委派任务")
    @PostMapping("/task/delegate")
    public R<Void> delegateTask(@RequestBody TaskOperateDTO dto) {
        workflowService.delegateTask(dto);
        return R.ok();
    }

    @Operation(summary = "转办任务")
    @PostMapping("/task/transfer")
    public R<Void> transferTask(@RequestBody TaskOperateDTO dto) {
        workflowService.transferTask(dto);
        return R.ok();
    }

    // ==================== 业务关联 ====================

    @Operation(summary = "反查业务单据关联的流程")
    @GetMapping("/business/by-business")
    public R<WorkflowBusinessDO> getByBusiness(@RequestParam String businessType,
                                              @RequestParam String businessId) {
        return R.ok(workflowService.getByBusiness(businessType, businessId));
    }

    @Operation(summary = "反查流程实例关联的业务")
    @GetMapping("/business/by-process")
    public R<WorkflowBusinessDO> getByProcessInstance(@RequestParam String processInstanceId) {
        return R.ok(workflowService.getByProcessInstance(processInstanceId));
    }
}
