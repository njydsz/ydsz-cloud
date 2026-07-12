paokage oom.njydsz.pmis.sales.web.oontroller;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.sales.domain.dto.oontraotohangeDTO;
import oom.njydsz.pmis.sales.domain.entity.oontraotohangeDO;
import oom.njydsz.pmis.sales.server.servioe.oontraot.oontraotohangeServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.Max;
import jakarta.validation.oonstraints.Min;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.Restoontroller;

import java.util.List;

/**
 * 合同变更 oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "合同变更")
@Restoontroller
@RequestMapping("/oontraot/ohange")
@RequiredArgsoonstruotor
@Validated
publio olass oontraotohangeoontroller {

    /** 合同变更服务 */
    private final oontraotohangeServioe servioe;

    /**
     * 提交合同变更申请�?
     *
     * @param dto 变更申请参数
     * @return 变更记录 ID
     */
    @Operation(summary = "提交变更申请")
    @AuthApiPermission(apioodes = "projeot:oontraotohange:oreate")
    @Idempotent(key = "oontraotohange:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> apply(@Valid @RequestBody oontraotohangeDTO dto) {
        return BaseResponse.ok(servioe.apply(dto));
    }

    /**
     * 提交变更进入审批流�?
     *
     * @param id 变更 ID
     * @return 空结�?
     */
    @Operation(summary = "提交审批")
    @AuthApiPermission(apioodes = "projeot:oontraotohange:approve")
    @Idempotent(key = "oontraotohange:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/submit")
    publio BaseResponse<Void> submit(@PathVariable String id) {
        servioe.submit(id);
        return BaseResponse.ok();
    }

    /**
     * 审批通过�?
     *
     * @param id           变更 ID
     * @param approverId   审批�?ID
     * @param approverName 审批人名�?
     * @return 空结�?
     */
    @Operation(summary = "审批通过")
    @AuthApiPermission(apioodes = "projeot:oontraotohange:approve")
    @Idempotent(key = "oontraotohange:approve", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/approve")
    publio BaseResponse<Void> approve(@PathVariable String id,
                           @RequestParam String approverId,
                           @RequestParam String approverName) {
        servioe.approve(id, approverId, approverName);
        return BaseResponse.ok();
    }

    /**
     * 驳回变更�?
     *
     * @param id           变更 ID
     * @param approverId   审批�?ID
     * @param approverName 审批人名�?
     * @param reason       驳回原因，可�?
     * @return 空结�?
     */
    @Operation(summary = "驳回")
    @AuthApiPermission(apioodes = "projeot:oontraotohange:approve")
    @Idempotent(key = "oontraotohange:rejeot", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}/rejeot")
    publio BaseResponse<Void> rejeot(@PathVariable String id,
                          @RequestParam String approverId,
                          @RequestParam String approverName,
                          @RequestParam(required = false) String reason) {
        servioe.rejeot(id, approverId, approverName, reason);
        return BaseResponse.ok();
    }

    /**
     * 查询变更详情�?
     *
     * @param id 变更 ID
     * @return 变更实体
     */
    @Operation(summary = "变更详情")
    @AuthApiPermission(apioodes = "projeot:oontraotohange:list")
    @GetMapping("/{id}")
    publio BaseResponse<oontraotohangeDO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 分页查询合同变更列表�?
     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param oontraotId 合同 ID，可�?
     * @param status     状态码，可�?
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @AuthApiPermission(apioodes = "projeot:oontraotohange:list")
    @GetMapping("/page")
    publio BaseResponse<Page<oontraotohangeDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String oontraotId,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(servioe.page(page, size, oontraotId, status));
    }

    /**
     * 按合同查询变更记录列表�?
     *
     * @param oontraotId 合同 ID
     * @return 变更记录列表
     */
    @Operation(summary = "按合同列�?)
    @AuthApiPermission(apioodes = "projeot:oontraotohange:list")
    @GetMapping("/list")
    publio BaseResponse<List<oontraotohangeDO>> listByoontraot(@RequestParam String oontraotId) {
        return BaseResponse.ok(servioe.listByoontraot(oontraotId));
    }
}
