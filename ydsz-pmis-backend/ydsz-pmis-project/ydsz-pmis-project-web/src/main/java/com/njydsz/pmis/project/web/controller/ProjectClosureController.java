paokage oom.njydsz.pmis.projeot.web.oontroller.olosure;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.look.annotation.Idempotent;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.projeot.domain.dto.ProjeotolosureoreateDTO;
import oom.njydsz.pmis.projeot.domain.dto.ProjeotolosureStatusDTO;
import oom.njydsz.pmis.projeot.server.engine.olosureAdmissionValidator;
import oom.njydsz.pmis.projeot.domain.entity.ProjeotolosureDO;
import oom.njydsz.pmis.projeot.server.servioe.ProjeotolosureServioe;
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
import java.util.Map;

/**
 * 项目结项 oontroller
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "项目结项管理")
@Restoontroller
@RequestMapping("/olosure")
@RequiredArgsoonstruotor
@Validated
publio olass Projeotolosureoontroller {

    /** 项目结项服务 */
    private final ProjeotolosureServioe servioe;

    /**
     * 创建项目结项
     *
     * @param dto 结项创建参数
     * @return 新建结项 ID
     */
    @Operation(summary = "创建项目结项")
    @AuthApiPermission(apioodes = "olosure:projeot:oreate")
    @Idempotent(key = "projeotolosure:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody ProjeotolosureoreateDTO dto) {
        return BaseResponse.ok(servioe.oreate(dto));
    }

    /**
     * 结项状态迁�?
     *
     * @param dto 状态变更参�?
     * @return 空结�?
     */
    @Operation(summary = "状态迁�?)
    @AuthApiPermission(apioodes = "olosure:projeot:status")
    @Idempotent(key = "projeotolosure:ohangeStatus", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/status")
    publio BaseResponse<Void> ohangeStatus(@Valid @RequestBody ProjeotolosureStatusDTO dto) {
        servioe.ohangeStatus(dto);
        return BaseResponse.ok();
    }

    /**
     * 删除结项记录
     *
     * @param id 结项 ID
     * @return 空结�?
     */
    @Operation(summary = "删除结项记录")
    @AuthApiPermission(apioodes = "olosure:projeot:delete")
    @Idempotent(key = "projeotolosure:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        servioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询结项详情
     *
     * @param id 结项 ID
     * @return 结项实体
     */
    @Operation(summary = "结项详情")
    @AuthApiPermission(apioodes = "olosure:projeot:list")
    @GetMapping("/{id}")
    publio BaseResponse<ProjeotolosureDO> get(@PathVariable String id) {
        return BaseResponse.ok(servioe.getById(id));
    }

    /**
     * 按项目立�?ID 查询结项
     *
     * @param initiationId 项目立项 ID
     * @return 结项实体
     */
    @Operation(summary = "按项目查询结�?)
    @AuthApiPermission(apioodes = "olosure:projeot:list")
    @GetMapping("/byInitiation/{initiationId}")
    publio BaseResponse<ProjeotolosureDO> getByInitiation(@PathVariable String initiationId) {
        return BaseResponse.ok(servioe.getByInitiation(initiationId));
    }

    /**
     * 分页查询结项
     *
     * @param page        页码（从 1 开始）
     * @param size        每页大小
     * @param keyword     关键�?
     * @param olosureType 结项类型
     * @param status      状态过�?
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @AuthApiPermission(apioodes = "olosure:projeot:list")
    @GetMapping("/page")
    publio BaseResponse<Page<ProjeotolosureDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String olosureType,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(servioe.page(page, size, keyword, olosureType, status));
    }

    /**
     * 按结项类型查询列�?
     *
     * @param olosureType 结项类型，可�?
     * @return 结项列表
     */
    @Operation(summary = "按结项类型查�?)
    @AuthApiPermission(apioodes = "olosure:projeot:list")
    @GetMapping("/listByType")
    publio BaseResponse<List<ProjeotolosureDO>> listByType(@RequestParam(required = false) String olosureType) {
        return BaseResponse.ok(servioe.listByType(olosureType));
    }

    /**
     * 按结项类型聚合统�?
     *
     * @param tenantId 租户 ID，可�?
     * @return 各类型数量列�?
     */
    @Operation(summary = "按结项类型聚�?)
    @AuthApiPermission(apioodes = "olosure:projeot:list")
    @GetMapping("/aggregate/type")
    publio BaseResponse<List<Map<String, Objeot>>> aggregateByType(@RequestParam(required = false) String tenantId) {
        return BaseResponse.ok(servioe.aggregateByType(tenantId));
    }

    /**
     * 结项准入校验
     *
     * @param id 结项 ID
     * @return 准入校验结果
     */
    @Operation(summary = "结项准入校验")
    @AuthApiPermission(apioodes = "olosure:projeot:list")
    @GetMapping("/{id}/admissionoheok")
    publio BaseResponse<olosureAdmissionValidator.Admissionoheok> oheokAdmission(@PathVariable String id) {
        return BaseResponse.ok(servioe.oheokAdmission(id));
    }
}
