package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.WorkflowFacade;
import com.njydsz.pmis.workflow.dto.FlowCcQueryDTO;
import com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.dto.FlowInstanceViewDTO;
import com.njydsz.pmis.workflow.dto.FlowStartProcessDTO;
import com.njydsz.pmis.workflow.dto.FlowTaskOperateDTO;
import com.njydsz.pmis.workflow.dto.InstanceMigrationDTO;
import com.njydsz.pmis.workflow.dto.InstanceMigrationResultDTO;
import com.njydsz.pmis.workflow.entity.FlowCcDO;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowDelegateAuthDO;
import com.njydsz.pmis.workflow.entity.FlowInstanceDO;
import com.njydsz.pmis.workflow.entity.FlowNotifyChannelDO;
import com.njydsz.pmis.workflow.entity.FlowTaskDO;
import com.njydsz.pmis.workflow.mapper.FlowHisTaskMapper;
import com.njydsz.pmis.workflow.mapper.FlowInstanceMapper;
import com.njydsz.pmis.workflow.service.FlowAiAssistService;
import com.njydsz.pmis.workflow.service.FlowCanaryService;
import com.njydsz.pmis.workflow.service.FlowCcService;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowDelegateAuthService;
import com.njydsz.pmis.workflow.service.FlowEfficiencyService;
import com.njydsz.pmis.workflow.service.FlowInstanceMigrationService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import com.njydsz.pmis.workflow.service.FlowNotifyChannelService;
import com.njydsz.pmis.workflow.service.FlowSlaService;
import com.njydsz.pmis.workflow.service.FlowTaskService;
import com.njydsz.pmis.workflow.service.FlowTemplateService;
import com.njydsz.pmis.workflow.service.FlowTodoCountPushService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
@Tag(name = "workflow-engine", description = "工作流引擎管理接口")
@RequestMapping("/api/v1/workflow/engine")
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
    /** P2-4: 流程实例 mapper（监控仪表盘聚合查询） */
    private final FlowInstanceMapper instanceMapper;
    /** P1-4: 长期授权委派服务 */
    private final FlowDelegateAuthService delegateAuthService;
    /** GAP-P2: 审批效率分析服务 */
    private final FlowEfficiencyService efficiencyService;
    /** GAP-P2: 流程模板服务 */
    private final FlowTemplateService templateService;
    /** P1-6: SLA 超时自动策略服务 */
    private final FlowSlaService slaService;
    /** P1-7: WebSocket 待办数实时推送服务 */
    private final FlowTodoCountPushService todoCountPushService;
    /** P2-1: 智能审批辅助服务（推荐审批人 / 起草意见） */
    private final FlowAiAssistService aiAssistService;
    /** GAP-V2-09: 流程实例迁移服务（新版本部署后迁移运行中实例） */
    private final FlowInstanceMigrationService instanceMigrationService;
    /** GAP-V2: 通知通道配置服务 */
    private final FlowNotifyChannelService notifyChannelService;
    /** P0-1: BPMN 事件订阅服务（消息关联 / 错误抛出） */
    private final com.njydsz.pmis.workflow.service.FlowEventSubscriptionService eventSubscriptionService;

    // ============== 引擎信息 ==============

    /**
     * 查询引擎信息
     *
     * @return 统一响应结果，包含引擎类型与可用性
     */
    @GetMapping("/info")
    @Operation(summary = "查询工作流引擎信息")
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
    @Operation(summary = "部署流程定义")
    public Result<Long> deploy(@Valid @RequestBody FlowDeployProcessDTO dto) {
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
    @Operation(summary = "发布流程定义")
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
    @Operation(summary = "废弃流程定义")
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
    @Operation(summary = "按编码查询已发布流程定义")
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
    @Operation(summary = "分页查询流程定义")
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
    @Operation(summary = "查询流程定义详情（含节点与跳转）")
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
    @Operation(summary = "切换流程定义的激活版本")
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
    @Operation(summary = "启用流程定义")
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
    @Operation(summary = "停用流程定义")
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
    @Operation(summary = "更新流程节点坐标")
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
    @Operation(summary = "编辑未发布的流程定义草稿")
    public Result<Void> updateDefinition(@PathVariable Long id,
                                         @Valid @RequestBody FlowDeployProcessDTO dto) {
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
    @Operation(summary = "导出流程定义为 JSON")
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
    @Operation(summary = "从 JSON 导入流程定义")
    public Result<Long> importDefinition(@RequestBody String json,
                                         @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(definitionService.importDefinition(json, tid));
    }

    /**
     * 列出流程定义的所有历史版本
     *
     * @param id 流程定义 ID
     * @return 统一响应结果，包含版本列表
     */
    @GetMapping("/definition/{id}/versions")
    @Operation(summary = "列出流程定义的所有历史版本")
    public Result<List<Map<String, Object>>> listVersions(@PathVariable Long id) {
        return Result.ok(definitionService.listVersions(id));
    }

    /**
     * 版本差异对比
     *
     * @param id 流程定义 ID
     * @param v1 版本号 1
     * @param v2 版本号 2
     * @return 统一响应结果，包含 nodeChanges 和 skipChanges
     */
    @GetMapping("/definition/{id}/diff")
    @Operation(summary = "流程定义版本差异对比")
    public Result<Map<String, Object>> diffVersions(@PathVariable Long id,
                                                     @RequestParam Integer v1,
                                                     @RequestParam Integer v2) {
        return Result.ok(definitionService.diffVersions(id, v1, v2));
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
    @Operation(summary = "流程模拟运行")
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
    @Operation(summary = "启动流程实例")
    public Result<String> startProcess(@Valid @RequestBody FlowStartProcessDTO dto) {
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
    @Operation(summary = "按业务类型与业务 ID 查询流程实例")
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
    @Operation(summary = "终止流程实例")
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
    public Result<Void> pass(@Valid @RequestBody FlowTaskOperateDTO dto) {
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
    public Result<Void> reject(@Valid @RequestBody FlowTaskOperateDTO dto) {
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
    public Result<Void> transfer(@Valid @RequestBody FlowTaskOperateDTO dto) {
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
    public Result<Void> delegate(@Valid @RequestBody FlowTaskOperateDTO dto) {
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
    public Result<Void> countersignBefore(@Valid @RequestBody FlowTaskOperateDTO dto) {
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
    public Result<Void> countersignAfter(@Valid @RequestBody FlowTaskOperateDTO dto) {
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
    public Result<Void> jump(@Valid @RequestBody FlowTaskOperateDTO dto) {
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
    public Result<Void> countersignRemove(@Valid @RequestBody FlowTaskOperateDTO dto) {
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
    public Result<Void> communicate(@Valid @RequestBody FlowTaskOperateDTO dto) {
        taskService.communicate(dto);
        return Result.ok();
    }

    /**
     * GAP-P0: 暂存待审 — 审批人保存审批意见草稿
     *
     * @param dto 任务操作参数（需含 taskId + userId + comment）
     * @return 统一响应结果
     */
    @PostMapping("/task/saveDraft")
    public Result<Void> saveDraft(@Valid @RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.saveDraft(dto);
        return Result.ok();
    }

    /**
     * GAP-P0: 追加处理人 — 在已有会签任务中追加审批人
     *
     * @param dto 任务操作参数（需含 taskId + targetUserId + targetUserName）
     * @return 统一响应结果
     */
    @PostMapping("/task/addApprover")
    public Result<Void> addApprover(@Valid @RequestBody FlowTaskOperateDTO dto) {
        workflowFacade.addApprover(dto);
        return Result.ok();
    }

    // ============== P1-6: SLA 超时自动策略 ==============

    /**
     * P1-6: 手动触发 SLA 扫描（管理后台调试用，cronjob 默认每 60s 自动扫描）
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
            return Result.failed(BizErrorCode.NOT_FOUND, "任务不存在: " + taskId);
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
        // P0-1 修复：移除 countOverdue 死代码（结果被覆盖），直接用 listTodoByUser 计算待办数
        var tasks = taskService.listTodoByUser(userId, null, null, tenantId);
        long count = tasks == null ? 0 : tasks.size();
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
            return Result.failed(BizErrorCode.BAD_REQUEST, "请求体不能为空");
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
            return Result.failed(BizErrorCode.BAD_REQUEST, "请求体不能为空");
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

    // ============== P0-3 / P2-4: 监控看板聚合端点 ==============

    /**
     * P0-3 / P2-4: 监控概览 — 聚合实例/任务/效率统计
     *
     * <p>P2-4 修复：前后端契约对齐，字段名与 {@code MonitorOverviewDTO} 一致；
     * 实例状态计数从 5 次 count 查询合并为 1 次 GROUP BY 查询；
     * 新增今日新增/今日完成/待办任务数三项指标。
     *
     * @return 概览统计数据：runningCount/todayNewCount/pendingTaskCount/overdueTaskCount/todayCompletedCount
     */
    @GetMapping("/monitor/overview")
    public Result<Map<String, Object>> monitorOverview() {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        Map<String, Object> overview = new LinkedHashMap<>();

        // P2-4: 1 次 GROUP BY 查询替代 5 次 count（RUNNING/COMPLETED/REJECTED/TERMINATED/SUSPENDED）
        long running = 0;
        try {
            List<Map<String, Object>> statusCounts = instanceMapper.selectCountGroupByStatus(tenantId);
            if (statusCounts != null) {
                for (Map<String, Object> row : statusCounts) {
                    String status = String.valueOf(row.get("flowStatus"));
                    long cnt = ((Number) row.get("cnt")).longValue();
                    if ("RUNNING".equals(status)) running = cnt;
                }
            }
        } catch (Exception e) {
            log.warn("[Monitor] 状态分组计数查询失败: {}", e.getMessage());
        }
        overview.put("runningCount", running);

        // P2-4: 今日新增/今日完成（单次查询）
        try {
            Map<String, Object> today = instanceMapper.selectTodayCount(tenantId);
            if (today != null) {
                overview.put("todayNewCount",
                        today.get("todayNewCount") == null ? 0 : ((Number) today.get("todayNewCount")).longValue());
                overview.put("todayCompletedCount",
                        today.get("todayCompletedCount") == null ? 0 : ((Number) today.get("todayCompletedCount")).longValue());
            } else {
                overview.put("todayNewCount", 0);
                overview.put("todayCompletedCount", 0);
            }
        } catch (Exception e) {
            log.warn("[Monitor] 今日计数查询失败: {}", e.getMessage());
            overview.put("todayNewCount", 0);
            overview.put("todayCompletedCount", 0);
        }

        // P2-4: 待办任务数（PENDING + CLAIMED）
        try {
            overview.put("pendingTaskCount", taskService.countPending(tenantId));
        } catch (Exception e) {
            log.warn("[Monitor] 待办任务计数失败: {}", e.getMessage());
            overview.put("pendingTaskCount", 0);
        }

        // P2-4: 超期任务数
        try {
            overview.put("overdueTaskCount", taskService.countOverdue(null, tenantId));
        } catch (Exception e) {
            log.warn("[Monitor] 超期任务计数失败: {}", e.getMessage());
            overview.put("overdueTaskCount", 0);
        }

        return Result.ok(overview);
    }

    /**
     * P0-3 / P2-4: 异常流程列表 — 超期/卡单/长期运行/高驳回率
     *
     * <p>P2-4 修复：接入 efficiencyService.detectAnomalies() 的完整异常检测能力
     * （卡单/高驳回率节点/长期运行实例），并在前端 DTO 字段对齐。
     *
     * @param anomalyType 异常类型过滤（TIMEOUT/STUCK/REPEATED_REJECT/CIRCULAR_APPROVAL，可空）
     * @param warnLevel   预警级别过滤（RED/YELLOW/ORANGE，可空）
     * @param pageNum     页码（从 1 开始）
     * @param pageSize    每页大小
     * @return 分页异常实例列表
     */
    @GetMapping("/monitor/anomaly")
    public Result<PageResult<Map<String, Object>>> monitorAnomaly(
            @RequestParam(required = false) String anomalyType,
            @RequestParam(required = false) String warnLevel,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);

        // 拉取全量异常（detectAnomalies 默认 limit=100，足够覆盖监控场景）
        List<Map<String, Object>> all = new ArrayList<>();
        try {
            List<Map<String, Object>> detected = efficiencyService.detectAnomalies(tenantId, 100, 24, 7);
            if (detected != null) {
                for (Map<String, Object> a : detected) {
                    Map<String, Object> item = mapAnomaly(a, tenantId);
                    if (item == null) continue;
                    // 类型过滤
                    if (anomalyType != null && !anomalyType.isBlank()
                            && !anomalyType.equals(item.get("anomalyType"))) continue;
                    // 预警级别过滤
                    if (warnLevel != null && !warnLevel.isBlank()
                            && !warnLevel.equals(item.get("warnLevel"))) continue;
                    all.add(item);
                }
            }
        } catch (Exception e) {
            log.warn("[Monitor] 异常检测失败: {}", e.getMessage());
        }

        // 内存分页（数据量小，足够）
        int total = all.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> page = from < to ? all.subList(from, to) : new ArrayList<>();

        return Result.ok(PageResult.of(page, total, pageNum, pageSize));
    }

    /**
     * 将 efficiencyService 返回的异常 Map 映射为前端 AnomalyInstanceDTO 字段
     */
    private Map<String, Object> mapAnomaly(Map<String, Object> a, Long tenantId) {
        String type = String.valueOf(a.getOrDefault("type", "UNKNOWN"));
        Map<String, Object> item = new LinkedHashMap<>();
        // 类型映射：HIGH_REJECTION → REPEATED_REJECT；LONG_RUNNING → TIMEOUT；STUCK/OVERDUE 保留
        String anomalyType;
        switch (type) {
            case "STUCK" -> anomalyType = "STUCK";
            case "HIGH_REJECTION" -> anomalyType = "REPEATED_REJECT";
            case "LONG_RUNNING", "OVERDUE" -> anomalyType = "TIMEOUT";
            default -> anomalyType = "TIMEOUT";
        }
        item.put("anomalyType", anomalyType);

        // 实例 ID（卡单场景从 task.instanceId 取，其他从 instanceId 取）
        Object instanceId = a.get("instanceId");
        if (instanceId == null) instanceId = a.get("taskId");
        item.put("id", instanceId == null ? 0 : ((Number) instanceId).longValue());

        // 补实例详情字段（若有 instanceId）
        if (instanceId instanceof Number n) {
            try {
                FlowInstanceDO inst = instanceService.getById(n.longValue());
                if (inst != null) {
                    item.put("flowCode", inst.getFlowCode());
                    item.put("flowName", inst.getFlowName());
                    item.put("title", inst.getTitle());
                    item.put("initiatorName", inst.getInitiatorName());
                    item.put("status", inst.getFlowStatus());
                    item.put("currentNodeName", inst.getCurrentNodeName());
                    item.put("startTime", inst.getStartAt() == null ? null : inst.getStartAt().toString());
                }
            } catch (Exception e) {
                // 实例查询失败不阻塞，降级使用 detectAnomalies 返回的字段
            }
        }
        // 兜底字段（若上面实例查询失败）
        item.putIfAbsent("flowCode", a.get("flowCode"));
        item.putIfAbsent("flowName", a.get("flowName"));
        item.putIfAbsent("currentNodeName", a.get("nodeName") != null ? a.get("nodeName") : a.get("currentNodeName"));

        // 超期天数 / 卡单小时 → 映射为 overdueDays
        Object stuckHours = a.get("stuckHours");
        Object runningDays = a.get("runningDays");
        if (runningDays instanceof Number d) {
            item.put("overdueDays", d.longValue());
        } else if (stuckHours instanceof Number h) {
            item.put("overdueDays", h.longValue() / 24);
        }

        // 预警级别：overdueDays >= 7 → RED；>= 3 → YELLOW；> 0 → ORANGE；卡单/高驳回默认 YELLOW
        long days = item.get("overdueDays") instanceof Number d ? d.longValue() : 0;
        String warnLevel;
        if (anomalyType.equals("TIMEOUT")) {
            if (days >= 7) warnLevel = "RED";
            else if (days >= 3) warnLevel = "YELLOW";
            else warnLevel = "ORANGE";
        } else {
            warnLevel = "YELLOW";  // STUCK / REPEATED_REJECT 默认警告级
        }
        item.put("warnLevel", warnLevel);

        // 描述（用于 tooltip）
        item.put("description", a.get("description"));
        return item;
    }

    /**
     * P0-3 / P2-4: 实例趋势 — 按日期统计新增/完成数
     *
     * <p>P2-4 修复：入参支持 {@code days}（前端 DTO），返回 {@code date/newCount/completedCount} 三字段。
     * 内部按 days 生成日期序列，左连接新增/完成统计补 0。
     *
     * @param days 统计天数（默认 7，可选 30）
     * @return 趋势列表
     */
    @GetMapping("/monitor/instance-trend")
    public Result<List<Map<String, Object>>> monitorInstanceTrend(
            @RequestParam(defaultValue = "7") int days) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        int effectiveDays = (days == 30) ? 30 : 7;

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate start = today.minusDays(effectiveDays - 1L);
        LocalDateTime startDt = start.atStartOfDay();
        LocalDateTime endDt = today.atTime(23, 59, 59);

        // 两次 GROUP BY 查询
        List<Map<String, Object>> newCounts = instanceMapper.selectDailyNewCount(tenantId, startDt, endDt);
        List<Map<String, Object>> completedCounts = instanceMapper.selectDailyCompletedCount(tenantId, startDt, endDt);

        // 合并为日期 → {newCount, completedCount}
        Map<String, long[]> byDate = new LinkedHashMap<>();
        for (int i = 0; i < effectiveDays; i++) {
            byDate.put(start.plusDays(i).toString(), new long[]{0, 0});
        }
        if (newCounts != null) {
            for (Map<String, Object> row : newCounts) {
                String d = String.valueOf(row.get("date"));
                if (byDate.containsKey(d)) {
                    byDate.get(d)[0] = ((Number) row.get("newCount")).longValue();
                }
            }
        }
        if (completedCounts != null) {
            for (Map<String, Object> row : completedCounts) {
                String d = String.valueOf(row.get("date"));
                if (byDate.containsKey(d)) {
                    byDate.get(d)[1] = ((Number) row.get("completedCount")).longValue();
                }
            }
        }

        // 输出按日期排序
        List<Map<String, Object>> result = new ArrayList<>(effectiveDays);
        for (Map.Entry<String, long[]> entry : byDate.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", entry.getKey());
            row.put("newCount", entry.getValue()[0]);
            row.put("completedCount", entry.getValue()[1]);
            result.add(row);
        }
        return Result.ok(result);
    }

    /**
     * P0-3 / P2-4: 审批人效率排名 — SQL GROUP BY 聚合
     *
     * <p>P2-4 修复：直接走 {@code FlowHisTaskMapper.selectApproverEfficiency} SQL 聚合，
     * 替代原 efficiencyService.approverRanking 的 Java 层全表加载聚合。
     * 字段对齐前端 {@code ApproverEfficiencyDTO}：userId/userName/completedCount/avgDurationMs/totalDurationMs。
     *
     * @param topN     返回条数上限
     * @param startTime finish_at 下界（可空）
     * @param endTime   finish_at 上界（可空）
     * @return 审批人排名列表
     */
    @GetMapping("/monitor/approver-efficiency")
    public Result<List<Map<String, Object>>> monitorApproverEfficiency(
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        LocalDateTime startDt = parseDateTime(startTime);
        LocalDateTime endDt = parseDateTime(endTime);
        List<Map<String, Object>> rows = hisTaskMapper.selectApproverEfficiency(tenantId, startDt, endDt, topN);

        // 字段重命名：assigneeId(String) → userId(Long) / assigneeName → userName
        List<Map<String, Object>> result = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                try {
                    item.put("userId", Long.parseLong(String.valueOf(row.get("assigneeId"))));
                } catch (NumberFormatException e) {
                    item.put("userId", 0);
                }
                item.put("userName", row.get("assigneeName"));
                item.put("completedCount", row.get("completedCount"));
                item.put("avgDurationMs", row.get("avgDurationMs"));
                item.put("totalDurationMs", row.get("totalDurationMs"));
                result.add(item);
            }
        }
        return Result.ok(result);
    }

    /**
     * P0-3 / P2-4: 流程类型分布 — SQL GROUP BY 聚合
     *
     * <p>P2-4 修复：从 500 条 Java 层聚合改为 SQL GROUP BY 全量聚合；
     * 返回字段对齐前端 {@code FlowTypeDistributionDTO}：flowCode/flowName/count/percentage。
     *
     * @param startTime start_at 下界（可空）
     * @param endTime   start_at 上界（可空）
     * @return 分布列表
     */
    @GetMapping("/monitor/flow-type-distribution")
    public Result<List<Map<String, Object>>> monitorFlowTypeDistribution(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        LocalDateTime startDt = parseDateTime(startTime);
        LocalDateTime endDt = parseDateTime(endTime);
        List<Map<String, Object>> rows = instanceMapper.selectFlowTypeDistribution(tenantId, startDt, endDt);

        long total = 0;
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                total += ((Number) row.get("cnt")).longValue();
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("flowCode", row.get("flowCode"));
                item.put("flowName", row.get("flowName") == null ? row.get("flowCode") : row.get("flowName"));
                long cnt = ((Number) row.get("cnt")).longValue();
                item.put("count", cnt);
                item.put("percentage", total > 0 ? Math.round(cnt * 10000.0 / total) / 100.0 : 0.0);
                result.add(item);
            }
        }
        return Result.ok(result);
    }

    /**
     * P2-4: 解析日期时间字符串（yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd）
     */
    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return LocalDateTime.parse(str, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            try {
                return java.time.LocalDate.parse(str).atStartOfDay();
            } catch (Exception ex) {
                log.warn("[Monitor] 无法解析时间: {}", str);
                return null;
            }
        }
    }

    /**
     * P0-3: 超期任务列表（stats/overdue 别名，前端兼容）
     *
     * @param assigneeId 办理人 ID（可空）
     * @return 超期任务列表
     */
    @GetMapping("/stats/overdue")
    public Result<List<FlowTaskDO>> statsOverdue(
            @RequestParam(required = false) String assigneeId) {
        Long tenantId = SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(taskService.listOverdue(assigneeId, tenantId));
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
     * @param flowName     自定义流程名称（可选，为空则使用模板名称）
     * @return 新创建的流程定义 ID
     */
    @PostMapping("/template/{templateCode}/import")
    public Result<Long> importTemplate(@PathVariable String templateCode,
                                       @RequestParam(required = false) String flowName) {
        return Result.ok(templateService.importTemplate(templateCode, flowName));
    }

    /**
     * GAP-P2: 获取模板详情（含 BPMN XML）
     *
     * @param templateCode 模板编码
     * @return 模板详情
     */
    @GetMapping("/template/{templateCode}")
    public Result<Map<String, Object>> getTemplate(@PathVariable String templateCode) {
        return Result.ok(templateService.getTemplate(templateCode));
    }

    // ============== P3-1: 灰度发布 ==============

    /** P3-1: 灰度发布服务 */
    private final FlowCanaryService canaryService;

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
     * P1-2: 获取节点 SLA 配置（JSON 字符串）
     *
     * @param id       流程定义 ID
     * @param nodeCode 节点编码
     * @return SLA 配置 JSON（未配置返回 null）
     */
    @GetMapping("/definition/{id}/sla-config/{nodeCode}")
    public Result<String> getSlaConfig(@PathVariable Long id,
                                        @PathVariable String nodeCode) {
        return Result.ok(definitionService.getSlaConfig(id, nodeCode));
    }

    /**
     * P1-2: 保存节点 SLA 配置
     *
     * @param id         流程定义 ID
     * @param nodeCode   节点编码
     * @param slaConfig  SLA 配置（JSON 对象，由 controller 序列化为字符串存储）
     * @return 统一响应结果
     */
    @PostMapping("/definition/{id}/sla-config/{nodeCode}")
    public Result<Void> saveSlaConfig(@PathVariable Long id,
                                        @PathVariable String nodeCode,
                                        @RequestBody java.util.Map<String, Object> slaConfig) {
        String json = slaConfig == null ? null : com.alibaba.fastjson2.JSON.toJSONString(slaConfig);
        definitionService.saveSlaConfig(id, nodeCode, json);
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

    // ============== GAP-V2: 通知通道配置 ==============

    /**
     * 查询所有通知通道配置
     *
     * @param tenantId 租户 ID（可选，默认从上下文获取）
     * @return 通道配置列表
     */
    @GetMapping("/notify-channel/list")
    public Result<List<FlowNotifyChannelDO>> listNotifyChannels(
            @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(notifyChannelService.listChannels(tid));
    }

    /**
     * 新增或更新通知通道配置
     *
     * @param dto 通道配置（id 为空时新增，非空时更新）
     * @return 保存后的通道配置
     */
    @PostMapping("/notify-channel/save")
    public Result<FlowNotifyChannelDO> saveNotifyChannel(@RequestBody FlowNotifyChannelDO dto) {
        if (dto.getTenantId() == null) {
            dto.setTenantId(SecurityContext.getTenantIdOrDefault(1L));
        }
        return Result.ok(notifyChannelService.saveChannel(dto));
    }

    /**
     * 启用/停用通知通道
     *
     * @param id      通道配置 ID
     * @param enabled 是否启用
     * @return 统一响应结果
     */
    @PutMapping("/notify-channel/{id}/toggle")
    public Result<Void> toggleNotifyChannel(@PathVariable Long id,
                                             @RequestParam Boolean enabled) {
        notifyChannelService.toggleChannel(id, enabled);
        return Result.ok();
    }

    /**
     * 删除通知通道配置
     *
     * @param id 通道配置 ID
     * @return 统一响应结果
     */
    @DeleteMapping("/notify-channel/{id}")
    public Result<Void> deleteNotifyChannel(@PathVariable Long id) {
        notifyChannelService.deleteChannel(id);
        return Result.ok();
    }

    // ============== GAP-V2-09: 流程实例迁移 ==============

    /**
     * GAP-V2-09: 执行实例迁移 — 将源定义下运行中实例迁移到目标定义。
     *
     * <p>请求体 {@link InstanceMigrationDTO}：
     * <ul>
     *   <li>sourceDefinitionId / targetDefinitionId：源/目标定义 ID（必填）</li>
     *   <li>tenantId：租户 ID（可选，默认从上下文获取）</li>
     *   <li>nodeMapping：旧节点编码 -> 新节点编码 映射（可选）</li>
     *   <li>dryRun：是否试运行（可选，true 时仅模拟不落库）</li>
     * </ul>
     *
     * @param dto 迁移参数
     * @return 统一响应结果，包含迁移结果报告
     */
    @PostMapping("/instance/migrate")
    public Result<InstanceMigrationResultDTO> migrateInstances(@RequestBody InstanceMigrationDTO dto) {
        return Result.ok(instanceMigrationService.migrate(dto));
    }

    /**
     * GAP-V2-09: 预览实例迁移（试运行 / dry run）— 不实际更新数据库，仅返回迁移报告。
     *
     * @param dto 迁移参数（dryRun 字段将被忽略，强制为试运行）
     * @return 统一响应结果，包含迁移结果报告
     */
    @PostMapping("/instance/migrate/preview")
    public Result<InstanceMigrationResultDTO> previewMigration(@RequestBody InstanceMigrationDTO dto) {
        return Result.ok(instanceMigrationService.previewMigration(dto));
    }

    /**
     * GAP-V2-09: 自动映射节点编码 — 对比源/目标定义节点，按编码自动匹配。
     *
     * <p>返回的映射可作为 {@link InstanceMigrationDTO#setNodeMapping(Map)} 的预填值，
     * 编码不同的节点需人工补充映射。
     *
     * @param sourceDefinitionId 源定义 ID
     * @param targetDefinitionId 目标定义 ID
     * @return 统一响应结果，包含 旧节点编码 -> 新节点编码 的映射
     */
    @GetMapping("/instance/migrate/auto-map")
    public Result<Map<String, String>> autoMapNodes(
            @RequestParam Long sourceDefinitionId,
            @RequestParam Long targetDefinitionId) {
        return Result.ok(instanceMigrationService.autoMapNodes(sourceDefinitionId, targetDefinitionId));
    }

    // ============== P0-1: BPMN 事件触发 ==============

    /**
     * P0-1: 消息关联 — 外部系统通过消息名称触发 WAITING 的 MESSAGE 订阅
     *
     * <p>BPMN intermediateCatchEvent / boundaryEvent 配置 messageEventDefinition 后，
     * 流程推进到该节点时会创建 MESSAGE 类型订阅（WAITING）。
     * 外部系统调用本接口，按 messageName + correlationKey 匹配订阅并触发，
     * 触发后流程从事件捕获节点推进到下游。
     *
     * @param messageName    消息名称（对应 BPMN messageRef）
     * @param correlationKey 关联键（业务标识，可选）
     * @param payload        消息载荷 JSON（会合并到流程变量）
     * @param tenantId       租户 ID（可选）
     * @return 触发的订阅数量
     */
    @PostMapping("/event/correlate-message")
    public Result<Integer> correlateMessage(
            @RequestParam String messageName,
            @RequestParam(required = false) String correlationKey,
            @RequestBody(required = false) String payload,
            @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(eventSubscriptionService.correlateMessage(tid, messageName, correlationKey, payload));
    }

    /**
     * P0-1: 抛出错误 — 触发 WAITING 的 ERROR 订阅（边界错误事件）
     *
     * <p>BPMN boundaryEvent 配置 errorEventDefinition 后，附着在 userTask 上。
     * 当外部系统抛出匹配 errorCode 的错误时，取消 userTask，流程沿边界事件的出边推进。
     *
     * @param errorCode  错误代码（对应 BPMN errorRef）
     * @param instanceId 实例 ID（可选，为空则按 errorCode 全局匹配）
     * @param payload    错误载荷 JSON
     * @param tenantId   租户 ID（可选）
     * @return 触发的订阅数量
     */
    @PostMapping("/event/throw-error")
    public Result<Integer> throwError(
            @RequestParam String errorCode,
            @RequestParam(required = false) Long instanceId,
            @RequestBody(required = false) String payload,
            @RequestParam(required = false) Long tenantId) {
        Long tid = tenantId != null ? tenantId : SecurityContext.getTenantIdOrDefault(1L);
        return Result.ok(eventSubscriptionService.throwError(tid, instanceId, errorCode, payload));
    }

    /**
     * P0-1: 查询实例的事件订阅列表
     *
     * @param instanceId 实例 ID
     * @return 订阅列表（含 WAITING / COMPLETED / CANCELLED 状态）
     */
    @GetMapping("/instance/{instanceId}/event-subscriptions")
    public Result<List<com.njydsz.pmis.workflow.entity.FlowEventSubscriptionDO>> listEventSubscriptions(
            @PathVariable Long instanceId) {
        return Result.ok(eventSubscriptionService.listByInstance(instanceId));
    }
}
