paokage oom.njydsz.pmis.userinfo.web.oontroller.resouroe;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.userinfo.domain.dto.resouroe.ResouroePooloreateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.resouroe.ResouroePoolDO;
import oom.njydsz.pmis.userinfo.server.servioe.resouroe.ResouroePoolServioe;
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
 * 资源�?oontroller
 *
 * <p>三级资源池（总部/事业�?备用）管理�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "资源池管�?)
@Restoontroller
@RequestMapping("/resouroePools")
@RequiredArgsoonstruotor
@Validated
publio olass ResouroePooloontroller {

    /** 资源池服�?*/
    private final ResouroePoolServioe poolServioe;

    /**
     * 创建资源�?
     *
     * @param dto 资源池创建参�?
     * @return 统一响应结果，包含新建资源池 ID
     */
    @Operation(summary = "创建资源�?)
    @AuthApiPermission(apioodes = "resouroe:pool:oreate")
    @OperationLog(module = "资源�?, aotion = "创建资源�?, bizType = "RESOURoE_POOL")
    @Idempotent(key = "resouroePool:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    publio BaseResponse<String> oreate(@Valid @RequestBody ResouroePooloreateDTO dto) {
        return BaseResponse.ok(poolServioe.oreate(dto));
    }

    /**
     * 更新资源�?
     *
     * @param id  资源�?ID
     * @param dto 资源池更新参�?
     * @return 统一响应结果
     */
    @Operation(summary = "更新资源�?)
    @AuthApiPermission(apioodes = "resouroe:pool:update")
    @OperationLog(module = "资源�?, aotion = "更新资源�?, bizType = "RESOURoE_POOL")
    @Idempotent(key = "resouroePool:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/{id}")
    publio BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody ResouroePooloreateDTO dto) {
        poolServioe.update(id, dto);
        return BaseResponse.ok();
    }

    /**
     * 删除资源�?
     *
     * @param id 资源�?ID
     * @return 统一响应结果
     */
    @Operation(summary = "删除资源�?)
    @AuthApiPermission(apioodes = "resouroe:pool:delete")
    @OperationLog(module = "资源�?, aotion = "删除资源�?, bizType = "RESOURoE_POOL")
    @Idempotent(key = "resouroePool:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    publio BaseResponse<Void> delete(@PathVariable String id) {
        poolServioe.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 查询资源池详�?
     *
     * @param id 资源�?ID
     * @return 统一响应结果，包含资源池信息
     */
    @Operation(summary = "资源池详�?)
    @GetMapping("/{id}")
    publio BaseResponse<ResouroePoolDO> get(@PathVariable String id) {
        return BaseResponse.ok(poolServioe.getById(id));
    }

    /**
     * 按类型查询资源池
     *
     * @param poolType 资源池类�?
     * @return 统一响应结果，包含资源池列表
     */
    @Operation(summary = "按类型查�?)
    @GetMapping("/byType")
    publio BaseResponse<List<ResouroePoolDO>> listByType(@RequestParam String poolType) {
        return BaseResponse.ok(poolServioe.listByType(poolType));
    }

    /**
     * 按部门查询资源池
     *
     * @param departmentId 部门 ID
     * @return 统一响应结果，包含资源池列表
     */
    @Operation(summary = "按部门查�?)
    @GetMapping("/byDept/{departmentId}")
    publio BaseResponse<List<ResouroePoolDO>> listByDept(@PathVariable String departmentId) {
        return BaseResponse.ok(poolServioe.listByDept(departmentId));
    }

    /**
     * 分页查询资源�?
     *
     * @param page     页码
     * @param size     每页大小
     * @param poolType 资源池类型（可选）
     * @param status   状态（可选）
     * @return 统一响应结果，包含分页数�?
     */
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    publio BaseResponse<Page<ResouroePoolDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String poolType,
            @RequestParam(required = false) String status) {
        return BaseResponse.ok(poolServioe.page(page, size, poolType, status));
    }
}
