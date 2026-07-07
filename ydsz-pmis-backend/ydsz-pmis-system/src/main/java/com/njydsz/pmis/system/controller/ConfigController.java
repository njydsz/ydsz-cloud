package com.njydsz.pmis.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.annotation.RateLimit;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.system.dto.ConfigFormDTO;
import com.njydsz.pmis.system.dto.ConfigQueryDTO;
import com.njydsz.pmis.system.entity.ConfigDO;
import com.njydsz.pmis.system.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

    private final ConfigService configService;

    @Operation(summary = "配置分页")
    @PrePermission("sys:config:list")
    @RateLimit(key = "config", qps = 50, windowSeconds = 60)
    @GetMapping
    public Result<Page<ConfigDO>> page(ConfigQueryDTO query) {
        return Result.ok(configService.page(query));
    }

    @Operation(summary = "按 group+key 查配置")
    @RateLimit(key = "config", qps = 50, windowSeconds = 60)
    @GetMapping("/by-key")
    public Result<ConfigDO> getByKey(
            @Parameter(description = "配置分组") @RequestParam String group,
            @Parameter(description = "配置键") @RequestParam String key) {
        return Result.ok(configService.getByKey(group, key));
    }

    @Operation(summary = "按 group 查全部配置（key-value 形式）")
    @RateLimit(key = "config", qps = 50, windowSeconds = 60)
    @GetMapping("/group/{group}")
    public Result<Map<String, String>> getGroup(
            @Parameter(description = "配置分组") @PathVariable String group) {
        return Result.ok(configService.getGroupAsMap(group));
    }

    @Operation(summary = "公开配置（前端可见）")
    @RateLimit(key = "config", qps = 50, windowSeconds = 60)
    @GetMapping("/public")
    public Result<List<ConfigDO>> publicConfigs() {
        return Result.ok(configService.listPublic());
    }

    @Operation(summary = "创建配置")
    @PrePermission("sys:config:create")
    @OperationLog(module = "系统配置", action = "创建配置", bizType = "CONFIG")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ConfigFormDTO dto) {
        return Result.ok(configService.create(dto));
    }

    @Operation(summary = "更新配置")
    @PrePermission("sys:config:update")
    @OperationLog(module = "系统配置", action = "更新配置", bizType = "CONFIG")
    @PutMapping
    public Result<Void> update(@Valid @RequestBody ConfigFormDTO dto) {
        configService.update(dto);
        return Result.ok();
    }

    @Operation(summary = "删除配置")
    @PrePermission("sys:config:delete")
    @OperationLog(module = "系统配置", action = "删除配置", bizType = "CONFIG")
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @Parameter(description = "配置ID") @PathVariable @Min(1) Long id) {
        configService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "按分组批量删除")
    @PrePermission("sys:config:delete")
    @OperationLog(module = "系统配置", action = "按分组删除", bizType = "CONFIG")
    @DeleteMapping("/group/{group}")
    public Result<Integer> deleteByGroup(
            @Parameter(description = "配置分组") @PathVariable String group) {
        return Result.ok(configService.deleteByGroup(group));
    }

    @Operation(summary = "按分组批量启停")
    @PrePermission("sys:config:update")
    @OperationLog(module = "系统配置", action = "按分组启停", bizType = "CONFIG")
    @PutMapping("/group/{group}/status/{status}")
    public Result<Integer> updateStatusByGroup(
            @Parameter(description = "配置分组") @PathVariable String group,
            @Parameter(description = "状态") @PathVariable String status) {
        return Result.ok(configService.updateStatusByGroup(group, status));
    }

    @Operation(summary = "刷新缓存")
    @PrePermission("sys:config:refresh")
    @OperationLog(module = "系统配置", action = "刷新缓存", bizType = "CONFIG")
    @PostMapping("/refresh")
    public Result<Void> refresh() {
        configService.refreshCache();
        return Result.ok();
    }
}
