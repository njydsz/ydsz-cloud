package com.njydsz.pmis.project.web.controller.execution;

import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.project.domain.dto.DeliveryItemCreateDTO;
import com.njydsz.pmis.project.domain.dto.DeliveryItemStatusDTO;
import com.njydsz.pmis.project.domain.dto.DeliveryStandardCreateDTO;
import com.njydsz.pmis.project.domain.entity.DeliveryItemDO;
import com.njydsz.pmis.project.domain.entity.DeliveryStandardDO;
import com.njydsz.pmis.project.server.engine.StageGateValidator;
import com.njydsz.pmis.project.server.service.DeliveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/execution/delivery")
@RequiredArgsConstructor
@Validated
public class DeliveryController {

    /** 交付物服务 */
    private final DeliveryService service;

    // ========== 标准管理 ==========

    /**
     * 创建交付物标准
     *
     * @param dto 标准创建参数
     * @return 新建标准 ID
     */
    @Operation(summary = "创建交付物标准")
    @AuthApiPermission(apiCodes = "execution:delivery:create")
    @Idempotent(key = "delivery:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/standard")
    public BaseResponse<String> createStandard(@Valid @RequestBody DeliveryStandardCreateDTO dto) {
        return BaseResponse.ok(service.createStandard(dto));
    }

    /**
     * 删除交付物标准
     *
     * @param id 标准 ID
     * @return 空结果
     */
    @Operation(summary = "删除交付物标准")
    @AuthApiPermission(apiCodes = "execution:delivery:delete")
    @OperationLog(module = "交付物管理", action = "删除交付物标准", bizType = "DELIVERY_STANDARD")
    @Idempotent(key = "delivery:deleteStandard", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/standard/{id}")
    public BaseResponse<Void> deleteStandard(@PathVariable String id) {
        service.deleteStandard(id);
        return BaseResponse.ok();
    }

    /**
     * 查询交付物标准详情
     *
     * @param id 标准 ID
     * @return 标准实体
     */
    @Operation(summary = "交付物标准详情")
    @AuthApiPermission(apiCodes = "execution:delivery:list")
    @GetMapping("/standard/{id}")
    public BaseResponse<DeliveryStandardDO> getStandard(@PathVariable String id) {
        return BaseResponse.ok(service.getStandardById(id));
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
    @AuthApiPermission(apiCodes = "execution:delivery:list")
    @GetMapping("/standard/list")
    public BaseResponse<List<DeliveryStandardDO>> listStandards(
            @RequestParam(required = false) String projectType,
            @RequestParam(required = false) String projectLevel,
            @RequestParam(required = false) String stage) {
        return BaseResponse.ok(service.listStandards(projectType, projectLevel, stage));
    }

    /**
     * 统计项目类型的标准数
     *
     * @param projectType 项目类型
     * @return 标准数量
     */
    @Operation(summary = "统计项目类型的标准数")
    @AuthApiPermission(apiCodes = "execution:delivery:list")
    @GetMapping("/standard/count")
    public BaseResponse<Integer> countStandardsByType(@RequestParam String projectType) {
        return BaseResponse.ok(service.countStandardsByType(projectType));
    }

    // ========== 实例管理 ==========

    /**
     * 创建项目交付物实例
     *
     * @param dto 实例创建参数
     * @return 新建实例 ID
     */
    @Operation(summary = "创建项目交付物实例")
    @AuthApiPermission(apiCodes = "execution:delivery:create")
    @Idempotent(key = "delivery:createItem", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/item")
    public BaseResponse<String> createItem(@Valid @RequestBody DeliveryItemCreateDTO dto) {
        return BaseResponse.ok(service.createItem(dto));
    }

    /**
     * 交付物状态迁移
     *
     * @param dto 状态变更参数
     * @return 空结果
     */
    @Operation(summary = "交付物状态迁移")
    @AuthApiPermission(apiCodes = "execution:delivery:status")
    @Idempotent(key = "delivery:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/item/status")
    public BaseResponse<Void> changeItemStatus(@Valid @RequestBody DeliveryItemStatusDTO dto) {
        service.changeItemStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 标记 TR 完成
     *
     * @param id        交付物实例 ID
     * @param completed 是否完成（1 是 / 0 否）
     * @return 空结果
     */
    @Operation(summary = "标记 TR 完成")
    @AuthApiPermission(apiCodes = "execution:delivery:status")
    @Idempotent(key = "delivery:markTrCompleted", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/item/{id}/trCompleted")
    public BaseResponse<Void> markTrCompleted(@PathVariable String id,
                                   @RequestParam Integer completed) {
        service.markTrCompleted(id, completed);
        return BaseResponse.ok();
    }

    /**
     * 删除交付物实例
     *
     * @param id 实例 ID
     * @return 空结果
     */
    @Operation(summary = "删除交付物实例")
    @AuthApiPermission(apiCodes = "execution:delivery:delete")
    @OperationLog(module = "交付物管理", action = "删除交付物实例", bizType = "DELIVERY_ITEM")
    @Idempotent(key = "delivery:deleteItem", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/item/{id}")
    public BaseResponse<Void> deleteItem(@PathVariable String id) {
        service.deleteItem(id);
        return BaseResponse.ok();
    }

    /**
     * 查询交付物实例详情
     *
     * @param id 实例 ID
     * @return 实例实体
     */
    @Operation(summary = "交付物实例详情")
    @AuthApiPermission(apiCodes = "execution:delivery:list")
    @GetMapping("/item/{id}")
    public BaseResponse<DeliveryItemDO> getItem(@PathVariable String id) {
        return BaseResponse.ok(service.getItemById(id));
    }

    /**
     * 按项目查询所有交付物
     *
     * @param initiationId 项目立项 ID
     * @return 交付物列表
     */
    @Operation(summary = "按项目查询所有交付物")
    @AuthApiPermission(apiCodes = "execution:delivery:list")
    @GetMapping("/item/listByInitiation/{initiationId}")
    public BaseResponse<List<DeliveryItemDO>> listItemsByInitiation(@PathVariable String initiationId) {
        return BaseResponse.ok(service.listItemsByInitiation(initiationId));
    }

    /**
     * 按项目+阶段查询交付物
     *
     * @param initiationId 项目立项 ID
     * @param stage        阶段
     * @return 交付物列表
     */
    @Operation(summary = "按项目+阶段查询交付物")
    @AuthApiPermission(apiCodes = "execution:delivery:list")
    @GetMapping("/item/listByStage")
    public BaseResponse<List<DeliveryItemDO>> listItemsByStage(@RequestParam String initiationId,
                                                    @RequestParam String stage) {
        return BaseResponse.ok(service.listItemsByStage(initiationId, stage));
    }

    /**
     * 按状态聚合交付物
     *
     * @param initiationId 项目立项 ID
     * @return 各状态数量列表
     */
    @Operation(summary = "按状态聚合交付物")
    @AuthApiPermission(apiCodes = "execution:delivery:list")
    @GetMapping("/item/aggregate/status")
    public BaseResponse<List<Map<String, Object>>> aggregateItemStatus(@RequestParam String initiationId) {
        return BaseResponse.ok(service.aggregateItemStatus(initiationId));
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
    @AuthApiPermission(apiCodes = "execution:delivery:status")
    @GetMapping("/stageGate/check")
    public BaseResponse<StageGateValidator.GateCheckResult> checkStageGate(
            @RequestParam String initiationId,
            @RequestParam String targetStage,
            @RequestParam(required = false) String projectLevel) {
        return BaseResponse.ok(service.checkStageGate(initiationId, targetStage, projectLevel));
    }
}
