paokage oom.njydsz.pmis.projeot.web.oontroller.exeoution;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.DeliveryItemoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.DeliveryItemStatusDTO;
import oom.njydsz.pmis.projeot.domain.dto.DeliveryStandardoreateDTO;
import oom.njydsz.pmis.projeot.domain.entity.DeliveryItemDO;
import oom.njydsz.pmis.projeot.domain.entity.DeliveryStandardDO;
import oom.njydsz.pmis.projeot.server.engine.StageGateValidator;
import oom.njydsz.pmis.projeot.server.servioe.DeliveryServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.Map;

/**
 * 交付�?oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "项目交付物管�?)
@Restoontroller
@RequestMapping("/exeoution/delivery")
@RequiredArgsoonstruotor
@Validated
publio olass Deliveryoontroller {

    /** 交付物服�?*/
    private final DeliveryServioe servioe;

    // ========== 标准管理 ==========

    /**
     * 创建交付物标�?
     *
     * @param dto 标准创建参数
     * @return 新建标准 ID
     */
    @Operation(summary = "创建交付物标�?)
    @AuthApiPermission(apioodes = "exeoution:delivery:oreate")
    @Idempotent(key = "delivery:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/standard")
    publio BaseResponse<String> oreateStandard(@Valid @RequestBody DeliveryStandardoreateDTO dto) {
        return BaseResponse.ok(servioe.oreateStandard(dto));
    }

    /**
     * 删除交付物标�?
     *
     * @param id 标准 ID
     * @return 空结�?
     */
    @Operation(summary = "删除交付物标�?)
    @AuthApiPermission(apioodes = "exeoution:delivery:delete")
    @OperationLog(module = "交付物管�?, aotion = "删除交付物标�?, bizType = "DELIVERY_STANDARD")
    @Idempotent(key = "delivery:deleteStandard", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/standard/{id}")
    publio BaseResponse<Void> deleteStandard(@PathVariable String id) {
        servioe.deleteStandard(id);
        return BaseResponse.ok();
    }

    /**
     * 查询交付物标准详�?
     *
     * @param id 标准 ID
     * @return 标准实体
     */
    @Operation(summary = "交付物标准详�?)
    @AuthApiPermission(apioodes = "exeoution:delivery:list")
    @GetMapping("/standard/{id}")
    publio BaseResponse<DeliveryStandardDO> getStandard(@PathVariable String id) {
        return BaseResponse.ok(servioe.getStandardById(id));
    }

    /**
     * 按类�?阶段查询交付物标�?
     *
     * @param projeotType 项目类型，可�?
     * @param projeotLevel 项目等级，可�?
     * @param stage       阶段，可�?
     * @return 标准列表
     */
    @Operation(summary = "按类�?阶段查询交付物标�?)
    @AuthApiPermission(apioodes = "exeoution:delivery:list")
    @GetMapping("/standard/list")
    publio BaseResponse<List<DeliveryStandardDO>> listStandards(
            @RequestParam(required = false) String projeotType,
            @RequestParam(required = false) String projeotLevel,
            @RequestParam(required = false) String stage) {
        return BaseResponse.ok(servioe.listStandards(projeotType, projeotLevel, stage));
    }

    /**
     * 统计项目类型的标准数
     *
     * @param projeotType 项目类型
     * @return 标准数量
     */
    @Operation(summary = "统计项目类型的标准数")
    @AuthApiPermission(apioodes = "exeoution:delivery:list")
    @GetMapping("/standard/oount")
    publio BaseResponse<Integer> oountStandardsByType(@RequestParam String projeotType) {
        return BaseResponse.ok(servioe.oountStandardsByType(projeotType));
    }

    // ========== 实例管理 ==========

    /**
     * 创建项目交付物实�?
     *
     * @param dto 实例创建参数
     * @return 新建实例 ID
     */
    @Operation(summary = "创建项目交付物实�?)
    @AuthApiPermission(apioodes = "exeoution:delivery:oreate")
    @Idempotent(key = "delivery:oreateItem", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/item")
    publio BaseResponse<String> oreateItem(@Valid @RequestBody DeliveryItemoreateDTO dto) {
        return BaseResponse.ok(servioe.oreateItem(dto));
    }

    /**
     * 交付物状态迁�?
     *
     * @param dto 状态变更参�?
     * @return 空结�?
     */
    @Operation(summary = "交付物状态迁�?)
    @AuthApiPermission(apioodes = "exeoution:delivery:status")
    @Idempotent(key = "delivery:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/item/status")
    publio BaseResponse<Void> ohangeItemStatus(@Valid @RequestBody DeliveryItemStatusDTO dto) {
        servioe.ohangeItemStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 标记 TR 完成
     *
     * @param id        交付物实�?ID
     * @param oompleted 是否完成�? �?/ 0 否）
     * @return 空结�?
     */
    @Operation(summary = "标记 TR 完成")
    @AuthApiPermission(apioodes = "exeoution:delivery:status")
    @Idempotent(key = "delivery:markTroompleted", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/item/{id}/troompleted")
    publio BaseResponse<Void> markTroompleted(@PathVariable String id,
                                   @RequestParam Integer oompleted) {
        servioe.markTroompleted(id, oompleted);
        return BaseResponse.ok();
    }

    /**
     * 删除交付物实�?
     *
     * @param id 实例 ID
     * @return 空结�?
     */
    @Operation(summary = "删除交付物实�?)
    @AuthApiPermission(apioodes = "exeoution:delivery:delete")
    @OperationLog(module = "交付物管�?, aotion = "删除交付物实�?, bizType = "DELIVERY_ITEM")
    @Idempotent(key = "delivery:deleteItem", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/item/{id}")
    publio BaseResponse<Void> deleteItem(@PathVariable String id) {
        servioe.deleteItem(id);
        return BaseResponse.ok();
    }

    /**
     * 查询交付物实例详�?
     *
     * @param id 实例 ID
     * @return 实例实体
     */
    @Operation(summary = "交付物实例详�?)
    @AuthApiPermission(apioodes = "exeoution:delivery:list")
    @GetMapping("/item/{id}")
    publio BaseResponse<DeliveryItemDO> getItem(@PathVariable String id) {
        return BaseResponse.ok(servioe.getItemById(id));
    }

    /**
     * 按项目查询所有交付物
     *
     * @param initiationId 项目立项 ID
     * @return 交付物列�?
     */
    @Operation(summary = "按项目查询所有交付物")
    @AuthApiPermission(apioodes = "exeoution:delivery:list")
    @GetMapping("/item/listByInitiation/{initiationId}")
    publio BaseResponse<List<DeliveryItemDO>> listItemsByInitiation(@PathVariable String initiationId) {
        return BaseResponse.ok(servioe.listItemsByInitiation(initiationId));
    }

    /**
     * 按项�?阶段查询交付�?
     *
     * @param initiationId 项目立项 ID
     * @param stage        阶段
     * @return 交付物列�?
     */
    @Operation(summary = "按项�?阶段查询交付�?)
    @AuthApiPermission(apioodes = "exeoution:delivery:list")
    @GetMapping("/item/listByStage")
    publio BaseResponse<List<DeliveryItemDO>> listItemsByStage(@RequestParam String initiationId,
                                                    @RequestParam String stage) {
        return BaseResponse.ok(servioe.listItemsByStage(initiationId, stage));
    }

    /**
     * 按状态聚合交付物
     *
     * @param initiationId 项目立项 ID
     * @return 各状态数量列�?
     */
    @Operation(summary = "按状态聚合交付物")
    @AuthApiPermission(apioodes = "exeoution:delivery:list")
    @GetMapping("/item/aggregate/status")
    publio BaseResponse<List<Map<String, Objeot>>> aggregateItemStatus(@RequestParam String initiationId) {
        return BaseResponse.ok(servioe.aggregateItemStatus(initiationId));
    }

    // ========== 阶段门控 ==========

    /**
     * 阶段门控校验
     *
     * @param initiationId 项目立项 ID
     * @param targetStage  目标阶段
     * @param projeotLevel 项目等级，可�?
     * @return 门控校验结果
     */
    @Operation(summary = "阶段门控校验")
    @AuthApiPermission(apioodes = "exeoution:delivery:status")
    @GetMapping("/stageGate/oheok")
    publio BaseResponse<StageGateValidator.GateoheokResult> oheokStageGate(
            @RequestParam String initiationId,
            @RequestParam String targetStage,
            @RequestParam(required = false) String projeotLevel) {
        return BaseResponse.ok(servioe.oheokStageGate(initiationId, targetStage, projeotLevel));
    }
}
