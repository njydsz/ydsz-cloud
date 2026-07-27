package com.njydsz.system.web.controller;

import java.util.List;

import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.SentinelRateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.service.ConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 系统配置 Controller。
 *
 * @author ydsz-team
 */
@Tag(name = "系统配置", description = "系统参数配置 CRUD + 按键查询 + 分组批量查询")
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService service;

    @Operation(summary = "分页查询配置列表（支持搜索过滤）")
    @GetMapping("/page")
    public PageResponse<List<ConfigVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "配置分组") @RequestParam(required = false) String configGroup,
            @Parameter(description = "配置键模糊搜索") @RequestParam(required = false) String configKey,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        IPage<ConfigVO> page = service.page(pageNum, pageSize, configGroup, configKey, status);
        return PageResponse.success(page.getTotal(), (long) pageNum, (long) pageSize, page.getRecords());
    }

    @Operation(summary = "按 ID 查询配置")
    @GetMapping("/{id}")
    public BaseResponse<ConfigVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @Operation(summary = "按配置键查询配置值")
    @GetMapping("/key/{configKey}")
    public BaseResponse<String> getByKey(@PathVariable String configKey) {
        return BaseResponse.success(service.getConfigValue(configKey));
    }

    @Operation(summary = "按配置分组批量查询启用的配置项")
    @GetMapping("/group/{configGroup}")
    public BaseResponse<List<ConfigVO>> getByGroup(@PathVariable String configGroup) {
        return BaseResponse.success(service.getConfigsByGroup(configGroup));
    }

    @Operation(summary = "查询所有公开配置")
    @GetMapping("/public")
    public BaseResponse<List<ConfigVO>> listPublic() {
        return BaseResponse.success(service.listPublicConfigs());
    }

    @Audit(module = "系统配置", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建配置: ' + #dto.configKey")
    @Operation(summary = "创建配置")
    @Idempotent(key = 'system:config:save', ttlSeconds = 5, message = "请勿重复提交")
    @SentinelRateLimit(resource = "system.config.save", threshold = 50)
    @Idempotent(key = "ydsz:system:ConfigController:save:lock", ttlSeconds = 5)
    @PostMapping
    public BaseResponse<String> save(@Valid @RequestBody ConfigDTO dto) {
        return BaseResponse.success(service.save(dto));
    }

    @Audit(module = "系统配置", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新配置: ' + #dto.configKey")
    @Operation(summary = "更新配置")
    @Idempotent(key = 'system:config:update', ttlSeconds = 5, message = "请勿重复提交")
    @SentinelRateLimit(resource = "system.config.update", threshold = 50)
    @Idempotent(key = "ydsz:system:ConfigController:update:lock", ttlSeconds = 5)
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody ConfigDTO dto) {
        return BaseResponse.success(service.updateById(dto));
    }

    @Audit(module = "系统配置", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除配置: ' + #id")
    @Operation(summary = "删除配置")
    @Idempotent(key = 'system:config:remove', ttlSeconds = 5, message = "请勿重复提交")
    @SentinelRateLimit(resource = "system.config.remove", threshold = 50)
    @Idempotent(key = "ydsz:system:ConfigController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
