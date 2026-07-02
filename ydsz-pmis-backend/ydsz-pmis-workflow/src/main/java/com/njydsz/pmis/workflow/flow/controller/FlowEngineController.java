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

    /** 工作流门面，业务调用入口 */
    private final WorkflowFacade workflowFacade;
    /** 流程定义服务 */
    private final FlowDefinitionService definitionService;

    // ============== 引擎信息 ==============

    /**
     * 查询引擎信息
     *
     * @return 统一响应结果，包含引擎类型与可用性
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        return Result.ok(Map.of(
                "engineType", workflowFacade.engineType(),
                "available", true
        ));
    }

    // ============== 流程定义（管理） ==============

    /**
     * 部署流程定义
     *
     * @param dto 流程部署参数
     * @return 统一响应结果，包含流程定义 ID
     */
    @PostMapping("/definition/deploy")
    public Result<Long> deploy(@RequestBody FlowDeployProcessDTO dto) {
        Long id = definitionService.deploy(dto);
        return Result.ok(id);
    }

    /**
     * 发布流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        definitionService.publish(id);
        return Result.ok();
    }

    /**
     * 废弃流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/deprecate")
    public Result<Void> deprecate(@PathVariable Long id) {
        definitionService.deprecate(id);
        return Result.ok();
    }

    /**
     * 按编码查询已发布流程定义
     *
     * @param code      流程编码
     * @param version   版本号（可选）
     * @param tenantId  租户 ID（可选）
     * @return 统一响应结果，包含流程定义
     */
    @GetMapping("/definition/code/{code}")
    public Result<FlowDefinitionDO> getByCode(@PathVariable String code,
                                          @RequestParam(required = false) String version,
                                          @RequestParam(required = false) Long tenantId) {
        return Result.ok(definitionService.getPublished(code, version, tenantId));
    }

    /**
     * 分页查询流程定义
     *
     * @param pageNo   页码
     * @param pageSize 每页大小
     * @param category 分类（可选）
     * @param flowCode 流程编码（可选）
     * @return 统一响应结果，包含流程定义列表
     */
    @GetMapping("/definition/page")
    public Result<List<FlowDefinitionDO>> page(@RequestParam(defaultValue = "1") int pageNo,
                                          @RequestParam(defaultValue = "20") int pageSize,
                                          @RequestParam(required = false) String category,
                                          @RequestParam(required = false) String flowCode) {
        return Result.ok(definitionService.page(pageNo, pageSize, category, flowCode));
    }

    // ============== 流程实例 ==============

    /**
     * 启动流程实例
     *
     * @param dto 流程启动参数
     * @return 统一响应结果，包含流程实例 ID
     */
    @PostMapping("/instance/start")
    public Result<String> startProcess(@RequestBody FlowStartProcessDTO dto) {
        return Result.ok(workflowFacade.startProcess(dto));
    }

    /**
     * 按业务类型与业务 ID 查询流程实例
     *
     * @param businessType 业务类型
     * @param businessId   业务 ID
     * @return 统一响应结果，包含流程实例视图
     */
    @GetMapping("/instance/byBusiness")
    public Result<FlowInstanceViewDTO> getByBusiness(@RequestParam String businessType,
                                                 @RequestParam String businessId) {
        return Result.ok(workflowFacade.getByBusiness(businessType, businessId));
    }

    /**
     * 终止流程实例
     *
     * @param id     流程实例 ID
     * @param reason 终止原因（可选）
     * @return 统一响应结果
     */
    @PostMapping("/instance/{id}/terminate")
    public Result<Void> terminate(@PathVariable String id, @RequestParam(required = false) String reason) {
        workflowFacade.terminateProcess(id, reason);
        return Result.ok();
    }

    /**
     * 挂起流程实例
     *
     * @param id 流程实例 ID
     * @return 统一响应结果
     */
    @PostMapping("/instance/{id}/suspend")
    public Result<Void> suspend(@PathVariable String id) {
        workflowFacade.suspendProcess(id);
        return Result.ok();
    }

    /**
     * 激活流程实例
     *
     * @param id 流程实例 ID
     * @return 统一响应结果
     */
    @PostMapping("/instance/{id}/activate")
    public Result<Void> activate(@PathVariable String id) {
        workflowFacade.activateProcess(id);
        return Result.ok();
    }

    /**
     * 撤回流程（仅发起人可撤回，仅运行中可撤回）
     *
     * @param id         流程实例 ID
     * @param initiatorId 发起人 ID
     * @return 统一响应结果，包含是否撤回成功
     */
    @PostMapping("/instance/{id}/recall")
    public Result<Boolean> recall(@PathVariable String id, @RequestParam Long initiatorId) {
        return Result.ok(workflowFacade.recallProcess(id, initiatorId));
    }

    /**
     * 审计轨迹查询
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含审计轨迹列表
     */
    @GetMapping("/instance/{id}/auditTrail")
    public Result<List<Map<String, Object>>> auditTrail(@PathVariable String id) {
        return Result.ok(workflowFacade.listAuditTrail(id));
    }

    // ============== 任务操作 ==============

    /**
     * 签收任务
     *
     * @param taskId 任务 ID
     * @param userId 用户 ID
     * @return 统一响应结果
     */
    @PostMapping("/task/claim")
    public Result<Void> claim(@RequestParam Long taskId, @RequestParam Long userId) {
        workflowFacade.claimTask(taskId, userId);
        return Result.ok();
    }

    /**
     * 通过任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/pass")
    public Result<Void> pass(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.completeTask(dto);
        return Result.ok();
    }

    /**
     * 驳回任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/reject")
    public Result<Void> reject(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.rejectTask(dto);
        return Result.ok();
    }

    /**
     * 转办任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/transfer")
    public Result<Void> transfer(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.transferTask(dto);
        return Result.ok();
    }

    /**
     * 委派任务
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/delegate")
    public Result<Void> delegate(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.delegateTask(dto);
        return Result.ok();
    }

    /**
     * 前加签
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/countersignBefore")
    public Result<Void> countersignBefore(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.countersignBeforeTask(dto);
        return Result.ok();
    }

    /**
     * 后加签
     *
     * @param dto 任务操作参数
     * @return 统一响应结果
     */
    @PostMapping("/task/countersignAfter")
    public Result<Void> countersignAfter(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.countersignAfterTask(dto);
        return Result.ok();
    }

    /**
     * 催办
     *
     * @param id         流程实例 ID
     * @param operatorId 操作人 ID
     * @param comment    催办备注（可选）
     * @return 统一响应结果，包含被催办人列表
     */
    @PostMapping("/instance/{id}/urge")
    public Result<List<String>> urge(@PathVariable Long id,
                                 @RequestParam Long operatorId,
                                 @RequestParam(required = false) String comment) {
        return Result.ok(workflowFacade.urgeTask(id, operatorId, comment));
    }

    /**
     * 待办任务查询
     *
     * @param userId 用户 ID
     * @param page   页码
     * @param size   每页大小
     * @return 统一响应结果，包含待办任务列表
     */
    @GetMapping("/task/todo")
    public Result<List<Map<String, Object>>> todo(@RequestParam Long userId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(workflowFacade.listTodoTasks(userId, page, size));
    }

    /**
     * 已办任务查询
     *
     * @param userId 用户 ID
     * @param page   页码
     * @param size   每页大小
     * @return 统一响应结果，包含已办任务列表
     */
    @GetMapping("/task/done")
    public Result<List<Map<String, Object>>> done(@RequestParam Long userId,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return Result.ok(workflowFacade.listDoneTasks(userId, page, size));
    }
}
