package com.njydsz.pmis.workflow.flow.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.workflow.flow.WorkflowFacade;
import com.njydsz.pmis.workflow.flow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.flow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.flow.service.FlowDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 自研工作流引擎 HTTP API
 *
 * <p>用于管理 pmis_flow_* 表和自建引擎操作。
 * 业务调用方（project/execution/closure）应使用 WorkflowFacade 而非直接调用本 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/engine")
@RequiredArgsConstructor
public class FlowEngineController {

    private final WorkflowFacade workflowFacade;
    private final FlowDefinitionService definitionService;

    // ============== 引擎信息 ==============

    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        return Result.ok(Map.of(
                "engineType", workflowFacade.engineType(),
                "available", true
        ));
    }

    // ============== 流程定义（管理） ==============

    @PostMapping("/definition/deploy")
    public Result<Long> deploy(@RequestBody FlowDeployProcessDTO dto) {
        Long id = definitionService.deploy(dto);
        return Result.ok(id);
    }

    @PostMapping("/definition/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        definitionService.publish(id);
        return Result.ok();
    }

    @PostMapping("/definition/{id}/deprecate")
    public Result<Void> deprecate(@PathVariable Long id) {
        definitionService.deprecate(id);
        return Result.ok();
    }

    @GetMapping("/definition/code/{code}")
    public Result<FlowDefinitionDO> getByCode(@PathVariable String code,
                                          @RequestParam(required = false) String version,
                                          @RequestParam(required = false) Long tenantId) {
        return Result.ok(definitionService.getPublished(code, version, tenantId));
    }

    @GetMapping("/definition/page")
    public Result<List<FlowDefinitionDO>> page(@RequestParam(defaultValue = "1") int pageNo,
                                          @RequestParam(defaultValue = "20") int pageSize,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String flowCode) {
        return Result.ok(definitionService.page(pageNo, pageSize, category, flowCode));
    }

    // ============== 流程实例 ==============

    @PostMapping("/instance/start")
    public Result<String> startProcess(@RequestBody FlowStartProcessDTO dto) {
        return Result.ok(workflowFacade.startProcess(dto));
    }

    @GetMapping("/instance/byBusiness")
    public Result<FlowInstanceViewDTO> getByBusiness(@RequestParam String businessType,
                                                 @RequestParam String businessId) {
        return Result.ok(workflowFacade.getByBusiness(businessType, businessId));
    }

    @PostMapping("/instance/{id}/terminate")
    public Result<Void> terminate(@PathVariable String id, @RequestParam(required = false) String reason) {
        workflowFacade.terminateProcess(id, reason);
        return Result.ok();
    }

    @PostMapping("/instance/{id}/suspend")
    public Result<Void> suspend(@PathVariable String id) {
        workflowFacade.suspendProcess(id);
        return Result.ok();
    }

    @PostMapping("/instance/{id}/activate")
    public Result<Void> activate(@PathVariable String id) {
        workflowFacade.activateProcess(id);
        return Result.ok();
    }

    // P1-8: 撤回流程（仅发起人可撤回，仅运行中可撤回）
    @PostMapping("/instance/{id}/recall")
    public Result<Boolean> recall(@PathVariable String id, @RequestParam Long initiatorId) {
        return Result.ok(workflowFacade.recallProcess(id, initiatorId));
    }

    // P1-13: 审计轨迹查询
    @GetMapping("/instance/{id}/auditTrail")
    public Result<List<Map<String, Object>>> auditTrail(@PathVariable String id) {
        return Result.ok(workflowFacade.listAuditTrail(id));
    }

    // ============== 任务操作 ==============

    @PostMapping("/task/claim")
    public Result<Void> claim(@RequestParam Long taskId, @RequestParam Long userId) {
        workflowFacade.claimTask(taskId, userId);
        return Result.ok();
    }

    @PostMapping("/task/pass")
    public Result<Void> pass(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.completeTask(dto);
        return Result.ok();
    }

    @PostMapping("/task/reject")
    public Result<Void> reject(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.rejectTask(dto);
        return Result.ok();
    }

    @PostMapping("/task/transfer")
    public Result<Void> transfer(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.transferTask(dto);
        return Result.ok();
    }

    @PostMapping("/task/delegate")
    public Result<Void> delegate(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.delegateTask(dto);
        return Result.ok();
    }

    // P1-7: 前加签
    @PostMapping("/task/countersignBefore")
    public Result<Void> countersignBefore(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.countersignBeforeTask(dto);
        return Result.ok();
    }

    // P1-7: 后加签
    @PostMapping("/task/countersignAfter")
    public Result<Void> countersignAfter(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.countersignAfterTask(dto);
        return Result.ok();
    }

    // P1-9: 催办
    @PostMapping("/instance/{id}/urge")
    public Result<List<String>> urge(@PathVariable Long id,
                                 @RequestParam Long operatorId,
                                 @RequestParam(required = false) String comment) {
        return Result.ok(workflowFacade.urgeTask(id, operatorId, comment));
    }

    @GetMapping("/task/todo")
    public Result<List<Map<String, Object>>> todo(@RequestParam Long userId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(workflowFacade.listTodoTasks(userId, page, size));
    }

    @GetMapping("/task/done")
    public Result<List<Map<String, Object>>> done(@RequestParam Long userId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(workflowFacade.listDoneTasks(userId, page, size));
    }
}
