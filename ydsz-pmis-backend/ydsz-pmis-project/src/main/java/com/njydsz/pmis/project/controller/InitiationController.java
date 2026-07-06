package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.literule.spi.BudgetSnapshotProvider;
import com.njydsz.pmis.project.dto.BudgetItemDTO;
import com.njydsz.pmis.project.dto.GateReviewDTO;
import com.njydsz.pmis.project.dto.InitiationCreateDTO;
import com.njydsz.pmis.project.dto.InitiationStageDTO;
import com.njydsz.pmis.project.entity.BudgetItemDO;
import com.njydsz.pmis.project.entity.GateReviewDO;
import com.njydsz.pmis.project.entity.InitiationDO;
import com.njydsz.pmis.project.service.InitiationService;
import com.njydsz.pmis.project.vo.BudgetSnapshotVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
 * 立项管理 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "立项管理", description = "立项管理相关接口")
@RestController
@RequestMapping("/api/v1/project/initiation")
@RequiredArgsConstructor
@Validated
public class InitiationController {

    /** 立项服务 */
    private final InitiationService service;

    /** 预算快照提供者（SPI），用于批量查询预算快照 */
    private final BudgetSnapshotProvider budgetSnapshotProvider;

    /**
     * 提交立项。
     *
     * @param dto 立项创建参数
     * @return 立项 ID
     */
    @Operation(summary = "提交立项")
    @PrePermission("project:initiation:create")
    @OperationLog(module = "立项管理", action = "提交立项", bizType = "INITIATION", saveResult = true)
    @Idempotent(key = "initiation:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody InitiationCreateDTO dto) {
        return Result.ok(service.create(dto));
    }

    /**
     * 立项阶段迁移（遵循 InitiationStage 状态机）。
     *
     * @param dto 阶段迁移参数
     * @return 空结果
     */
    @Operation(summary = "阶段迁移")
    @PrePermission("project:initiation:update")
    @OperationLog(module = "立项管理", action = "阶段迁移", bizType = "INITIATION")
    @Idempotent(key = "initiation:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/stage")
    public Result<Void> changeStage(@Valid @RequestBody InitiationStageDTO dto) {
        service.changeStage(dto);
        return Result.ok();
    }

    /**
     * 删除立项（逻辑删除）。
     *
     * @param id 立项 ID
     * @return 空结果
     */
    @Operation(summary = "删除立项")
    @PrePermission("project:initiation:delete")
    @OperationLog(module = "立项管理", action = "删除立项", bizType = "INITIATION")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@Parameter(description = "立项ID") @PathVariable @Min(1) Long id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 查询立项详情。
     *
     * @param id 立项 ID
     * @return 立项实体
     */
    @Operation(summary = "立项详情")
    @PrePermission("project:initiation:list")
    @GetMapping("/{id}")
    public Result<InitiationDO> get(@Parameter(description = "立项ID") @PathVariable @Min(1) Long id) {
        return Result.ok(service.getById(id));
    }

    /**
     * 分页查询立项列表。
     *
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @param keyword     关键词（编号/名称），可空
     * @param stage       阶段码，可空
     * @param projectLevel 项目分级，可空
     * @param pmId        项目经理 ID，可空
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @PrePermission("project:initiation:list")
    @GetMapping("/page")
    public Result<Page<InitiationDO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "阶段") @RequestParam(required = false) String stage,
            @Parameter(description = "项目分级") @RequestParam(required = false) String projectLevel,
            @Parameter(description = "项目经理ID") @RequestParam(required = false) Long pmId) {
        return Result.ok(service.page(page, size, keyword, stage, projectLevel, pmId));
    }

    // ============= 预算 =============

    /**
     * 新增预算明细。
     *
     * @param dto 预算明细参数
     * @return 预算明细 ID
     */
    @Operation(summary = "新增预算明细")
    @PrePermission("project:initiation:budget")
    @OperationLog(module = "立项管理", action = "新增预算明细", bizType = "BUDGET", saveResult = true)
    @PostMapping("/budget")
    public Result<Long> addBudget(@Valid @RequestBody BudgetItemDTO dto) {
        return Result.ok(service.addBudgetItem(dto));
    }

    /**
     * 删除预算明细（逻辑删除）。
     *
     * @param id 预算明细 ID
     * @return 空结果
     */
    @Operation(summary = "删除预算明细")
    @PrePermission("project:initiation:budget")
    @OperationLog(module = "立项管理", action = "删除预算明细", bizType = "BUDGET")
    @DeleteMapping("/budget/{id}")
    public Result<Void> delBudget(@Parameter(description = "预算明细ID") @PathVariable @Min(1) Long id) {
        service.deleteBudgetItem(id);
        return Result.ok();
    }

    /**
     * 查询立项的预算明细列表。
     *
     * @param id 立项 ID
     * @return 预算明细列表
     */
    @Operation(summary = "预算明细列表")
    @PrePermission("project:initiation:budget")
    @GetMapping("/{id}/budget")
    public Result<List<BudgetItemDO>> listBudget(@Parameter(description = "立项ID") @PathVariable @Min(1) Long id) {
        return Result.ok(service.listBudget(id));
    }

    /**
     * 按分类汇总预算金额。
     *
     * @param id 立项 ID
     * @return 每个分类对应的金额汇总列表
     */
    @Operation(summary = "预算按分类汇总")
    @PrePermission("project:initiation:budget")
    @GetMapping("/{id}/budget/summary")
    public Result<List<Map<String, Object>>> sumBudget(@Parameter(description = "立项ID") @PathVariable @Min(1) Long id) {
        return Result.ok(service.sumBudgetByCategory(id));
    }

