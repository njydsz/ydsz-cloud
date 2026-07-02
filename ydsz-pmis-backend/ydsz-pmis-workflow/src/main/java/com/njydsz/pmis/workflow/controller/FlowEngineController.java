package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.dto.FlowCcQueryDTO;
import com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.entity.FlowCcDO;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.service.FlowCcService;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowDelegateAuthService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
    /** 流程实例服务（P2-23/P2-24 分页查询与变量读写） */
    private final FlowInstanceService instanceService;
    /** 任务服务（P2-31/32/33 耗时统计/超期统计/多维筛选） */
    private final FlowTaskService taskService;
    /** P0-3: 抄送服务 */
    private final FlowCcService ccService;
    /** P1-1: 历史任务 mapper（驳回候选目标节点） */
    private final FlowHisTaskMapper hisTaskMapper;
    /** P1-4: 长期授权委派服务 */
    private final FlowDelegateAuthService delegateAuthService;
    /** GAP-P2: 审批效率分析服务 */
    private final com.njydsz.pmis.workflow.service.FlowEfficiencyService efficiencyService;
    /** GAP-P2: 流程模板服务 */
    private final com.njydsz.pmis.workflow.service.FlowTemplateService templateService;
    /** P1-6: SLA 超时自动策略服务 */
    private final com.njydsz.pmis.workflow.service.FlowSlaService slaService;
    /** P1-7: WebSocket 待办数实时推送服务 */
    private final com.njydsz.pmis.workflow.service.FlowTodoCountPushService todoCountPushService;
    /** P2-1: 智能审批辅助服务（推荐审批人 / 起草意见） */
    private final com.njydsz.pmis.workflow.service.FlowAiAssistService aiAssistService;

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

    /**
     * P2-21: 流程定义详情查询（含节点 + 跳转）
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，包含 definition / nodes / skips
     */
    @GetMapping("/definition/{id}")
    public Result<Map<String, Object>> getDefinitionDetail(@PathVariable Long id) {
        return Result.ok(definitionService.getDetail(id));
    }

    /**
     * P2-27: 切换流程定义的激活版本
     *
     * @param code         流程编码
     * @param definitionId 目标流程定义 ID
     * @param tenantId     租户 ID（可选）
     * @return 统一响应结果
     */
    @PostMapping("/definition/{code}/switchVersion")
    public Result<Void> switchVersion(@PathVariable String code,
                                      @RequestParam Long definitionId,
                                      @RequestParam(required = false) Long tenantId) {
        definitionService.switchActiveVersion(code, definitionId, tenantId);
        return Result.ok();
    }

    /**
     * P2-28: 启用流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/enable")
    public Result<Void> enable(@PathVariable Long id) {
        definitionService.enable(id);
        return Result.ok();
    }

    /**
     * P2-28: 停用流程定义
     *
     * @param id 流程定义 ID
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        definitionService.disable(id);
        return Result.ok();
    }

    /**
     * P2-40: 更新节点坐标（供前端设计器保存布局）
     *
     * @param definitionId 流程定义 ID
     * @param nodeCode     节点编码
     * @param coordinate   坐标 JSON 字符串
     * @return 统一响应结果
     */
    @PostMapping("/definition/{definitionId}/node/{nodeCode}/coordinate")
    public Result<Void> updateNodeCoordinate(@PathVariable Long definitionId,
                                             @PathVariable String nodeCode,
                                             @RequestBody String coordinate) {
        definitionService.updateNodeCoordinate(definitionId, nodeCode, coordinate);
        return Result.ok();
    }

    /**
     * P2-41: 编辑未发布的流程定义草稿
     *
     * @param id  流程定义 ID
     * @param dto 部署参数（含更新后的元数据与节点/跳转）
     * @return 统一响应结果
     */
    @PutMapping("/definition/{id}")
    public Result<Void> updateDefinition(@PathVariable Long id,
                                         @RequestBody FlowDeployProcessDTO dto) {
        definitionService.updateDefinition(id, dto);
        return Result.ok();
    }

    /**
     * GAP-V2-06: 导出流程定义为 JSON（含定义元数据 + 节点 + 跳转）
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，包含 JSON 字符串
     */
    @GetMapping("/definition/{id}/export")
    public Result<String> exportDefinition(@PathVariable Long id) {
        return Result.ok(definitionService.exportDefinition(id));
    }

    /**
     * GAP-V2-06: 从 JSON 导入流程定义（创建为草稿）
     *
     * @param json     导出的 JSON 字符串
     * @param tenantId 租户 ID（可选，默认从上下文获取）
     * @return 统一响应结果，包含新创建的流程定义 ID
     */
    @PostMapping("/definition/import")
    public Result<Long> importDefinition(@RequestBody String json,
                                         @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(definitionService.importDefinition(json, tid));
    }

    /**
     * GAP-V2-08: 流程模拟运行 — 使用模拟变量驱动引擎走一遍流程，不创建实际实例
     *
     * @param flowCode  流程编码
     * @param version   版本号（可选，默认查最新已发布版本）
     * @param variables 模拟变量
     * @param tenantId  租户 ID（可选）
     * @return 统一响应结果，包含模拟路径列表
     */
    @PostMapping("/definition/simulate")
    public Result<List<Map<String, Object>>> simulate(@RequestParam String flowCode,
                                                       @RequestParam(required = false) String version,
                                                       @RequestBody Map<String, Object> variables,
                                                       @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(instanceService.simulate(flowCode, version, variables, tid));
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

    /**
     * P2-30: 审批轨迹时间线查询 — 合并历史任务 + 审计日志 + 当前待办为统一时间线
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含时间线列表
     */
    @GetMapping("/instance/{id}/timeline")
    public Result<List<Map<String, Object>>> timeline(@PathVariable String id) {
        return Result.ok(workflowFacade.getTimeline(id));
    }

    /**
     * P2-22: 流程图查询（高亮当前节点）
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含 definition / nodes / skips，nodes 中每个节点带 active 标记
     */
    @GetMapping("/instance/{id}/diagram")
    public Result<Map<String, Object>> diagram(@PathVariable String id) {
        return Result.ok(workflowFacade.getDiagram(id));
    }

    /**
     * P2-4: 流程回放步骤序列
     *
     * <p>按时间顺序合并历史任务 + 审计日志 + 当前待办为统一步骤序列，驱动前端
     * {@code FlowDiagramReplay} 组件依次高亮节点。
     *
     * @param id 流程实例 ID
     * @return 步骤列表（按 timestamp 升序）
     */
    @GetMapping("/instance/{id}/replay")
    public Result<List<Map<String, Object>>> replay(@PathVariable String id) {
        return Result.ok(workflowFacade.getReplaySteps(id));
    }

    /**
     * P2-23: 实例多维分页查询
     *
     * @param pageNo       页码
     * @param pageSize     每页大小
     * @param businessType 业务类型（可选）
     * @param initiatorId  发起人 ID（可选）
     * @param flowStatus   流程状态（可选）
     * @param startTime    开始时间下界（可选）
     * @param endTime      开始时间上界（可选）
     * @param tenantId     租户 ID（可选）
     * @return 统一响应结果，包含分页实例列表
     */
    @GetMapping("/instance/page")
    public Result<PageResult<FlowInstanceDO>> instancePage(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) Long initiatorId,
            @RequestParam(required = false) String flowStatus,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(instanceService.page(businessType, initiatorId, flowStatus,
                startTime, endTime, tid, pageNo, pageSize));
    }

    /**
     * P2-24: 读取流程变量
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含变量 Map
     */
    @GetMapping("/instance/{id}/variables")
    public Result<Map<String, Object>> getVariables(@PathVariable Long id) {
        return Result.ok(instanceService.getVariables(id));
    }

    /**
     * P2-24: 批量写入流程变量
     *
     * @param id        流程实例 ID
     * @param variables 变量 Map
     * @return 统一响应结果
     */
    @PostMapping("/instance/{id}/variables")
    public Result<Void> setVariables(@PathVariable Long id,
                                     @RequestBody Map<String, Object> variables) {
        instanceService.setVariables(id, variables);
        return Result.ok();
    }

    // ============== 任务操作 ==============

    /**
     * P2-20: 任务详情查询
     *
     * @param taskId 任务 ID
     * @return 统一响应结果，包含任务详情
     */
    @GetMapping("/task/{taskId}")
    public Result<Map<String, Object>> taskDetail(@PathVariable Long taskId) {
        return Result.ok(workflowFacade.getTaskDetail(taskId));
    }

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
     * @param dto 任务操作参数（可含 targetNodeCode 指定驳回目标；不填则按流程默认）
     * @return 统一响应结果
     */
    @PostMapping("/task/reject")
    public Result<Void> reject(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.rejectTask(dto);
        return Result.ok();
    }

    /**
     * P1-1: 查询任务所属实例经过的历史节点（驳回候选目标）
     *
     * <p>前端在打开"驳回"弹窗前调用本接口，渲染"驳回到"下拉列表。
     *
     * @param taskId 任务 ID
     * @return 该任务所属实例经过的历史节点列表（按首次完成时间正序）
     */
    @GetMapping("/task/{taskId}/rejectable-nodes")
    public Result<List<Map<String, Object>>> rejectableNodes(@PathVariable Long taskId) {
        FlowTaskDO task = taskService.getById(taskId);
        if (task == null) {
            return Result.ok(List.of());
        }
        List<Map<String, Object>> nodes = hisTaskMapper.listPassedNodes(task.getInstanceId());
        return Result.ok(nodes);
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
     * P2-25: 自由跳转 — 管理员强制跳转到任意节点
     *
     * @param dto 任务操作参数（需含 taskId + targetNodeCode）
     * @return 统一响应结果
     */
    @PostMapping("/task/jump")
    public Result<Void> jump(@RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.jumpTask(dto);
        return Result.ok();
    }

    /**
     * P2-26: 批量审批 — 对多个任务逐一通过
     *
     * @param taskIds 任务 ID 列表
     * @param userId  操作人 ID
     * @param comment 审批意见（可选）
     * @return 统一响应结果
     */
    @PostMapping("/task/batchPass")
    public Result<Void> batchPass(@RequestParam List<Long> taskIds,
                                  @RequestParam Long userId,
                                  @RequestParam(required = false) String comment) {
        workflowFacade.batchPassTasks(taskIds, userId, comment);
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

    // ============== P2-31/32/33: 审计运营统计 ==============

    /**
     * P2-31: 按节点统计平均耗时
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID（可选）
     * @return 统一响应结果，包含每个节点的平均耗时统计
     */
    @GetMapping("/stats/nodeDuration")
    public Result<List<Map<String, Object>>> nodeDurationStats(
            @RequestParam String flowCode,
            @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(taskService.nodeDurationStats(flowCode, tid));
    }

    /**
     * P2-32: 查询超期任务
     *
     * @param assigneeId 办理人 ID（可选，为空时查全部）
     * @param tenantId   租户 ID（可选）
     * @return 统一响应结果，包含超期任务列表
     */
    @GetMapping("/task/overdue")
    public Result<List<FlowTaskDO>> overdue(@RequestParam(required = false) String assigneeId,
                                         @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(taskService.listOverdue(assigneeId, tid));
    }

    /**
     * P2-36: 标记任务超时（管理员手动标记）
     *
     * @param taskId 任务 ID
     * @param reason 超时原因（可选）
     * @return 统一响应结果
     */
    @PostMapping("/task/{taskId}/timeout")
    public Result<Void> timeoutTask(@PathVariable Long taskId,
                                    @RequestParam(required = false) String reason) {
        taskService.timeoutTask(taskId, reason);
        return Result.ok();
    }

    /**
     * P2-33: 已办多维筛选分页查询
     *
     * @param assigneeId   办理人 ID（可选）
     * @param businessType 业务类型（可选）
     * @param flowCode     流程编码（可选）
     * @param startTime    完成时间下界（可选）
     * @param endTime      完成时间上界（可选）
     * @param tenantId     租户 ID（可选）
     * @param pageNo       页码
     * @param pageSize     每页大小
     * @return 统一响应结果，包含分页已办列表
     */
    @GetMapping("/task/done/search")
    public Result<PageResult<FlowTaskDO>> doneSearch(
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String flowCode,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(taskService.listDoneByAssigneePageMulti(assigneeId, businessType,
                flowCode, startTime, endTime, tid, pageNo, pageSize));
    }

    // ============== P0-3: 抄送中心 ==============

    /**
     * P0-3: 抄送中心 - 分页查询
     *
     * @param query 查询条件
     * @return 抄送分页结果
     */
    @PostMapping("/cc/page")
    public Result<PageResult<FlowCcDO>> pageCc(@RequestBody FlowCcQueryDTO query) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        Long userId = SecurityContext.getUserId();
        int pageNo = query.getPageNum() == null ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null ? 20 : query.getPageSize();
        return Result.ok(ccService.listCcByUser(userId, query.getReadStatus(),
                query.getFlowCode(), tenantId, pageNo, pageSize));
    }

    /**
     * P0-3: 抄送未读数（前端导航栏徽标）
     */
    @GetMapping("/cc/unread-count")
    public Result<Long> ccUnreadCount() {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        Long userId = SecurityContext.getUserId();
        return Result.ok(ccService.countUnread(userId, tenantId));
    }

    /**
     * P0-3: 抄送标记已读
     */
    @PostMapping("/cc/{id}/read")
    public Result<Boolean> ccMarkRead(@PathVariable Long id) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        Long userId = SecurityContext.getUserId();
        ccService.markRead(tenantId, userId, id);
        return Result.ok(Boolean.TRUE);
    }

    /**
     * P0-3: 抄送全部标记已读
     */
    @PostMapping("/cc/read-all")
    public Result<Integer> ccMarkAllRead() {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        Long userId = SecurityContext.getUserId();
        return Result.ok(ccService.markAllRead(tenantId, userId));
    }

    // ============== P1-4: 长期授权委派 ==============

    /**
     * P1-4: 创建长期授权委派
     *
     * <p>业务示例：用户 A 休假 7 天，希望 B 代理处理所有流程。
     * 提交时 body 形如：
     * <pre>
     * {
     *   "ownerUserId": 1001,
     *   "ownerUserName": "张三",
     *   "delegateUserId": 1002,
     *   "delegateUserName": "李四",
     *   "scopeType": "ALL",
     *   "startTime": "2026-07-02T00:00:00",
     *   "endTime": "2026-07-09T23:59:59",
     *   "reason": "年假"
     * }
     * </pre>
     */
    @PostMapping("/delegate-auth/create")
    public Result<Long> createDelegateAuth(@RequestBody FlowDelegateAuthDO auth) {
        // 从 SecurityContext 兜底 ownerUserId（防止前端漏传）
        if (auth.getOwnerUserId() == null) {
            auth.setOwnerUserId(SecurityContext.getUserId());
        }
        Long id = delegateAuthService.create(auth);
        return Result.ok(id);
    }

    /**
     * P1-4: 撤回授权
     */
    @PostMapping("/delegate-auth/{id}/revoke")
    public Result<Void> revokeDelegateAuth(@PathVariable Long id) {
        Long ownerId = SecurityContext.getUserId();
        delegateAuthService.revoke(id, ownerId);
        return Result.ok();
    }

    /**
     * P1-4: 启用/停用授权
     */
    @PostMapping("/delegate-auth/{id}/status")
    public Result<Void> updateDelegateAuthStatus(@PathVariable Long id,
                                                 @RequestParam String status) {
        Long operatorId = SecurityContext.getUserId();
        delegateAuthService.updateStatus(id, status, operatorId);
        return Result.ok();
    }

    /**
     * P1-4: 查"我设置的"授权列表
     */
    @GetMapping("/delegate-auth/mine")
    public Result<List<FlowDelegateAuthDO>> listMyDelegateAuths(
            @RequestParam(required = false) String status) {
        Long ownerId = SecurityContext.getUserId();
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(delegateAuthService.listMine(ownerId, tenantId, status));
    }

    /**
     * P1-4: 查"代理给我的"授权列表
     */
    @GetMapping("/delegate-auth/as-delegate")
    public Result<List<FlowDelegateAuthDO>> listAsDelegate(
            @RequestParam(required = false) String status) {
        Long delegateUserId = SecurityContext.getUserId();
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(delegateAuthService.listAsDelegate(delegateUserId, tenantId, status));
    }

    /**
     * P1-4: 查"我代理处理了哪些任务"
     */
    @GetMapping("/delegate-auth/log/delegate")
    public Result<PageResult<?>> myDelegateLog(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long delegateUserId = SecurityContext.getUserId();
        return Result.ok(delegateAuthService.listDelegateLog(delegateUserId, page, size));
    }

    /**
     * P1-4: 查"我的哪些任务被代理了"
     */
    @GetMapping("/delegate-auth/log/owner")
    public Result<PageResult<?>> myOwnerLog(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long ownerUserId = SecurityContext.getUserId();
        return Result.ok(delegateAuthService.listOwnerLog(ownerUserId, page, size));
    }

    // ============== GAP-P1: 减签 / GAP-P2: 已阅 / 沟通 ==============

    /**
     * GAP-P1: 减签 — 从会签任务中移除指定审批人
     *
     * @param dto 任务操作参数（需含 taskId + targetUserId）
     * @return 统一响应结果
     */
    @PostMapping("/task/countersignRemove")
    public Result<Void> countersignRemove(@RequestBody FlowTaskOperateDTO dto) {
        taskService.countersignRemove(dto);
        return Result.ok();
    }

    /**
     * GAP-P2: 已阅 — 标记任务已阅
     *
     * @param taskId 任务 ID
     * @return 统一响应结果
     */
    @PostMapping("/task/{taskId}/read")
    public Result<Void> markRead(@PathVariable Long taskId) {
        Long userId = SecurityContext.getUserId();
        taskService.markRead(taskId, userId);
        return Result.ok();
    }

    /**
     * GAP-P2: 沟通 — 在任务下添加沟通评论
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     * @return 统一响应结果
     */
    @PostMapping("/task/communicate")
    public Result<Void> communicate(@RequestBody FlowTaskOperateDTO dto) {
        taskService.communicate(dto);
        return Result.ok();
    }

    // ============== P1-6: SLA 超时自动策略 ==============

    /**
     * P1-6: 手动触发 SLA 扫描（管理后台调试用，scheduler 默认每 60s 自动扫描）
     *
     * @return 本轮扫描处理的任务数
     */
    @PostMapping("/sla/scan")
    public Result<Integer> slaScan() {
        int processed = slaService.scanAndProcess();
        return Result.ok(processed);
    }

    /**
     * P1-6: 手动触发单条任务的 SLA 处理
     *
     * @param taskId 任务 ID
     * @return 是否处理成功
     */
    @PostMapping("/sla/process/{taskId}")
    public Result<Boolean> slaProcess(@PathVariable Long taskId) {
        FlowTaskDO task = taskService.getById(taskId);
        if (task == null) {
            return Result.failed(com.njydsz.pmis.common.api.BizErrorCode.NOT_FOUND, "任务不存在: " + taskId);
        }
        boolean ok = slaService.processOverdue(task);
        return Result.ok(ok);
    }

    // ============== P1-7: WebSocket 待办数实时推送 ==============

    /**
     * P1-7: 查询当前用户的待办数（HTTP 拉模式，作为 WebSocket 推送的兜底）
     *
     * @return 包含 todoCount、userId、timestamp 的响应
     */
    @GetMapping("/todo/count")
    public Result<Map<String, Object>> myTodoCount() {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            return Result.ok(Map.of("userId", 0, "todoCount", 0));
        }
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        long count = taskService.countOverdue(null, tenantId) // 占位调用，避免使用未读字段
                + 0;
        // 直接用 taskService.listTodoByUser 计算
        var tasks = taskService.listTodoByUser(userId, null, null, tenantId);
        count = tasks == null ? 0 : tasks.size();
        return Result.ok(Map.of(
                "userId", userId,
                "todoCount", count,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * P1-7: 手动触发推送当前用户待办数到 WebSocket（前端重连后调一次同步）
     *
     * @return 是否成功
     */
    @PostMapping("/todo/push-mine")
    public Result<Boolean> pushMyTodoCount() {
        Long userId = SecurityContext.getUserId();
        if (userId == null) {
            return Result.ok(false);
        }
        todoCountPushService.pushTodoCount(userId);
        return Result.ok(true);
    }

    // ============== P2-1: 智能审批辅助 ==============

    /**
     * P2-1: 推荐审批人
     *
     * <p>请求体：{
     *   flowCode, nodeCode, businessType, businessId, businessTitle,
     *   requiredLevel, requiredRole, requiredDepartment,
     *   candidates: [ {userId, name, department, level, role, activeTasks, avgApprovalMs} ],
     *   topN
     * }
     *
     * @return Top N 推荐审批人列表
     */
    @PostMapping("/ai/recommend-approvers")
    public Result<List<Map<String, Object>>> recommendApprovers(
            @RequestBody Map<String, Object> body) {
        if (body == null) {
            return Result.failed(com.njydsz.pmis.common.api.BizErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = body.get("candidates") instanceof List<?>
                ? (List<Map<String, Object>>) body.get("candidates") : List.of();
        int topN = body.get("topN") instanceof Number n ? n.intValue() : 3;
        List<Map<String, Object>> top = aiAssistService.recommendApprovers(body, candidates, topN);
        return Result.ok(top);
    }

    /**
     * P2-1: 起草审批意见
     *
     * <p>请求体：{
     *   action (PASS/REJECT/TRANSFER/DELEGATE/URGE),
     *   flowCode, flowName, nodeCode, nodeName,
     *   title, riskLevel, overdueDays, tone, maxLength,
     *   historicalComments: [String]
     * }
     */
    @PostMapping("/ai/draft-comment")
    public Result<Map<String, Object>> draftComment(@RequestBody Map<String, Object> body) {
        if (body == null) {
            return Result.failed(com.njydsz.pmis.common.api.BizErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        Map<String, Object> result = aiAssistService.draftComment(body);
        return Result.ok(result);
    }

    /**
     * P2-1: 检查 AI Agent 服务是否可用
     */
    @GetMapping("/ai/status")
    public Result<Map<String, Object>> aiStatus() {
        return Result.ok(Map.of(
                "available", aiAssistService.isAiAvailable(),
                "agents", List.of("APPROVER_RECOMMEND", "COMMENT_DRAFT")
        ));
    }

    // ============== GAP-P2: 审批效率分析 ==============

    /**
     * GAP-P2: 审批效率统计 — 单量/平均耗时/代批率/超期率
     *
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @return 统计结果
     */
    @GetMapping("/efficiency/stats")
    public Result<Map<String, Object>> efficiencyStats(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(efficiencyService.efficiencyStats(tenantId, startTime, endTime));
    }

    /**
     * GAP-P2: 节点瓶颈排名
     *
     * @param flowCode 流程编码（可选）
     * @param limit    返回条数上限
     * @return 瓶颈节点列表
     */
    @GetMapping("/efficiency/bottleneck")
    public Result<List<Map<String, Object>>> bottleneckRanking(
            @RequestParam(required = false) String flowCode,
            @RequestParam(defaultValue = "10") int limit) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(efficiencyService.bottleneckRanking(tenantId, flowCode, limit));
    }

    /**
     * GAP-P2: 审批人效率排名
     *
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @param limit     返回条数上限
     * @return 审批人排名列表
     */
    @GetMapping("/efficiency/approver-ranking")
    public Result<List<Map<String, Object>>> approverRanking(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "10") int limit) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(efficiencyService.approverRanking(tenantId, startTime, endTime, limit));
    }

    /**
     * GAP-P2: 审批趋势
     *
     * @param interval  聚合粒度：DAY / WEEK / MONTH
     * @param startTime 开始时间（可选）
     * @param endTime   结束时间（可选）
     * @return 趋势列表
     */
    @GetMapping("/efficiency/trend")
    public Result<List<Map<String, Object>>> approvalTrend(
            @RequestParam(defaultValue = "DAY") String interval,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(efficiencyService.approvalTrend(tenantId, interval, startTime, endTime));
    }

    // ============== GAP-P2: 流程模板库 ==============

    /**
     * GAP-P2: 列出所有可用模板
     *
     * @param category 模板分类（可选）
     * @return 模板列表
     */
    @GetMapping("/template/list")
    public Result<List<Map<String, Object>>> listTemplates(
            @RequestParam(required = false) String category) {
        return Result.ok(templateService.listTemplates(category));
    }

    /**
     * GAP-P2: 一键导入模板
     *
     * @param templateCode 模板编码
     * @return 新创建的流程定义 ID
     */
    @PostMapping("/template/{templateCode}/import")
    public Result<Long> importTemplate(@PathVariable String templateCode) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(templateService.importTemplate(templateCode, tenantId));
    }

    /**
     * GAP-P2: 预览模板内容
     *
     * @param templateCode 模板编码
     * @return 模板详情
     */
    @GetMapping("/template/{templateCode}/preview")
    public Result<Map<String, Object>> previewTemplate(@PathVariable String templateCode) {
        return Result.ok(templateService.previewTemplate(templateCode));
    }

    // ============== P3-1: 灰度发布 ==============

    /** P3-1: 灰度发布服务 */
    private final com.njydsz.pmis.workflow.service.FlowCanaryService canaryService;

    /**
     * P3-1: 启动灰度发布
     *
     * <p>将指定定义标记为灰度版，按 initialPercent 切流。
     *
     * @param definitionId   灰度版定义 ID
     * @param initialPercent 初始灰度比例（0-100）
     * @param strategy       切流策略：USER_HASH / RANDOM / WHITELIST
     * @param operatorId     操作人 ID
     * @param operatorName   操作人姓名
     * @param note           备注
     * @return 统一响应结果
     */
    @PostMapping("/canary/{definitionId}/publish")
    public Result<Void> publishCanary(
            @PathVariable Long definitionId,
            @RequestParam(defaultValue = "10") int initialPercent,
            @RequestParam(defaultValue = "USER_HASH") String strategy,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String note) {
        canaryService.publishCanary(definitionId, initialPercent, strategy,
                operatorId, operatorName, note);
        return Result.ok();
    }

    /**
     * P3-1: 调整灰度比例（逐步放量/缩量）
     *
     * @param definitionId 定义 ID
     * @param newPercent   新比例（0-100）
     * @param operatorId   操作人 ID
     * @param operatorName 操作人姓名
     * @param note         备注
     * @return 统一响应结果
     */
    @PostMapping("/canary/{definitionId}/adjust")
    public Result<Void> adjustCanary(
            @PathVariable Long definitionId,
            @RequestParam int newPercent,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String note) {
        canaryService.adjustCanaryPercent(definitionId, newPercent, operatorId, operatorName, note);
        return Result.ok();
    }

    /**
     * P3-1: 全量发布 - 灰度版晋升为稳定版
     *
     * @param definitionId 灰度版定义 ID
     * @param operatorId   操作人 ID
     * @param operatorName 操作人姓名
     * @param note         备注
     * @return 统一响应结果
     */
    @PostMapping("/canary/{definitionId}/promote")
    public Result<Void> promoteCanary(
            @PathVariable Long definitionId,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String note) {
        canaryService.promoteCanary(definitionId, operatorId, operatorName, note);
        return Result.ok();
    }

    /**
     * P3-1: 灰度回滚
     *
     * @param definitionId 灰度版定义 ID
     * @param operatorId   操作人 ID
     * @param operatorName 操作人姓名
     * @param note         备注（含回滚原因）
     * @return 统一响应结果
     */
    @PostMapping("/canary/{definitionId}/rollback")
    public Result<Void> rollbackCanary(
            @PathVariable Long definitionId,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String note) {
        canaryService.rollbackCanary(definitionId, operatorId, operatorName, note);
        return Result.ok();
    }

    /**
     * P3-1: 查询某 flowCode 的灰度发布历史
     *
     * @param flowCode 流程编码
     * @param tenantId 租户 ID（可选）
     * @return rollout 日志列表
     */
    @GetMapping("/canary/{flowCode}/rollout-log")
    public Result<List<Map<String, Object>>> rolloutLog(
            @PathVariable String flowCode,
            @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(canaryService.listCanaryRolloutLog(flowCode, tid));
    }

    // ============== GAP-V2-01: 可视化流程设计器 API ==============

    /**
     * GAP-V2-01: 获取设计器数据 — 返回完整流程图（节点+边+坐标）
     *
     * @param id 流程定义 ID
     * @return 设计器数据（definition / nodes / edges）
     */
    @GetMapping("/definition/{id}/designer")
    public Result<Map<String, Object>> getDesignerData(@PathVariable Long id) {
        return Result.ok(definitionService.getDesignerData(id));
    }

    /**
     * GAP-V2-01: 批量保存设计器数据 — 一次性保存节点坐标 + 属性
     *
     * @param id           流程定义 ID
     * @param designerData 设计器数据（nodes + edges）
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/designer")
    public Result<Void> saveDesignerData(@PathVariable Long id,
                                          @RequestBody Map<String, Object> designerData) {
        definitionService.saveDesignerData(id, designerData);
        return Result.ok();
    }

    // ============== GAP-V2-02: 表单引擎字段配置 ==============

    /**
     * GAP-V2-02: 获取节点表单字段配置
     *
     * @param id       流程定义 ID
     * @param nodeCode 节点编码
     * @return 字段权限 JSON 字符串
     */
    @GetMapping("/definition/{id}/form-config/{nodeCode}")
    public Result<String> getFormConfig(@PathVariable Long id,
                                         @PathVariable String nodeCode) {
        return Result.ok(definitionService.getFormConfig(id, nodeCode));
    }

    /**
     * GAP-V2-02: 保存节点表单字段配置
     *
     * @param id              流程定义 ID
     * @param nodeCode        节点编码
     * @param formFieldsConfig 字段权限 JSON 字符串
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/form-config/{nodeCode}")
    public Result<Void> saveFormConfig(@PathVariable Long id,
                                        @PathVariable String nodeCode,
                                        @RequestBody String formFieldsConfig) {
        definitionService.saveFormConfig(id, nodeCode, formFieldsConfig);
        return Result.ok();
    }

    /**
     * GAP-V2-02: 获取表单渲染数据 — 审批人打开待办时获取字段权限
     *
     * @param instanceId 流程实例 ID
     * @param taskId     任务 ID（可选，为空取当前节点）
     * @return 渲染数据（nodeCode / formFieldsConfig / variables）
     */
    @GetMapping("/instance/{instanceId}/form-render")
    public Result<Map<String, Object>> getFormRenderData(
            @PathVariable Long instanceId,
            @RequestParam(required = false) Long taskId) {
        return Result.ok(instanceService.getFormRenderData(instanceId, taskId));
    }
}
