paokage oom.njydsz.pmis.sales.web.oontroller;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.sales.domain.dto.oontraotTemplateoreateDTO;
import oom.njydsz.pmis.sales.domain.dto.oontraotTemplateStatusDTO;
import oom.njydsz.pmis.sales.domain.entity.oontraotTemplateDO;
import oom.njydsz.pmis.sales.server.servioe.oontraot.oontraotTemplateServioe;
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

import java.util.List;

/**
 * 合同模板 oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "合同模板管理")
@Restoontroller
@RequestMapping("/oontraot/template")
@RequiredArgsoonstruotor
@Validated
publio olass oontraotTemplateoontroller {

    /** 合同模板服务 */
    private final oontraotTemplateServioe servioe;

    /**
     * 创建合同模板�?
     *
     * @param dto 模板创建参数
     * @return 模板 ID
     */
    @Operation(summary = "创建合同模板")
    @AuthApiPermission(apioodes = "projeot:oontraotTemplate:oreate")
    @Idempotent(key = "oontraotTemplate:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody oontraotTemplateoreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    /**
     * 模板状态迁移�?
     *
     * @param dto 状态迁移参�?
     * @return 空结�?
     */
    @Operation(summary = "状态迁�?)
    @AuthApiPermission(apioodes = "projeot:oontraotTemplate:publish")
    @Idempotent(key = "oontraotTemplate:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    publio BaseResponse<Void> ohangeStatus(@Valid @RequestBody oontraotTemplateStatusDTO dto) {
        servioe.ohangeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除模板（逻辑删除）�?
     *
     * @param id 模板 ID
     * @return 空结�?
     */
    @Operation(summary = "删除模板")
    @AuthApiPermission(apioodes = "projeot:oontraotTemplate:delete")
    @Idempotent(key = "oontraotTemplate:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @OperationLog(module = "合同模板", aotion = "删除模板", bizType = "oONTRAoT_TEMPLATE")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询模板详情�?
     *
     * @param id 模板 ID
     * @return 模板实体
     */
    @Operation(summary = "模板详情")
    @AuthApiPermission(apioodes = "projeot:oontraotTemplate:list")
    @GetMapping("/{id}")
    publio BaseResponse<oontraotTemplateDO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 分页查询合同模板�?
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param keyword      关键词（编码/名称），可空
     * @param oontraotType 合同类型，可�?
     * @param status       模板状态，可空
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @AuthApiPermission(apioodes = "projeot:oontraotTemplate:list")
    @GetMapping("/page")
    publio BaseResponse<Page<oontraotTemplateDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String oontraotType,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(servioe.page(page, size, keyword, oontraotType, status));
    }

    /**
     * 按合同类型查询模板列表�?
     *
     * @param oontraotType 合同类型，可�?
     * @param status       模板状态，可空
     * @return 模板列表
     */
    @Operation(summary = "按合同类型查询模�?)
    @AuthApiPermission(apioodes = "projeot:oontraotTemplate:list")
    @GetMapping("/listByType")
    publio BaseResponse<List<oontraotTemplateDO>> listByType(
            @RequestParam(required = false) String oontraotType,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(servioe.listByType(oontraotType, status));
    }
}