    /**
     * 重新汇总预算总额并落库。
     *
     * @param id 立项 ID
     * @return 汇总后的预算总额
     */
    @Operation(summary = "重新汇总预算总额")
    @PrePermission("project:initiation:budget")
    @OperationLog(module = "立项管理", action = "重新汇总预算总额", bizType = "BUDGET", saveResult = true)
    @PostMapping("/{id}/budget/recompute")
    public Result<BigDecimal> recomputeBudget(@Parameter(description = "立项ID") @PathVariable @Min(1) Long id) {
        return Result.ok(service.recomputeBudget(id));
    }

    // ============= 门径 =============

    /**
     * 提交门径评审。
     *
     * @param dto 门径评审参数
     * @return 评审记录 ID
     */
    @Operation(summary = "门径评审")
    @PrePermission("project:initiation:gate")
    @OperationLog(module = "立项管理", action = "门径评审", bizType = "GATE", saveResult = true)
    @PostMapping("/gate/review")
    public Result<Long> reviewGate(@Valid @RequestBody GateReviewDTO dto) {
        return Result.ok(service.reviewGate(dto));
    }

    /**
     * 查询立项的门径评审记录列表。
     *
     * @param id 立项 ID
     * @return 评审记录列表
     */
    @Operation(summary = "门径评审记录")
    @PrePermission("project:initiation:gate")
    @GetMapping("/{id}/gate/reviews")
    public Result<List<GateReviewDO>> listGateReviews(@Parameter(description = "立项ID") @PathVariable @Min(1) Long id) {
        return Result.ok(service.listGateReviews(id));
    }

    // ============= 统计 =============

    /**
     * 按阶段聚合计数。
     *
     * @param tenantId 租户 ID，可空
     * @return 每个阶段对应的数量列表
     */
    @Operation(summary = "按阶段聚合")
    @PrePermission("project:initiation:list")
    @GetMapping("/aggregate/stage")
    public Result<List<Map<String, Object>>> aggregateByStage(@Parameter(description = "租户ID") @RequestParam(required = false) Long tenantId) {
        return Result.ok(service.aggregateByStage(tenantId));
    }

    /**
     * 查询立项预算快照（供执行模块调用）。
     *
     * @param id 立项 ID
     * @return 预算快照信息
     */
    @Operation(summary = "查询立项预算（供执行模块调用）")
    @PrePermission("project:initiation:budget")
    @GetMapping("/{id}/budget/snapshot")
    public Result<Map<String, Object>> budgetSnapshot(@Parameter(description = "立项ID") @PathVariable @Min(1) Long id) {
        return Result.ok(service.budgetSnapshot(id));
    }

    /**
     * 批量查询项目预算快照。
     *
     * <p>返回全部预算预警相关项目的预算快照（projectId/projectName/totalBudget/incurredCost/usageRatio），
     * 供前端预算看板、预警大盘等场景使用。底层通过 SPI 接口
     * {@link BudgetSnapshotProvider#getBudgetSnapshots()} 获取数据。
     *
     * @return 预算快照列表
     */
    @Operation(summary = "批量查询项目预算快照")
    @PrePermission("project:initiation:budget")
    @GetMapping("/budget/snapshots")
    public Result<List<BudgetSnapshotVO>> batchBudgetSnapshots() {
        List<BudgetSnapshotVO> vos = budgetSnapshotProvider.getBudgetSnapshots().stream()
                .map(BudgetSnapshotVO::from)
                .toList();
        return Result.ok(vos);
    }

    // ============= 流程状态联动（供 workflow 模块 Feign 调用） =============

    /**
     * 标记立项为审批中。
     *
     * @param id 立项 ID
     * @return 空结果
     */
    @Operation(summary = "标记审批中")
    @PrePermission("project:initiation:update")
    @OperationLog(module = "立项管理", action = "标记审批中（流程回调）", bizType = "INITIATION")
    @PostMapping("/{id}/mark-processing")
    public Result<Void> markProcessing(@Parameter(description = "立项ID") @PathVariable @Min(1) Long id) {
        service.markProcessing(id);
        return Result.ok();
    }

    /**
     * 标记立项为已批准。
     *
     * @param id 立项 ID
     * @return 空结果
     */
    @Operation(summary = "标记已批准")
    @PrePermission("project:initiation:update")
    @OperationLog(module = "立项管理", action = "标记已批准（流程回调）", bizType = "INITIATION")
    @PostMapping("/{id}/mark-approved")
    public Result<Void> markApproved(@Parameter(description = "立项ID") @PathVariable @Min(1) Long id) {
        service.markApproved(id);
        return Result.ok();
    }

    /**
     * 标记立项为已驳回。
     *
     * @param id     立项 ID
     * @param reason 驳回原因（可空）
     * @return 空结果
     */
    @Operation(summary = "标记已驳回")
    @PrePermission("project:initiation:update")
    @OperationLog(module = "立项管理", action = "标记已驳回（流程回调）", bizType = "INITIATION")
    @PostMapping("/{id}/mark-rejected")
    public Result<Void> markRejected(@Parameter(description = "立项ID") @PathVariable @Min(1) Long id,
                                     @Parameter(description = "驳回原因") @RequestParam(required = false) String reason) {
        service.markRejected(id, reason);
        return Result.ok();
    }
}
