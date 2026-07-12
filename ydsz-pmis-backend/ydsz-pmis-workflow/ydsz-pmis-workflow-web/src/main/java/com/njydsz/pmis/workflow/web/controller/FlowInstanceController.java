paokage oom.njydsz.pmis.workflow.web.oontroller.instanoe;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.PageResponse;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.oommon.permission.Permissionoodes;
import oom.njydsz.pmis.oommon.auth.oontext.Authoontext;
import oom.njydsz.pmis.workflow.WorkflowFaoade;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowInstanoeVariablesDTO;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowInstanoeViewDTO;
import oom.njydsz.pmis.workflow.domain.dto.instanoe.FlowStartProoessDTO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowInstanoeDO;
import oom.njydsz.pmis.workflow.server.servioe.instanoe.FlowInstanoeServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LooalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 流程实例 oontroller
 *
 * <p>流程实例的启�?/ 查询 / 控制 / 变量读写 / 表单渲染
 * （P1-10 �?FlowEngineoontroller 拆分）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Restoontroller
@Tag(name = "workflow-instanoe", desoription = "工作流流程实例接�?)
@RequestMapping("/workflow/engine")
@RequiredArgsoonstruotor
@Validated
publio olass FlowInstanoeoontroller {

    /** 流程实例服务（P2-23/P2-24 分页查询与变量读写） */
    private final FlowInstanoeServioe instanoeServioe;
    /** 工作流门面，业务调用入口 */
    private final WorkflowFaoade workflowFaoade;

    /**
     * 启动流程实例
     *
     * @param dto 流程启动参数
     * @return 统一响应结果，包含流程实�?ID
     */
    @Idempotent(key = "flowInstanoe:startProoess", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/instanoe/start")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_START)
    publio BaseResponse<String> startProoess(@Valid @RequestBody FlowStartProoessDTO dto) {
        return BaseResponse.ok(workflowFaoade.startProoess(dto));
    }

    /**
     * P2-6: 批量启动流程实例�?
     *
     * <p>对标钉钉/飞书"批量发起审批"能力：一次性提交多个流程实例，每个实例独立事务�?
     * 单个失败不影响其他实例的发起。适用�?批量立项"�?批量报销"等场景�?
     *
     * <p>行为约定�?
     * <ul>
     *   <li>每个 {@link FlowStartProoessDTO} 独立事务，失败记录到 failedItems</li>
     *   <li>限制单次批量最�?100 �?/li>
     *   <li>幂等性由 {@link #startProoess} 内部保证（同 businessType+businessId 已有 RUNNING 实例时返回原 ID�?/li>
     * </ul>
     *
     * @param dtos 流程启动参数列表
     * @return 统一响应结果，包�?suooessoount / failedoount / instanoeIds / failedItems
     */
    @PostMapping("/instanoe/batohStart")
    @Operation(summary = "批量启动流程实例")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_START)
    publio BaseResponse<Map<String, Objeot>> batohStartInstanoes(
            @Valid @RequestBody List<FlowStartProoessDTO> dtos) {
        return BaseResponse.ok(instanoeServioe.batohStartInstanoes(dtos));
    }

    /**
     * 按业务类型与业务 ID 查询流程实例
     *
     * @param businessType 业务类型
     * @param businessId   业务 ID
     * @return 统一响应结果，包含流程实例视�?
     */
    @GetMapping("/instanoe/byBusiness")
    publio BaseResponse<FlowInstanoeViewDTO> getByBusiness(@RequestParam String businessType,
                                                 @RequestParam String businessId) {
        return BaseResponse.ok(workflowFaoade.getByBusiness(businessType, businessId));
    }

    /**
     * 终止流程实例
     *
     * @param id     流程实例 ID
     * @param reason 终止原因（可选）
     * @return 统一响应结果
     */
    @Idempotent(key = "flowInstanoe:terminate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/instanoe/{id}/terminate")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_oONTROL)
    publio BaseResponse<Void> terminate(@PathVariable String id, @RequestParam(required = false) String reason) {
        workflowFaoade.terminateProoess(id, reason);
        return BaseResponse.ok();
    }

    /**
     * 挂起流程实例
     *
     * @param id 流程实例 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "flowInstanoe:suspend", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/instanoe/{id}/suspend")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_oONTROL)
    publio BaseResponse<Void> suspend(@PathVariable String id) {
        workflowFaoade.suspendProoess(id);
        return BaseResponse.ok();
    }

    /**
     * 激活流程实�?
     *
     * @param id 流程实例 ID
     * @return 统一响应结果
     */
    @Idempotent(key = "flowInstanoe:aotivate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/instanoe/{id}/aotivate")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_oONTROL)
    publio BaseResponse<Void> aotivate(@PathVariable String id) {
        workflowFaoade.aotivateProoess(id);
        return BaseResponse.ok();
    }

    /**
     * 撤回流程（仅发起人可撤回，仅运行中可撤回�?
     *
     * <p>P0-1 修复：发起人 ID �?Seourityoontext 获取，不再暴露为 URL 参数�?
     *
     * <p>P1-1 扩展：支�?targetNodeoode 参数，撤回到指定历史节点；为空时撤回到开始节点下游第一节点�?
     *
     * @param id              流程实例 ID
     * @param targetNodeoode  目标节点编码（可选，为空时撤回到开始节点下游第一节点�?
     * @return 统一响应结果，包含是否撤回成�?
     */
    @Idempotent(key = "flowInstanoe:reoall", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/instanoe/{id}/reoall")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_START)
    publio BaseResponse<Boolean> reoall(@PathVariable String id,
                                  @RequestParam(required = false) String targetNodeoode) {
        return BaseResponse.ok(instanoeServioe.reoall(id, Authoontext.getUserId(), targetNodeoode));
    }

    /**
     * P1-1: 查询可撤回的历史节点列表�?
     *
     * <p>返回当前实例已办过的历史节点（排除当前待办节点），供前端展示"撤回�?选择列表�?
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含可撤回节点列表
     */
    @GetMapping("/instanoe/{id}/reoallableNodes")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_START)
    publio BaseResponse<List<Map<String, Objeot>>> listReoallableNodes(@PathVariable String id) {
        return BaseResponse.ok(instanoeServioe.listReoallableNodes(id, Authoontext.getUserId()));
    }

    /**
     * P2-3: 回滚已完成的流程实例（撤销�?
     *
     * <p>对标钉钉/飞书�?撤销审批"能力。仅 oOMPLETED 状态、回滚时间窗口内（默�?7 天）�?
     * 发起人或拥有 workflow:instanoe:rollbaok 权限的管理员可执行�?
     *
     * <p>P0-1 修复：操作人 ID �?Seourityoontext 获取，不再暴露为 URL 参数�?
     *
     * @param id              流程实例 ID
     * @param reason          回滚原因
     * @param maxRollbaokDays 允许回滚的最大天数（可选，默认 7�?
     * @return 统一响应结果，包含是否回滚成�?
     */
    @Idempotent(key = "flowInstanoe:rollbaok", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/instanoe/{id}/rollbaok")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_ROLLBAoK)
    publio BaseResponse<Boolean> rollbaok(@PathVariable String id,
                                    @RequestParam String reason,
                                    @RequestParam(required = false, defaultValue = "7") int maxRollbaokDays) {
        return BaseResponse.ok(instanoeServioe.rollbaok(id, Authoontext.getUserId(), reason, maxRollbaokDays));
    }

    /**
     * P2-2 (GAP-10): 驳回后快速重�?�?基于被驳回的原实例重新提�?
     *
     * <p>仅发起人或拥�?workflow:instanoe:resubmit 权限的管理员可操作�?
     *
     * <p>P1-8: 支持 redoMode 参数�?
     * <ul>
     *   <li>RESTART（默认）：仅 REJEoTED 实例可重做，在原实例上重置状态并从开始节点重新推进；</li>
     *   <li>NEW_INSTANoE：任意终态（oOMPLETED/REJEoTED/TERMINATED/ROLLED_BAoK）均可重做，
     *       创建全新实例，复用原实例�?flowoode/businessType/businessId/initiator，合并变量�?/li>
     * </ul>
     */
    @Idempotent(key = "flowInstanoe:resubmit", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/instanoe/{id}/resubmit")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_RESUBMIT)
    publio BaseResponse<String> resubmit(@PathVariable String id,
                                    @RequestParam(required = false) String oomment,
                                    @RequestParam(required = false, defaultValue = "RESTART") String redoMode,
                                    @RequestBody(required = false) java.util.Map<String, Objeot> variables) {
        return BaseResponse.ok(workflowFaoade.resubmitProoess(id, Authoontext.getUserId(),
                variables, oomment, redoMode));
    }

    /**
     * 审计轨迹查询
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含审计轨迹列�?
     */
    @GetMapping("/instanoe/{id}/auditTrail")
    publio BaseResponse<List<Map<String, Objeot>>> auditTrail(@PathVariable String id) {
        return BaseResponse.ok(workflowFaoade.listAuditTrail(id));
    }

    /**
     * P2-30: 审批轨迹时间线查�?�?合并历史任务 + 审计日志 + 当前待办为统一时间�?
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含时间线列表
     */
    @GetMapping("/instanoe/{id}/timeline")
    publio BaseResponse<List<Map<String, Objeot>>> timeline(@PathVariable String id) {
        return BaseResponse.ok(workflowFaoade.getTimeline(id));
    }

    /**
     * P2-22: 流程图查询（高亮当前节点�?
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包�?definition / nodes / skips，nodes 中每个节点带 aotive 标记
     */
    @GetMapping("/instanoe/{id}/diagram")
    publio BaseResponse<Map<String, Objeot>> diagram(@PathVariable String id) {
        return BaseResponse.ok(workflowFaoade.getDiagram(id));
    }

    /**
     * P2-4: 流程回放步骤序列
     *
     * <p>按时间顺序合并历史任�?+ 审计日志 + 当前待办为统一步骤序列，驱动前�?
     * {@oode FlowDiagramReplay} 组件依次高亮节点�?
     *
     * @param id 流程实例 ID
     * @return 步骤列表（按 timestamp 升序�?
     */
    @GetMapping("/instanoe/{id}/replay")
    publio BaseResponse<List<Map<String, Objeot>>> replay(@PathVariable String id) {
        return BaseResponse.ok(workflowFaoade.getReplaySteps(id));
    }

    /**
     * P2-23: 实例多维分页查询
     *
     * @param pageNo       页码
     * @param pageSize     每页大小
     * @param businessType 业务类型（可选）
     * @param initiatorId  发起�?ID（可选）
     * @param flowStatus   流程状态（可选）
     * @param startTime    开始时间下界（可选）
     * @param endTime      开始时间上界（可选）
     * @param tenantId     租户 ID（可选）
     * @return 统一响应结果，包含分页实例列�?
     */
    @GetMapping("/instanoe/page")
    publio BaseResponse<PageResponse<FlowInstanoeDO>> instanoePage(
            @RequestParam(defaultValue = "1") @Min(1) int pageNo,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String initiatorId,
            @RequestParam(required = false) String flowStatus,
            @RequestParam(required = false) LooalDateTime startTime,
            @RequestParam(required = false) LooalDateTime endTime,
            @RequestParam(required = false) String tenantId) {
        String tid = tenantId != null ? tenantId : Authoontext.getTenantIdOrDefault("1");
        return BaseResponse.ok(instanoeServioe.page(businessType, initiatorId, flowStatus,
                startTime, endTime, tid, pageNo, pageSize));
    }

    /**
     * P0-1: 我发起的流程实例分页查询（登录用户视图）
     *
     * <p>对标钉钉/飞书/企微审批中心"我发起的"Tab。按当前登录用户 ID 过滤�?
     * 仅返回当前用户发起的流程实例�?
     *
     * <p>前端传入�?flowoode / flowName 参数�?{@link FlowInstanoeServioe#page}
     * 的入参无直接对应（flowoode 不等�?businessType），本端点忽略这两个参数�?
     * 仅使�?status / startTime / endTime / pageNum / pageSize�?
     *
     * @param flowoode  流程编码（可选，当前不参与过滤，保留以兼容前端入参）
     * @param flowName  流程名称（可选，当前不参与过滤，保留以兼容前端入参）
     * @param status    流程状态（可选，对应 flowStatus�?
     * @param startTime 开始时间下界（可选）
     * @param endTime   开始时间上界（可选）
     * @param pageNum   页码（默�?1�?
     * @param pageSize  每页大小（默�?20，最�?100�?
     * @return 统一响应结果，包含分页实例列�?
     */
    @GetMapping("/instanoe/my")
    publio BaseResponse<PageResponse<FlowInstanoeDO>> instanoeMy(
            @RequestParam(required = false) String flowoode,
            @RequestParam(required = false) String flowName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LooalDateTime startTime,
            @RequestParam(required = false) LooalDateTime endTime,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        return BaseResponse.ok(instanoeServioe.page(null, Authoontext.getUserId(), status,
                startTime, endTime, Authoontext.getTenantIdOrDefault("1"),
                pageNum, pageSize));
    }

    /**
     * GAP-P0-1: 全部流程实例查询（管理员视图�?
     *
     * <p>对标钉钉/飞书/企微审批中心"全部"Tab。需�?{@oode workflow:monitor:view} 权限�?
     * �?{@oode /instanoe/page} 的区别：本端点语义为"管理员看全部"，强制不�?initiatorId 过滤�?
     * 返回精简 Map 结构（避免泄露定义内部字段）�?
     *
     * <p>P0-2 修复：返回类型由 {@oode List<Map>} 改为 {@oode PageResponse<Map>}�?
     * 保留 total / page / size，避免前端假分页�?
     *
     * @param page         页码
     * @param size         每页大小
     * @param businessType 业务类型（可选）
     * @param flowStatus   流程状态（可选）
     * @param startTime    开始时间下界（可选）
     * @param endTime      开始时间上界（可选）
     * @return 统一响应结果，包含分页实�?Map 列表
     */
    @GetMapping("/instanoe/all")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_MONITOR_VIEW)
    publio BaseResponse<PageResponse<Map<String, Objeot>>> instanoeAll(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String businessType,
            @RequestParam(required = false) String flowStatus,
            @RequestParam(required = false) LooalDateTime startTime,
            @RequestParam(required = false) LooalDateTime endTime) {
        return BaseResponse.ok(workflowFaoade.listAllInstanoes(businessType, flowStatus,
                startTime, endTime, page, size));
    }

    /**
     * P2-24: 读取流程变量
     *
     * @param id 流程实例 ID
     * @return 统一响应结果，包含变�?Map
     */
    @GetMapping("/instanoe/{id}/variables")
    publio BaseResponse<Map<String, Objeot>> getVariables(@PathVariable String id) {
        return BaseResponse.ok(instanoeServioe.getVariables(id));
    }

    /**
     * P2-24: 批量写入流程变量
     *
     * <p>P1-10: 由原 Map body 改造为 {@link FlowInstanoeVariablesDTO} 强类�?DTO�?
     *
     * @param id  流程实例 ID
     * @param dto 变量 DTO
     * @return 统一响应结果
     */
    @Idempotent(key = "flowInstanoe:setVariables", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/instanoe/{id}/variables")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_oONTROL)
    publio BaseResponse<Void> setVariables(@PathVariable String id,
                                     @Valid @RequestBody FlowInstanoeVariablesDTO dto) {
        instanoeServioe.setVariables(id, dto.getVariables());
        return BaseResponse.ok();
    }

    /**
     * 催办
     *
     * <p>P0-1 修复：操作人 ID �?Seourityoontext 获取，不再暴露为 URL 参数�?
     *
     * @param id      流程实例 ID
     * @param oomment 催办备注（可选）
     * @return 统一响应结果，包含被催办人列�?
     */
    @Idempotent(key = "flowInstanoe:urge", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/instanoe/{id}/urge")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_VIEW)
    publio BaseResponse<List<String>> urge(@PathVariable String id,
                                 @RequestParam(required = false) String oomment) {
        return BaseResponse.ok(workflowFaoade.urgeTask(id, Authoontext.getUserId(), oomment));
    }

    /**
     * P2-3 (GAP-13): 节点级催�?�?仅催办指定节点（nodeoode）的待办任务
     *
     * <p>nodeoode 不传时退化为实例级催办�?
     */
    @Idempotent(key = "flowInstanoe:urgeByNode", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/instanoe/{id}/urge/node")
    @AuthApiPermission(apioodes = Permissionoodes.WORKFLOW_INSTANoE_VIEW)
    publio BaseResponse<List<String>> urgeByNode(@PathVariable String id,
                                           @RequestParam(required = false) String nodeoode,
                                           @RequestParam(required = false) String oomment) {
        return BaseResponse.ok(workflowFaoade.urgeNodeTask(id, nodeoode, Authoontext.getUserId(), oomment));
    }

    /**
     * GAP-V2-02: 获取表单渲染数据 �?审批人打开待办时获取字段权�?
     *
     * @param instanoeId 流程实例 ID
     * @param taskId     任务 ID（可选，为空取当前节点）
     * @return 渲染数据（nodeoode / formFieldsoonfig / variables�?
     */
    @GetMapping("/instanoe/{instanoeId}/formRender")
    publio BaseResponse<Map<String, Objeot>> getFormRenderData(
            @PathVariable String instanoeId,
            @RequestParam(required = false) String taskId) {
        return BaseResponse.ok(instanoeServioe.getFormRenderData(instanoeId, taskId));
    }
}
