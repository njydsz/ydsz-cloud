package com.njydsz.pmis.project.controller;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.DeliveryItemCreateDTO;
import com.njydsz.pmis.project.dto.DeliveryItemStatusDTO;
import com.njydsz.pmis.project.dto.DeliveryStandardCreateDTO;
import com.njydsz.pmis.project.entity.DeliveryItemDO;
import com.njydsz.pmis.project.entity.DeliveryStandardDO;
import com.njydsz.pmis.project.engine.StageGateValidator;
import com.njydsz.pmis.project.service.DeliveryService;
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

import java.util.List;
import java.util.Map;

/**
 * 交付物 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "项目交付物管理")
@RestController
@RequestMapping("/api/v1/execution/delivery")
@RequiredArgsConstructor
@Validated
public class DeliveryController {

    private final DeliveryService service;

    // ========== 标准管理 ==========

    /**
     * 创建交付物标准
     *
     * @param dto 标准创建参数
     * @return 新建标准 ID
     */
    @Operation(summary = "创建交付物标准")
    @PrePermission("execution:delivery:create")
    @Idempotent(key = "delivery:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/standard")
    public Result<Long> createStandard(@Valid @RequestBody DeliveryStandardCreateDTO dto) {
        return Result.ok(service.createStandard(dto));
    }

    /**
     * 删除交付物标准
     *
     * @param id 标准 ID
     * @return 空结果
     */
    @Operation(summary = "删除交付物标准")
    @PrePermission("execution:delivery:delete")
    @DeleteMapping("/standard/{id}")
    public Result<Void> deleteStandard(@PathVariable @Min(1) Long id) {
        service.deleteStandard(id);
        return Result.ok();
    }

    /**
     * 查询交付物标准详情
     *
     * @param id 标准 ID
     * @return 标准实体
     */
    @Operation(summary = "交付物标准详情")
    @PrePermission("execution:delivery:list")
    @GetMapping("/standard/{id}")
    public Result<DeliveryStandardDO> getStandard(@PathVariable @Min(1) Long id) {
        return Result.ok(service.getStandardById(id));
    }

    /**
     * 按类型/阶段查询交付物标准
     *
     * @param projectType 项目类型，可选
     * @param projectLevel 项目等级，可选
     * @param stage       阶段，可选
     * @return 标准列表
     */
    @Operation(summary = "按类型/阶段查询交付物标准")
    @PrePermission("execution:delivery:list")
    @GetMapping("/standard/list")
    public Result<List<DeliveryStandardDO>> listStandards(
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String projectLevel,
            @RequestParam(required = false) String stage) {
        return Result.ok(service.listStandards(projectType, projectLevel, stage));
    }

    /**
     * 统计项目类型的标准数
     *
     * @param projectType 项目类型
     * @return 标准数量
     */
    @Operation(summary = "统计项目类型的标准数")
    @PrePermission("execution:delivery:list")
    @GetMapping("/standard/count")
    public Result<Long> countStandardsByType(@RequestParam String projectType) {
        return Result.ok(service.countStandardsByType(projectType));
    }

    // ========== 实例管理 ==========

    /**
     * 创建项目交付物实例
     *
     * @param dto 实例创建参数
     * @return 新建实例 ID
     */
    @Operation(summary = "创建项目交付物实例")
    @PrePermission("execution:delivery:create")
    @PostMapping("/item")
    public Result<Long> createItem(@Valid @RequestBody DeliveryItemCreateDTO dto) {
        return Result.ok(service.createItem(dto));
    }

    /**
     * 交付物状态迁移
     *
     * @param dto 状态变更参数
     * @return 空结果
     */
    @Operation(summary = "交付物状态迁移")
    @PrePermission("execution:delivery:status")
    @Idempotent(key = "delivery:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/item/status")
    public Result<Void> changeItemStatus(@Valid @RequestBody DeliveryItemStatusDTO dto) {
        service.changeItemStatus(dto);
        return Result.ok();
    }

    /**
     * 标记 TR 完成
     *
     * @param id        交付物实例 ID
     * @param completed 是否完成（1 是 / 0 否）
     * @return 空结果
     */
    @Operation(summary = "标记 TR 完成")
    @PrePermission("execution:delivery:status")
    @PutMapping("/item/{id}/tr-completed")
    public Result<Void> markTrCompleted(@PathVariable @Min(1) Long id,
                                   @RequestParam Integer completed) {
        service.markTrCompleted(id, completed);
        return Result.ok();
    }

    /**
     * 删除交付物实例
     *
     * @param id 实例 ID
     * @return 空结果
     */
    @Operation(summary = "删除交付物实例")
    @PrePermission("execution:delivery:delete")
    @DeleteMapping("/item/{id}")
    public Result<Void> deleteItem(@PathVariable @Min(1) Long id) {
        service.deleteItem(id);
        return Result.ok();
    }

    /**
     * 查询交付物实例详情
     *
     * @param id 实例 ID
     * @return 实例实体
     */
    @Operation(summary = "交付物实例详情")
    @PrePermission("execution:delivery:list")
    @GetMapping("/item/{id}")
    public Result<DeliveryItemDO> getItem(@PathVariable @Min(1) Long id) {
        return Result.ok(service.getItemById(id));
    }

    /**
     * 按项目查询所有交付物
     *
     * @param initiationId 项目立项 ID
     * @return 交付物列表
     */
    @Operation(summary = "按项目查询所有交付物")
    @PrePermission("execution:delivery:list")
    @GetMapping("/item/list-by-initiation/{initiationId}")
    public Result<List<DeliveryItemDO>> listItemsByInitiation(@PathVariable @Min(1) Long initiationId) {
        return Result.ok(service.listItemsByInitiation(initiationId));
    }

    /**
     * 按项目+阶段查询交付物
     *
     * @param initiationId 项目立项 ID
     * @param stage        阶段
     * @return 交付物列表
     */
    @Operation(summary = "按项目+阶段查询交付物")
    @PrePermission("execution:delivery:list")
    @GetMapping("/item/list-by-stage")
    public Result<List<DeliveryItemDO>> listItemsByStage(@RequestParam Long initiationId,
                                                    @RequestParam String stage) {
        return Result.ok(service.listItemsByStage(initiationId, stage));
    }

    /**
     * 按状态聚合交付物
     *
     * @param initiationId 项目立项 ID
     * @return 各状态数量列表
     */
    @Operation(summary = "按状态聚合交付物")
    @PrePermission("execution:delivery:list")
    @GetMapping("/item/aggregate/status")
    public Result<List<Map<String, Object>>> aggregateItemStatus(@RequestParam Long initiationId) {
        return Result.ok(service.aggregateItemStatus(initiationId));
    }

    // ========== 阶段门控 ==========

    /**
     * 阶段门控校验
     *
     * @param initiationId 项目立项 ID
     * @param targetStage  目标阶段
     * @param projectLevel 项目等级，可选
     * @return 门控校验结果
     */
    @Operation(summary = "阶段门控校验")
    @PrePermission("execution:delivery:status")
    @GetMapping("/stage-gate/check")
    public Result<StageGateValidator.GateCheckResult> checkStageGate(
            @RequestParam Long initiationId,
            @RequestParam String targetStage,
            @RequestParam(required = false) String projectLevel) {
        return Result.ok(service.checkStageGate(initiationId, targetStage, projectLevel));
    }
}
