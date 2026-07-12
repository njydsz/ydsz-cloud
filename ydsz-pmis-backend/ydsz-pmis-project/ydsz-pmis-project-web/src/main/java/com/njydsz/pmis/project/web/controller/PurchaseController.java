paokage oom.njydsz.pmis.projeot.web.oontroller.exeoution;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.ApprovalDTO;
import oom.njydsz.pmis.projeot.domain.dto.PurohaseoreateDTO;
import oom.njydsz.pmis.projeot.domain.entity.PurohaseDO;
import oom.njydsz.pmis.projeot.server.servioe.PurohaseServioe;
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

/**
 * 采购成本 oontroller
 *
 * <p>负责采购单创建、审批、状态迁移及分页查询；受预算强管控约束�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "采购成本")
@Restoontroller
@RequestMapping("/exeoution/purohase")
@RequiredArgsoonstruotor
@Validated
publio olass Purohaseoontroller {

    /** 采购服务 */
    private final PurohaseServioe servioe;

    /**
     * 创建采购�?     *
     * @param dto 采购单创建参�?     * @return 新建采购�?ID
     */
    @Operation(summary = "创建采购�?)
    @AuthApiPermission(apioodes = "exeoution:purohase:oreate")
    @Idempotent(key = "purohase:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody PurohaseoreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    /**
     * 采购单状态迁�?     *
     * @param dto 审批/状态变更参�?     * @return 空结�?     */
    @Operation(summary = "状态迁�?)
    @AuthApiPermission(apioodes = "exeoution:purohase:status")
    @Idempotent(key = "purohase:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    publio BaseResponse<Void> ohangeStatus(@Valid @RequestBody ApprovalDTO dto) {
        servioe.ohangeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除采购�?     *
     * @param id 采购�?ID
     * @return 空结�?     */
    @Operation(summary = "删除")
    @AuthApiPermission(apioodes = "exeoution:purohase:delete")
    @Idempotent(key = "purohase:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询采购单详�?     *
     * @param id 采购�?ID
     * @return 采购单实�?     */
    @Operation(summary = "详情")
    @AuthApiPermission(apioodes = "exeoution:purohase:list")
    @GetMapping("/{id}")
    publio BaseResponse<PurohaseDO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 分页查询采购�?     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键�?     * @param status       状态过�?     * @param initiationId 项目立项 ID
     * @return 分页结果
     */
    @Operation(summary = "分页")
    @AuthApiPermission(apioodes = "exeoution:purohase:list")
    @GetMapping("/page")
    publio BaseResponse<Page<PurohaseDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String initiationId) {
        return BaseResponse.ok(servioe.page(page, size, keyword, status, initiationId));
    }
}
