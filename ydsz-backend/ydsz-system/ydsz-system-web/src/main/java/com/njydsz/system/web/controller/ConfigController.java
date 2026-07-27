package com.njydsz.system.web.controller;

import java.util.List;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.service.ConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统配置 Controller。
 *
 * <p>提供配置 CRUD、按 key 查询、按 group 批量查询、公开配置查询等业务端点。
 *
 * @author ydsz-team
 */
@Tag(name = "系统配置", description = "系统参数配置 CRUD + 按键查询 + 分组批量查询")
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    // ============================== CRUD 端点 ==============================

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public PageResponse<List<ConfigVO>> page(ConfigPageQuery query) {
        PageResult<ConfigVO> result = configService.page(query);
        return PageResponse.success(
                result.getTotal(),
                (long) result.getPageNum(),
                (long) result.getPageSize(),
                result.getRecords());
    }

    @Operation(summary = "按 ID 查询")
    @GetMapping("/{id}")
    public BaseResponse<ConfigVO> getById(@PathVariable String id) {
        return BaseResponse.success(configService.getById(id));
    }

    @Audit(module = "系统配置", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建配置: ' + #dto.configKey")
    @Operation(summary = "创建配置")
    @RateLimit(resource = "system.config.save", threshold = 50)
    @Idempotent(key = "ydsz:system:ConfigController:save:lock", ttlSeconds = 5)
    @PostMapping
    public BaseResponse<String> save(@Valid @RequestBody ConfigDTO dto) {
        return BaseResponse.success(configService.save(dto));
    }

    @Audit(module = "系统配置", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新配置: ' + #dto.configKey")
    @Operation(summary = "更新配置")
    @RateLimit(resource = "system.config.update", threshold = 50)
    @Idempotent(key = "ydsz:system:ConfigController:update:lock", ttlSeconds = 5)
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody ConfigDTO dto) {
        return BaseResponse.success(configService.updateById(dto));
    }

    @Audit(module = "系统配置", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除配置: ' + #id")
    @Operation(summary = "删除配置")
    @RateLimit(resource = "system.config.remove", threshold = 50)
    @Idempotent(key = "ydsz:system:ConfigController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(configService.removeById(id));
    }

    // ============================== 业务扩展端点 ==============================

    @Operation(summary = "按配置键查询配置值")
    @GetMapping("/key/{configKey}")
    public BaseResponse<String> getByKey(@PathVariable String configKey) {
        return BaseResponse.success(configService.getConfigValue(configKey));
    }

    @Operation(summary = "按配置分组批量查询")
    @GetMapping("/group/{configGroup}")
    public BaseResponse<List<ConfigVO>> getConfigsByGroup(@PathVariable String configGroup) {
        return BaseResponse.success(configService.getConfigsByGroup(configGroup));
    }

    @Operation(summary = "查询所有公开配置")
    @GetMapping("/public")
    public BaseResponse<List<ConfigVO>> listPublicConfigs() {
        return BaseResponse.success(configService.listPublicConfigs());
    }
}