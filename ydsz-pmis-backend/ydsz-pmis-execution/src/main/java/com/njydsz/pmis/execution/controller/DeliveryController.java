package com.njydsz.pmis.execution.controller;

import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.execution.dto.DeliveryItemCreateDTO;
import com.njydsz.pmis.execution.dto.DeliveryItemStatusDTO;
import com.njydsz.pmis.execution.dto.DeliveryStandardCreateDTO;
import com.njydsz.pmis.execution.entity.DeliveryItemDO;
import com.njydsz.pmis.execution.entity.DeliveryStandardDO;
import com.njydsz.pmis.execution.engine.StageGateValidator;
import com.njydsz.pmis.execution.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class DeliveryController {

    private final DeliveryService service;

    // ========== 标准管理 ==========

    @Operation(summary = "创建交付物标准")
    @PostMapping("/standard")
    public R<Long> createStandard(@Valid @RequestBody DeliveryStandardCreateDTO dto) {
        return R.ok(service.createStandard(dto));
    }

    @Operation(summary = "删除交付物标准")
    @DeleteMapping("/standard/{id}")
    public R<Void> deleteStandard(@PathVariable Long id) {
        service.deleteStandard(id);
        return R.ok();
    }

    @Operation(summary = "交付物标准详情")
    @GetMapping("/standard/{id}")
    public R<DeliveryStandardDO> getStandard(@PathVariable Long id) {
        return R.ok(service.getStandardById(id));
    }

    @Operation(summary = "按类型/阶段查询交付物标准")
    @GetMapping("/standard/list")
    public R<List<DeliveryStandardDO>> listStandards(
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String projectLevel,
            @RequestParam(required = false) String stage) {
        return R.ok(service.listStandards(projectType, projectLevel, stage));
    }

    @Operation(summary = "统计项目类型的标准数")
    @GetMapping("/standard/count")
    public R<Long> countStandardsByType(@RequestParam String projectType) {
        return R.ok(service.countStandardsByType(projectType));
    }

    // ========== 实例管理 ==========

    @Operation(summary = "创建项目交付物实例")
    @PostMapping("/item")
    public R<Long> createItem(@Valid @RequestBody DeliveryItemCreateDTO dto) {
        return R.ok(service.createItem(dto));
    }

    @Operation(summary = "交付物状态迁移")
    @PutMapping("/item/status")
    public R<Void> changeItemStatus(@Valid @RequestBody DeliveryItemStatusDTO dto) {
        service.changeItemStatus(dto);
        return R.ok();
    }

    @Operation(summary = "标记 TR 完成")
    @PutMapping("/item/{id}/tr-completed")
    public R<Void> markTrCompleted(@PathVariable Long id,
                                   @RequestParam Integer completed) {
        service.markTrCompleted(id, completed);
        return R.ok();
    }

    @Operation(summary = "删除交付物实例")
    @DeleteMapping("/item/{id}")
    public R<Void> deleteItem(@PathVariable Long id) {
        service.deleteItem(id);
        return R.ok();
    }

    @Operation(summary = "交付物实例详情")
    @GetMapping("/item/{id}")
    public R<DeliveryItemDO> getItem(@PathVariable Long id) {
        return R.ok(service.getItemById(id));
    }

    @Operation(summary = "按项目查询所有交付物")
    @GetMapping("/item/list-by-initiation/{initiationId}")
    public R<List<DeliveryItemDO>> listItemsByInitiation(@PathVariable Long initiationId) {
        return R.ok(service.listItemsByInitiation(initiationId));
    }

    @Operation(summary = "按项目+阶段查询交付物")
    @GetMapping("/item/list-by-stage")
    public R<List<DeliveryItemDO>> listItemsByStage(@RequestParam Long initiationId,
                                                    @RequestParam String stage) {
        return R.ok(service.listItemsByStage(initiationId, stage));
    }

    @Operation(summary = "按状态聚合交付物")
    @GetMapping("/item/aggregate/status")
    public R<List<Map<String, Object>>> aggregateItemStatus(@RequestParam Long initiationId) {
        return R.ok(service.aggregateItemStatus(initiationId));
    }

    // ========== 阶段门控 ==========

    @Operation(summary = "阶段门控校验")
    @GetMapping("/stage-gate/check")
    public R<StageGateValidator.GateCheckResult> checkStageGate(
            @RequestParam Long initiationId,
            @RequestParam String targetStage,
            @RequestParam(required = false) String projectLevel) {
        return R.ok(service.checkStageGate(initiationId, targetStage, projectLevel));
    }
}
