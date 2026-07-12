package com.njydsz.pmis.project.web.controller.execution;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.project.domain.dto.WbsTaskCreateDTO;
import com.njydsz.pmis.project.domain.dto.WbsTaskStatusDTO;
import com.njydsz.pmis.project.domain.entity.WbsTaskDO;
import com.njydsz.pmis.project.server.service.WbsTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * WBS 任务管理 Controller
 *
 * <p>负责任务的创建、状态迁移、进度更新、分页查询及项目整体进度计算。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "WBS 任务管理")
@RestController
@RequestMapping("/execution/wbs")
@RequiredArgsConstructor
@Validated
public class WbsTaskController {

    /** WBS 任务服务 */
    private final WbsTaskService service;

    /**
     * 创建 WBS 任务
     *
     * @param dto 任务创建参数
     * @return 新建任务 ID
     */
    @Operation(summary = "创建 WBS 任务")
    @AuthApiPermission(apiCodes = "execution:wbs:create")
    @Idempotent(key = "wbsTask:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> create(@Valid @RequestBody WbsTaskCreateDTO dto) {
        return BaseResponse.ok(service.create(dto));
    }

    /**
     * 变更任务状态
     *
     * @param dto 状态变更参数
     * @return 空结果
     */
    @Operation(summary = "变更任务状态")
    @AuthApiPermission(apiCodes = "execution:wbs:status")
    @Idempotent(key = "wbsTask:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    public BaseResponse<Void> changeStatus(@Valid @RequestBody WbsTaskStatusDTO dto) {
        service.changeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 更新任务进度
     *
     * @param id           任务 ID
     * @param progressPct  进度百分比（0-100）
     * @param actualEffort 实际工时（人天），可选
     * @return 空结果
     */
    @Operation(summary = "更新任务进度")
    @AuthApiPermission(apiCodes = "execution:wbs:update")
    @Idempotent(key = "wbsTask:updateProgress", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/progress")
    public BaseResponse<Void> updateProgress(@PathVariable String id,
                                   @RequestParam BigDecimal progressPct,
                                   @RequestParam(required = false) BigDecimal actualEffort) {
        service.updateProgress(id, progressPct, actualEffort);
        return BaseResponse.ok();
    }

    /**
     * 删除任务
     *
     * @param id 任务 ID
     * @return 空结果
     */
    @Operation(summary = "删除任务")
    @AuthApiPermission(apiCodes = "execution:wbs:delete")
    @Idempotent(key = "wbsTask:delete", ttlSeconds = 5, message = "请勿重复提交")
    @OperationLog(module = "WBS任务", action = "删除任务", bizType = "WBS_TASK")
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询任务详情
     *
     * @param id 任务 ID
     * @return 任务实体
     */
    @Operation(summary = "任务详情")
    @AuthApiPermission(apiCodes = "execution:wbs:list")
    @GetMapping("/{id}")
    public BaseResponse<WbsTaskDO> get(@PathVariable String id) {
        return BaseResponse.ok(service.getById(id));
    }

    /**
     * 分页查询任务
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（任务名称/编号）
     * @param status       状态过滤
     * @param taskType     任务类型
     * @param initiationId 项目立项 ID
     * @param ownerId      责任人 ID
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @AuthApiPermission(apiCodes = "execution:wbs:list")
    @GetMapping("/page")
    public BaseResponse<Page<WbsTaskDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String ownerId) {
        return BaseResponse.ok(service.page(page, size, keyword, status, taskType, initiationId, ownerId));
    }

    /**
     * 查询项目下的任务列表
     *
     * @param initiationId 项目立项 ID
     * @return 任务列表
     */
    @Operation(summary = "项目下的任务列表")
    @AuthApiPermission(apiCodes = "execution:wbs:list")
    @GetMapping("/initiation/{initiationId}")
    public BaseResponse<List<WbsTaskDO>> listByInitiation(@PathVariable String initiationId) {
        return BaseResponse.ok(service.listByInitiation(initiationId));
    }

    /**
     * 查询项目下的里程碑任务列表
     *
     * @param initiationId 项目立项 ID
     * @return 里程碑任务列表
     */
    @Operation(summary = "项目里程碑")
    @AuthApiPermission(apiCodes = "execution:wbs:list")
    @GetMapping("/initiation/{initiationId}/milestones")
    public BaseResponse<List<WbsTaskDO>> listMilestones(@PathVariable String initiationId) {
        return BaseResponse.ok(service.listMilestones(initiationId));
    }

    /**
     * 计算项目整体进度（按工时加权）
     *
     * @param initiationId 项目立项 ID
     * @return 整体进度百分比（0-100）
     */
    @Operation(summary = "项目整体进度（按工时加权）")
    @AuthApiPermission(apiCodes = "execution:wbs:list")
    @GetMapping("/initiation/{initiationId}/overallProgress")
    public BaseResponse<BigDecimal> overallProgress(@PathVariable String initiationId) {
        return BaseResponse.ok(service.calcOverallProgress(initiationId));
    }

    /**
     * 统计项目任务状态分布
     *
     * @param initiationId 项目立项 ID
     * @return 各状态任务数量列表
     */
    @Operation(summary = "状态分布")
    @AuthApiPermission(apiCodes = "execution:wbs:list")
    @GetMapping("/aggregate/status")
    public BaseResponse<List<Map<String, Object>>> aggregateByStatus(@RequestParam String initiationId) {
        return BaseResponse.ok(service.aggregateByStatus(initiationId));
    }

    /**
     * 获取甘特图数据（P0-1：项目甘特图可视化）
     *
     * <p>返回项目下所有 WBS 任务的甘特图数据，包含：
     * <ul>
     *   <li>树形结构（parent → children 层级关系）</li>
     *   <li>计划/实际日期范围</li>
     *   <li>进度百分比</li>
     *   <li>前置依赖关系（dependsOn → taskId 映射）</li>
     *   <li>里程碑标记</li>
     *   <li>关键路径标记</li>
     * </ul>
     *
     * @param initiationId 项目立项 ID
     * @return 甘特图数据结构
     */
    @Operation(summary = "甘特图数据（P0-1）")
    @AuthApiPermission(apiCodes = "execution:wbs:list")
    @GetMapping("/gantt/{initiationId}")
    public BaseResponse<List<Map<String, Object>>> ganttData(@PathVariable String initiationId) {
        return BaseResponse.ok(service.getGanttData(initiationId));
    }
}
