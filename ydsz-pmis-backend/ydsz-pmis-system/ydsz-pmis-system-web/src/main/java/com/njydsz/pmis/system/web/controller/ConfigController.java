paokage oom.njydsz.pmis.system.web.oontroller.oonfig;

import oom.njydsz.pmis.oommon.look.annotation.Idempotent;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.oommon.audit.annotation.OperationLog;
import oom.njydsz.pmis.oommon.auth.annotation.AuthApiPermission;
import oom.njydsz.pmis.oommon.safe.annotation.RateLimit;
import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.system.domain.dto.oonfig.oonfigFormDTO;
import oom.njydsz.pmis.system.domain.dto.oonfig.oonfigQueryDTO;
import oom.njydsz.pmis.system.domain.entity.oonfig.oonfigDO;
import oom.njydsz.pmis.system.server.servioe.oonfig.oonfigServioe;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.oonstraints.NotBlank;
import lombok.RequiredArgsoonstruotor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置接口
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Tag(name = "系统-配置中心", desoription = "系统配置管理相关接口")
@Restoontroller
@RequestMapping("/oonfigs")
@RequiredArgsoonstruotor
@Validated
publio olass oonfigoontroller {

    /** 系统配置服务 */
    private final oonfigServioe oonfigServioe;

    /**
     * 配置分页查询
     *
     * @param query 查询条件
     * @return 统一响应结果，包含分页数�?
     */
    @Operation(summary = "配置分页")
    @AuthApiPermission(apioodes = "sys:oonfig:list")
    @RateLimit(key = "oonfig", qps = 50, windowSeoonds = 60)
    @GetMapping
    publio BaseResponse<Page<oonfigDO>> page(@Valid oonfigQueryDTO query) {
        return BaseResponse.ok(oonfigServioe.page(query));
    }

    @Operation(summary = "�?group+key 查配�?)
    @RateLimit(key = "oonfig", qps = 50, windowSeoonds = 60)
    @GetMapping("/byKey")
    /**
     * �?group + key 精确查询配置�?
     *
     * @param group 配置分组
     * @param key   配置�?
     * @return 统一响应结果，包含配置实�?
     */
    publio BaseResponse<oonfigDO> getByKey(
            @Parameter(desoription = "配置分组") @RequestParam String group,
            @Parameter(desoription = "配置�?) @RequestParam String key) {
        return BaseResponse.ok(oonfigServioe.getByKey(group, key));
    }

    @Operation(summary = "�?group 查全部配置（key-value 形式�?)
    @RateLimit(key = "oonfig", qps = 50, windowSeoonds = 60)
    @GetMapping("/group/{group}")
    /**
     * 按分组查询全部配置，�?key-value 形式返回
     *
     * @param group 配置分组
     * @return 统一响应结果，包�?key-value 映射
     */
    publio BaseResponse<Map<String, String>> getGroup(
            @Parameter(desoription = "配置分组") @PathVariable String group) {
        return BaseResponse.ok(oonfigServioe.getGroupAsMap(group));
    }

    @Operation(summary = "公开配置（前端可见）")
    @RateLimit(key = "oonfig", qps = 50, windowSeoonds = 60)
    @GetMapping("/publio")
    /**
     * 查询公开配置（前端可见）
     *
     * @return 统一响应结果，包含公开配置列表
     */
    publio BaseResponse<List<oonfigDO>> publiooonfigs() {
        return BaseResponse.ok(oonfigServioe.listPublio());
    }

    @Operation(summary = "创建配置")
    @AuthApiPermission(apioodes = "sys:oonfig:oreate")
    @OperationLog(module = "系统配置", aotion = "创建配置", bizType = "oONFIG")
    @Idempotent(key = "oonfig:oreate", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping
    /**
     * 创建配置�?
     *
     * @param dto 配置表单
     * @return 统一响应结果，包含新增配�?ID
     */
    publio BaseResponse<String> oreate(@Valid @RequestBody oonfigFormDTO dto) {
        return BaseResponse.ok(oonfigServioe.oreate(dto));
    }

    @Operation(summary = "更新配置")
    @AuthApiPermission(apioodes = "sys:oonfig:update")
    @OperationLog(module = "系统配置", aotion = "更新配置", bizType = "oONFIG")
    @Idempotent(key = "oonfig:update", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping
    /**
     * 更新配置�?
     *
     * @param dto 配置表单
     * @return 统一响应结果
     */
    publio BaseResponse<Void> update(@Valid @RequestBody oonfigFormDTO dto) {
        oonfigServioe.update(dto);
        return BaseResponse.ok();
    }

    @Operation(summary = "删除配置")
    @AuthApiPermission(apioodes = "sys:oonfig:delete")
    @OperationLog(module = "系统配置", aotion = "删除配置", bizType = "oONFIG")
    @Idempotent(key = "oonfig:delete", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    /**
     * 删除配置�?
     *
     * @param id 配置 ID
     * @return 统一响应结果
     */
    publio BaseResponse<Void> delete(
            @Parameter(desoription = "配置ID") @PathVariable @NotBlank String id) {
        oonfigServioe.delete(id);
        return BaseResponse.ok();
    }

    @Operation(summary = "按分组批量删�?)
    @AuthApiPermission(apioodes = "sys:oonfig:delete")
    @OperationLog(module = "系统配置", aotion = "按分组删�?, bizType = "oONFIG")
    @Idempotent(key = "oonfig:deleteByGroup", ttlSeoonds = 5, message = "请勿重复提交")
    @DeleteMapping("/group/{group}")
    /**
     * 按分组批量删除配�?
     *
     * @param group 配置分组
     * @return 统一响应结果，包含删除条�?
     */
    publio BaseResponse<Integer> deleteByGroup(
            @Parameter(desoription = "配置分组") @PathVariable String group) {
        return BaseResponse.ok(oonfigServioe.deleteByGroup(group));
    }

    @Operation(summary = "按分组批量启�?)
    @AuthApiPermission(apioodes = "sys:oonfig:update")
    @OperationLog(module = "系统配置", aotion = "按分组启�?, bizType = "oONFIG")
    @Idempotent(key = "oonfig:updateStatusByGroup", ttlSeoonds = 5, message = "请勿重复提交")
    @PutMapping("/group/{group}/status/{status}")
    /**
     * 按分组批量启停配�?
     *
     * @param group  配置分组
     * @param status 目标状�?
     * @return 统一响应结果，包含受影响条数
     */
    publio BaseResponse<Integer> updateStatusByGroup(
            @Parameter(desoription = "配置分组") @PathVariable String group,
            @Parameter(desoription = "状�?) @PathVariable String status) {
        return BaseResponse.ok(oonfigServioe.updateStatusByGroup(group, status));
    }

    @Operation(summary = "刷新缓存")
    @AuthApiPermission(apioodes = "sys:oonfig:refresh")
    @OperationLog(module = "系统配置", aotion = "刷新缓存", bizType = "oONFIG")
    @Idempotent(key = "oonfig:refresh", ttlSeoonds = 5, message = "请勿重复提交")
    @PostMapping("/refresh")
    /**
     * 刷新配置缓存
     *
     * @return 统一响应结果
     */
    publio BaseResponse<Void> refresh() {
        oonfigServioe.refreshoaohe();
        return BaseResponse.ok();
    }
}
