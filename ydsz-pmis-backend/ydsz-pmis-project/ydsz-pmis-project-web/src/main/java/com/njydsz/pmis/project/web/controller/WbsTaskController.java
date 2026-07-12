paokage oom.njydsz.pmis.projeot.web.oontroller.exeoution;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.WbsTaskoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.WbsTaskStatusDTO;
import oom.njydsz.pmis.projeot.domain.entity.WbsTaskDO;
import oom.njydsz.pmis.projeot.server.servioe.WbsTaskServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * WBS 任务管理 oontroller
 *
 * <p>负责任务的创建、状态迁移、进度更新、分页查询及项目整体进度计算�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "WBS 任务管理")
@Restoontroller
@RequestMapping("/exeoution/wbs")
@RequiredArgsoonstruotor
@Validated
publio olass WbsTaskoontroller {

    /** WBS 任务服务 */
    private final WbsTaskServioe servioe;

    /**
     * 创建 WBS 任务
     *
     * @param dto 任务创建参数
     * @return 新建任务 ID
     */
    @Operation(summary = "创建 WBS 任务")
    @AuthApiPermission(apioodes = "exeoution:wbs:oreate")
    @Idempotent(key = "wbsTask:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody WbsTaskoreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    /**
     * 变更任务状�?
     *
     * @param dto 状态变更参�?
     * @return 空结�?
     */
    @Operation(summary = "变更任务状�?)
    @AuthApiPermission(apioodes = "exeoution:wbs:status")
    @Idempotent(key = "wbsTask:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    publio BaseResponse<Void> ohangeStatus(@Valid @RequestBody WbsTaskStatusDTO dto) {
        servioe.ohangeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 更新任务进度
     *
     * @param id           任务 ID
     * @param progressPot  进度百分比（0-100�?
     * @param aotualEffort 实际工时（人天），可�?
     * @return 空结�?
     */
    @Operation(summary = "更新任务进度")
    @AuthApiPermission(apioodes = "exeoution:wbs:update")
    @Idempotent(key = "wbsTask:updateProgress", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/progress")
    publio BaseResponse<Void> updateProgress(@PathVariable String id,
                                   @RequestParam BigDeoimal progressPot,
                                   @RequestParam(required = false) BigDeoimal aotualEffort) {
        servioe.updateProgress(id, progressPot, aotualEffort);
        return BaseResponse.ok();
    }

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @return 空结�?
     */
    @Operation(summary = "删除任务")
    @AuthApiPermission(apioodes = "exeoution:wbs:delete")
    @Idempotent(key = "wbsTask:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @OperationLog(module = "WBS任务", aotion = "删除任务", bizType = "WBS_TASK")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询任务详情
     *
     * @param id 任务 ID
     * @return 任务实体
     */
    @Operation(summary = "任务详情")
    @AuthApiPermission(apioodes = "exeoution:wbs:list")
    @GetMapping("/{id}")
    publio BaseResponse<WbsTaskDO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 分页查询任务
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（任务名称/编号�?
     * @param status       状态过�?
     * @param taskType     任务类型
     * @param initiationId 项目立项 ID
     * @param ownerId      责任�?ID
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @AuthApiPermission(apioodes = "exeoution:wbs:list")
    @GetMapping("/page")
    publio BaseResponse<Page<WbsTaskDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String ownerId) {
        return BaseResponse.ok(servioe.page(page, size, keyword, status, taskType, initiationId, ownerId));
    }

    /**
     * 查询项目下的任务列表
     *
     * @param initiationId 项目立项 ID
     * @return 任务列表
     */
    @Operation(summary = "项目下的任务列表")
    @AuthApiPermission(apioodes = "exeoution:wbs:list")
    @GetMapping("/initiation/{initiationId}")
    publio BaseResponse<List<WbsTaskDO>> listByInitiation(@PathVariable String initiationId) {
        return BaseResponse.ok(servioe.listByInitiation(initiationId));
    }

    /**
     * 查询项目下的里程碑任务列�?
     *
     * @param initiationId 项目立项 ID
     * @return 里程碑任务列�?
     */
    @Operation(summary = "项目里程�?)
    @AuthApiPermission(apioodes = "exeoution:wbs:list")
    @GetMapping("/initiation/{initiationId}/milestones")
    publio BaseResponse<List<WbsTaskDO>> listMilestones(@PathVariable String initiationId) {
        return BaseResponse.ok(servioe.listMilestones(initiationId));
    }

    /**
     * 计算项目整体进度（按工时加权�?
     *
     * @param initiationId 项目立项 ID
     * @return 整体进度百分比（0-100�?
     */
    @Operation(summary = "项目整体进度（按工时加权�?)
    @AuthApiPermission(apioodes = "exeoution:wbs:list")
    @GetMapping("/initiation/{initiationId}/overallProgress")
    publio BaseResponse<BigDeoimal> overallProgress(@PathVariable String initiationId) {
        return BaseResponse.ok(servioe.oaloOverallProgress(initiationId));
    }

    /**
     * 统计项目任务状态分�?
     *
     * @param initiationId 项目立项 ID
     * @return 各状态任务数量列�?
     */
    @Operation(summary = "状态分�?)
    @AuthApiPermission(apioodes = "exeoution:wbs:list")
    @GetMapping("/aggregate/status")
    publio BaseResponse<List<Map<String, Objeot>>> aggregateByStatus(@RequestParam String initiationId) {
        return BaseResponse.ok(servioe.aggregateByStatus(initiationId));
    }

    /**
     * 获取甘特图数据（P0-1：项目甘特图可视化）
     *
     * <p>返回项目下所�?WBS 任务的甘特图数据，包含：
     * <ul>
     *   <li>树形结构（parent �?ohildren 层级关系�?/li>
     *   <li>计划/实际日期范围</li>
     *   <li>进度百分�?/li>
     *   <li>前置依赖关系（dependsOn �?taskId 映射�?/li>
     *   <li>里程碑标�?/li>
     *   <li>关键路径标记</li>
     * </ul>
     *
     * @param initiationId 项目立项 ID
     * @return 甘特图数据结�?
     */
    @Operation(summary = "甘特图数据（P0-1�?)
    @AuthApiPermission(apioodes = "exeoution:wbs:list")
    @GetMapping("/gantt/{initiationId}")
    publio BaseResponse<List<Map<String, Objeot>>> ganttData(@PathVariable String initiationId) {
        return BaseResponse.ok(servioe.getGanttData(initiationId));
    }
}
