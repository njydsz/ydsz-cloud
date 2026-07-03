package com.njydsz.pmis.config.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.OperationLog;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.config.dto.ConfigFormDTO;
import com.njydsz.pmis.config.dto.ConfigQueryDTO;
import com.njydsz.pmis.config.entity.ConfigDO;
import com.njydsz.pmis.config.service.ConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统配置接口
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "系统-配置中心")
@RestController
@RequestMapping("/api/v1/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    @Operation(summary = "配置分页")
    @PrePermission("sys:config:list")
    @GetMapping
    public Result<Page<ConfigDO>> page(ConfigQueryDTO query) {
        return Result.ok(configService.page(query));
    }

    @Operation(summary = "按 group+key 查配置")
    @GetMapping("/by-key")
    public Result<ConfigDO> getByKey(@RequestParam String group, @RequestParam String key) {
        return Result.ok(configService.getByKey(group, key));
    }

    @Operation(summary = "按 group 查全部配置（key-value 形式）")
    @GetMapping("/group/{group}")
    public Result<Map<String, String>> getGroup(@PathVariable String group) {
        return Result.ok(configService.getGroupAsMap(group));
    }

    @Operation(summary = "公开配置（前端可见）")
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
    public Result<Void> delete(@PathVariable Long id) {
        configService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "按分组批量删除")
    @PrePermission("sys:config:delete")
    @OperationLog(module = "系统配置", action = "按分组删除", bizType = "CONFIG")
    @DeleteMapping("/group/{group}")
    public Result<Integer> deleteByGroup(@PathVariable String group) {
        return Result.ok(configService.deleteByGroup(group));
    }

    @Operation(summary = "按分组批量启停")
    @PrePermission("sys:config:update")
    @OperationLog(module = "系统配置", action = "按分组启停", bizType = "CONFIG")
    @PutMapping("/group/{group}/status/{status}")
    public Result<Integer> updateStatusByGroup(@PathVariable String group, @PathVariable String status) {
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
