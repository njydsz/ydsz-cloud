package com.njydsz.pmis.workflow.flow.controller;

import com.njydsz.pmis.common.api.R;
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
    public R<Map<String, Object>> info() {
        return R.ok(Map.of(
                "engineType", workflowFacade.engineType(),
                "available", true
        ));
    }

    // ============== 流程定义（管理） ==============

    @PostMapping("/definition/deploy")
    public R<Long> deploy(@RequestBody FlowDeployProcessDTO dto) {
        Long id = definitionService.deploy(dto);
        return R.ok(id);
    }

    @PostMapping("/definition/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        definitionService.publish(id);
        return R.ok();
    }

    @PostMapping("/definition/{id}/deprecate")
    public R<Void> deprecate(@PathVariable Long id) {
        definitionService.deprecate(id);
        return R.ok();
    }

    @GetMapping("/definition/code/{code}")
    public R<FlowDefinitionDO> getByCode(@PathVariable String code,
                                          @RequestParam(required = false) String version,
                                          @RequestParam(required = false) Long tenantId) {
        return R.ok(definitionService.getPublished(code, version, tenantId));
    }

    @GetMapping("/definition/page")
    public R<List<FlowDefinitionDO>> page(@RequestParam(defaultValue = "1") int pageNo,
                                          @RequestParam(defaultValue = "20") int pageSize,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String flowCode) {
        return R.ok(definitionService.page(pageNo, pageSize, category, flowCode));
    }

    // ============== 流程实例 ==============

    @PostMapping("/instance/start")
    public R<String> startProcess(@RequestBody FlowStartProcessDTO dto) {
        return R.ok(workflowFacade.startProcess(dto));
    }

    @GetMapping("/instance/byBusiness")
    public R<FlowInstanceViewDTO> getByBusiness(@RequestParam String businessType,
                                                 @RequestParam String businessId) {
        return R.ok(workflowFacade.getByBusiness(businessType, businessId));
    }

    @PostMapping("/instance/{id}/terminate")
    public R<Void> terminate(@PathVariable String id, @RequestParam(required = false) String reason) {
        workflowFacade.terminateProcess(id, reason);
        return R.ok();
    }

    @PostMapping("/instance/{id}/suspend")
    public R<Void> suspend(@PathVariable String id) {
        workflowFacade.suspendProcess(id);
        return R.ok();
    }

    @PostMapping("/instance/{id}/activate")
    public R<Void> activate(@PathVariable String id) {
        workflowFacade.activateProcess(id);
        return R.ok();
    }

    // ============== 任务操作 ==============

    @PostMapping("/task/claim")
    public R<Void> claim(@RequestParam Long taskId, @RequestParam Long userId) {
        workflowFacade.claimTask(taskId, userId);
        return R.ok();
    }

    @PostMapping("/task/pass")
    public R<Void> pass(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.completeTask(dto);
        return R.ok();
    }

    @PostMapping("/task/reject")
    public R<Void> reject(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.rejectTask(dto);
        return R.ok();
    }

    @PostMapping("/task/transfer")
    public R<Void> transfer(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.transferTask(dto);
        return R.ok();
    }

    @PostMapping("/task/delegate")
    public R<Void> delegate(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.delegateTask(dto);
        return R.ok();
    }

    @GetMapping("/task/todo")
    public R<List<Map<String, Object>>> todo(@RequestParam Long userId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return R.ok(workflowFacade.listTodoTasks(userId, page, size));
    }

    @GetMapping("/task/done")
    public R<List<Map<String, Object>>> done(@RequestParam Long userId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return R.ok(workflowFacade.listDoneTasks(userId, page, size));
    }
}
