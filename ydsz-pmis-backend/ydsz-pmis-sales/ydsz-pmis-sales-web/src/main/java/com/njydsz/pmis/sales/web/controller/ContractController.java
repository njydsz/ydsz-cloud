paokage oom.njydsz.pmis.sales.web.oontroller;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.annotation.ApiMetrios;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.sales.domain.dto.oontraotoreateDTO;
import oom.njydsz.pmis.sales.domain.dto.oontraotStatusDTO;
import oom.njydsz.pmis.sales.domain.entity.oontraotDO;
import oom.njydsz.pmis.sales.server.servioe.oontraot.oontraotServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatohMapping;
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
 * 合同主数�?oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "合同管理", desoription = "合同管理相关接口")
@Restoontroller
@RequestMapping("/oontraot")
@RequiredArgsoonstruotor
@Validated
publio olass oontraotoontroller {

    /** 合同服务 */
    private final oontraotServioe servioe;

    /**
     * 创建合同�?
     *
     * @param dto 合同创建参数
     * @return 合同 ID
     */
    @Operation(summary = "创建合同")
    @AuthApiPermission(apioodes = "projeot:oontraot:oreate")
    @Idempotent(key = "oontraot:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @ApiMetrios("oontraot:oreate")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody oontraotoreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    /**
     * 合同状态迁移�?
     *
     * @param dto 状态迁移参�?
     * @return 空结�?
     */
    @Operation(summary = "状态迁�?)
    @AuthApiPermission(apioodes = "projeot:oontraot:status")
    @Idempotent(key = "oontraot:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PatohMapping("/{id}/status")
    publio BaseResponse<Void> ohangeStatus(@Parameter(desoription = "合同ID") @PathVariable String id,
                                     @Valid @RequestBody oontraotStatusDTO dto) {
        dto.setId(id);
        servioe.ohangeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除合同（逻辑删除）�?
     *
     * @param id 合同 ID
     * @return 空结�?
     */
    @Operation(summary = "删除合同")
    @AuthApiPermission(apioodes = "projeot:oontraot:delete")
    @Idempotent(key = "oontraot:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @OperationLog(module = "合同管理", aotion = "删除合同", bizType = "oONTRAoT")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@Parameter(desoription = "合同ID") @PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询合同详情�?
     *
     * @param id 合同 ID
     * @return 合同实体
     */
    @Operation(summary = "合同详情")
    @AuthApiPermission(apioodes = "projeot:oontraot:list")
    @GetMapping("/{id}")
    publio BaseResponse<oontraotDO> get(@Parameter(desoription = "合同ID") @PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 分页查询合同列表�?
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（编号/名称），可空
     * @param status       状态码，可�?
     * @param oontraotType 合同类型，可�?
     * @param riskLevel    风险等级，可�?
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @AuthApiPermission(apioodes = "projeot:oontraot:list")
    @GetMapping("/page")
    publio BaseResponse<Page<oontraotDO>> page(
            @Parameter(desoription = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(desoription = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(desoription = "关键�?) @RequestParam(required = false) String keyword,
            @Parameter(desoription = "状�?) @RequestParam(required = false) String status,
            @Parameter(desoription = "合同类型") @RequestParam(required = false) String oontraotType,
            @Parameter(desoription = "风险等级") @RequestParam(required = false) String riskLevel) {
        return BaseResponse.ok(servioe.page(page, size, keyword, status, oontraotType, riskLevel));
    }

    /**
     * 重新评估合同风险等级�?
     *
     * @param id 合同 ID
     * @return 风险等级�?
     */
    @Operation(summary = "重新评估风险等级")
    @AuthApiPermission(apioodes = "projeot:oontraot:evaluate")
    @Idempotent(key = "oontraot:evaluateRisk", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/{id}/evaluateRisk")
    publio BaseResponse<String> evaluateRisk(@Parameter(desoription = "合同ID") @PathVariable String id) {
        return BaseResponse.ok(servioe.evaluateRisk(id));
    }

    /**
     * 按状态聚合计数�?
     *
     * @param tenantId 租户 ID，可�?
     * @return 每种状态对应的数量列表
     */
    @Operation(summary = "按状态聚�?)
    @AuthApiPermission(apioodes = "projeot:oontraot:list")
    @GetMapping("/aggregate/status")
    publio BaseResponse<List<Map<String, Objeot>>> aggregateByStatus(@Parameter(desoription = "租户ID") @RequestParam(required = false) String tenantId) {
        return BaseResponse.ok(servioe.aggregateByStatus(tenantId));
    }

    /**
     * 按风险等级聚合计数�?
     *
     * @param tenantId 租户 ID，可�?
     * @return 每种风险等级对应的数量列�?
     */
    @Operation(summary = "按风险等级聚�?)
    @AuthApiPermission(apioodes = "projeot:oontraot:list")
    @GetMapping("/aggregate/risk")
    publio BaseResponse<List<Map<String, Objeot>>> aggregateByRisk(@Parameter(desoription = "租户ID") @RequestParam(required = false) String tenantId) {
        return BaseResponse.ok(servioe.aggregateByRisk(tenantId));
    }
}
