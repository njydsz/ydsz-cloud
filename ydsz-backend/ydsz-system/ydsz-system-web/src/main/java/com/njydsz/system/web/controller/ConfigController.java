package com.njydsz.system.web.controller;

import java.util.List;

import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.common.web.controller.BaseCrudController;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.entity.ConfigDO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.server.service.ConfigService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 系统配置 Controller。
 *
 * <p>继承 {@link BaseCrudController} 获得标准 CRUD 端点，
 * 额外提供按 key 查询、按 group 批量查询、公开配置查询等业务端点。
 *
 * @author ydsz-team
 */
@Tag(name = "系统配置", description = "系统参数配置 CRUD + 按键查询 + 分组批量查询")
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController extends BaseCrudController<ConfigDO, ConfigDTO, ConfigVO, ConfigPageQuery, String> {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    protected BaseCrudService<ConfigDO, ConfigDTO, ConfigVO, ConfigPageQuery, String> getService() {
        return configService;
    }

    // ============================== 覆写基类方法（添加审计 + 幂等 + 限流注解） ==============================

    @Override
    @Audit(module = "系统配置", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建配置: ' + #dto.configKey")
    @Operation(summary = "创建配置")
    @RateLimit(resource = "system.config.save", threshold = 50)
    @Idempotent(key = "ydsz:system:ConfigController:save:lock", ttlSeconds = 5)
    @PostMapping
    public BaseResponse<String> save(@Valid @RequestBody ConfigDTO dto) {
        return super.save(dto);
    }

    @Override
    @Audit(module = "系统配置", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新配置: ' + #dto.configKey")
    @Operation(summary = "更新配置")
    @RateLimit(resource = "system.config.update", threshold = 50)
    @Idempotent(key = "ydsz:system:ConfigController:update:lock", ttlSeconds = 5)
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody ConfigDTO dto) {
        return super.update(dto);
    }

    @Override
    @Audit(module = "系统配置", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除配置: ' + #id")
    @Operation(summary = "删除配置")
    @RateLimit(resource = "system.config.remove", threshold = 50)
    @Idempotent(key = "ydsz:system:ConfigController:remove:lock", ttlSeconds = 5)
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return super.remove(id);
    }

    // ============================== 业务扩展端点 ==============================

    @Operation(summary = "按配置键查询配置值")
    @GetMapping("/key/{configKey}")
    public BaseResponse<String> getByKey(@PathVariable String configKey) {
        return BaseResponse.success(configService.getConfigValue(configKey));
    }

    @Operation(summary = "按配置分组批量查询启用的配置项")
    @GetMapping("/group/{configGroup}")
    public BaseResponse<List<ConfigVO>> getByGroup(@PathVariable String configGroup) {
        return BaseResponse.success(configService.getConfigsByGroup(configGroup));
    }

    @Operation(summary = "查询所有公开配置")
    @GetMapping("/public")
    public BaseResponse<List<ConfigVO>> listPublic() {
        return BaseResponse.success(configService.listPublicConfigs());
    }
}