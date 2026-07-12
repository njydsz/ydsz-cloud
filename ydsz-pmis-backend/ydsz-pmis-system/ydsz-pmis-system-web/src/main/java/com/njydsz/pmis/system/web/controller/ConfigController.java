package com.njydsz.pmis.system.web.controller.config;

import com.njydsz.pmis.common.lock.annotation.Idempotent;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.auth.annotation.AuthApiPermission;
import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.system.domain.dto.config.ConfigFormDTO;
import com.njydsz.pmis.system.domain.dto.config.ConfigQueryDTO;
import com.njydsz.pmis.system.domain.entity.config.ConfigDO;
import com.njydsz.pmis.system.server.service.config.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "系统-配置中心", description = "系统配置管理相关接口")
@RestController
@RequestMapping("/configs")
@RequiredArgsConstructor
@Validated
public class ConfigController {

    /** 系统配置服务 */
    private final ConfigService configService;

    /**
     * 配置分页查询
     *
     * @param query 查询条件
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "配置分页")
    @AuthApiPermission(apiCodes = "sys:config:list")
    @RateLimit(key = "config", qps = 50, windowSeconds = 60)
    @GetMapping
    public BaseResponse<Page<ConfigDO>> page(@Valid ConfigQueryDTO query) {
        return BaseResponse.ok(configService.page(query));
    }

    @Operation(summary = "按 group+key 查配置")
    @RateLimit(key = "config", qps = 50, windowSeconds = 60)
    @GetMapping("/byKey")
    /**
     * 按 group + key 精确查询配置项
     *
     * @param group 配置分组
     * @param key   配置键
     * @return 统一响应结果，包含配置实体
     */
    public BaseResponse<ConfigDO> getByKey(
            @Parameter(description = "配置分组") @RequestParam String group,
            @Parameter(description = "配置键") @RequestParam String key) {
        return BaseResponse.ok(configService.getByKey(group, key));
    }

    @Operation(summary = "按 group 查全部配置（key-value 形式）")
    @RateLimit(key = "config", qps = 50, windowSeconds = 60)
    @GetMapping("/group/{group}")
    /**
     * 按分组查询全部配置，以 key-value 形式返回
     *
     * @param group 配置分组
     * @return 统一响应结果，包含 key-value 映射
     */
    public BaseResponse<Map<String, String>> getGroup(
            @Parameter(description = "配置分组") @PathVariable String group) {
        return BaseResponse.ok(configService.getGroupAsMap(group));
    }

    @Operation(summary = "公开配置（前端可见）")
    @RateLimit(key = "config", qps = 50, windowSeconds = 60)
    @GetMapping("/public")
    /**
     * 查询公开配置（前端可见）
     *
     * @return 统一响应结果，包含公开配置列表
     */
    public BaseResponse<List<ConfigDO>> publicConfigs() {
        return BaseResponse.ok(configService.listPublic());
    }

    @Operation(summary = "创建配置")
    @AuthApiPermission(apiCodes = "sys:config:create")
    @OperationLog(module = "系统配置", action = "创建配置", bizType = "CONFIG")
    @Idempotent(key = "config:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    /**
     * 创建配置项
     *
     * @param dto 配置表单
     * @return 统一响应结果，包含新增配置 ID
     */
    public BaseResponse<String> create(@Valid @RequestBody ConfigFormDTO dto) {
        return BaseResponse.ok(configService.create(dto));
    }

    @Operation(summary = "更新配置")
    @AuthApiPermission(apiCodes = "sys:config:update")
    @OperationLog(module = "系统配置", action = "更新配置", bizType = "CONFIG")
    @Idempotent(key = "config:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    /**
     * 更新配置项
     *
     * @param dto 配置表单
     * @return 统一响应结果
     */
    public BaseResponse<Void> update(@Valid @RequestBody ConfigFormDTO dto) {
        configService.update(dto);
        return BaseResponse.ok();
    }

    @Operation(summary = "删除配置")
    @AuthApiPermission(apiCodes = "sys:config:delete")
    @OperationLog(module = "系统配置", action = "删除配置", bizType = "CONFIG")
    @Idempotent(key = "config:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    /**
     * 删除配置项
     *
     * @param id 配置 ID
     * @return 统一响应结果
     */
    public BaseResponse<Void> delete(
            @Parameter(description = "配置ID") @PathVariable @NotBlank String id) {
        configService.delete(id);
        return BaseResponse.ok();
    }

    @Operation(summary = "按分组批量删除")
    @AuthApiPermission(apiCodes = "sys:config:delete")
    @OperationLog(module = "系统配置", action = "按分组删除", bizType = "CONFIG")
    @Idempotent(key = "config:deleteByGroup", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/group/{group}")
    /**
     * 按分组批量删除配置
     *
     * @param group 配置分组
     * @return 统一响应结果，包含删除条数
     */
    public BaseResponse<Integer> deleteByGroup(
            @Parameter(description = "配置分组") @PathVariable String group) {
        return BaseResponse.ok(configService.deleteByGroup(group));
    }

    @Operation(summary = "按分组批量启停")
    @AuthApiPermission(apiCodes = "sys:config:update")
    @OperationLog(module = "系统配置", action = "按分组启停", bizType = "CONFIG")
    @Idempotent(key = "config:updateStatusByGroup", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping("/group/{group}/status/{status}")
    /**
     * 按分组批量启停配置
     *
     * @param group  配置分组
     * @param status 目标状态
     * @return 统一响应结果，包含受影响条数
     */
    public BaseResponse<Integer> updateStatusByGroup(
            @Parameter(description = "配置分组") @PathVariable String group,
            @Parameter(description = "状态") @PathVariable String status) {
        return BaseResponse.ok(configService.updateStatusByGroup(group, status));
    }

    @Operation(summary = "刷新缓存")
    @AuthApiPermission(apiCodes = "sys:config:refresh")
    @OperationLog(module = "系统配置", action = "刷新缓存", bizType = "CONFIG")
    @Idempotent(key = "config:refresh", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/refresh")
    /**
     * 刷新配置缓存
     *
     * @return 统一响应结果
     */
    public BaseResponse<Void> refresh() {
        configService.refreshCache();
        return BaseResponse.ok();
    }
}
