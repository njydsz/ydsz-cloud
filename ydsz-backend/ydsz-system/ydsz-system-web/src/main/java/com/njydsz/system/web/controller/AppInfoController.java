package com.njydsz.system.web.controller;

import java.util.List;

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
import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.vo.AppInfoVO;
import com.njydsz.system.server.service.AppInfoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 应用注册 Controller。
 *
 * @author ydsz-team
 */
@Tag(name = "应用注册", description = "OAuth2 应用注册 CRUD")
@RestController
@RequestMapping("/api/v1/app")
@RequiredArgsConstructor
public class AppInfoController {

    private final AppInfoService service;

    @Operation(summary = "分页查询应用列表（支持搜索过滤）")
    @GetMapping("/page")
    public PageResponse<List<AppInfoVO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") int pageSize,
            @Parameter(description = "应用名称模糊搜索") @RequestParam(required = false) String appName,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        IPage<AppInfoVO> page = service.page(pageNum, pageSize, appName, status);
        return PageResponse.success(page.getTotal(), (long) pageNum, (long) pageSize, page.getRecords());
    }

    @Operation(summary = "按 ID 查询应用")
    @GetMapping("/{id}")
    public BaseResponse<AppInfoVO> getById(@PathVariable String id) {
        return BaseResponse.success(service.getById(id));
    }

    @Audit(module = "应用注册", type = AuditType.OPERATION, action = AuditAction.CREATE,
            content = "'创建应用: ' + #dto.appCode", excludeParams = {"appSecret"})
    @Operation(summary = "创建应用")
    @SentinelRateLimit(resource = "system.appinfo.save", threshold = 50)
    @SentinelRateLimit(resource = "system.appinfo.save", threshold = 50)
    @PostMapping
    public BaseResponse<String> save(@Valid @RequestBody AppInfoDTO dto) {
        return BaseResponse.success(service.save(dto));
    }

    @Audit(module = "应用注册", type = AuditType.OPERATION, action = AuditAction.UPDATE,
            content = "'更新应用: ' + #dto.appCode", excludeParams = {"appSecret"})
    @Operation(summary = "更新应用")
    @SentinelRateLimit(resource = "system.appinfo.update", threshold = 50)
    @SentinelRateLimit(resource = "system.appinfo.update", threshold = 50)
    @PutMapping
    public BaseResponse<Boolean> update(@Valid @RequestBody AppInfoDTO dto) {
        return BaseResponse.success(service.updateById(dto));
    }

    @Audit(module = "应用注册", type = AuditType.OPERATION, action = AuditAction.DELETE,
            content = "'删除应用: ' + #id")
    @Operation(summary = "删除应用")
    @SentinelRateLimit(resource = "system.appinfo.remove", threshold = 50)
    @SentinelRateLimit(resource = "system.appinfo.remove", threshold = 50)
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable String id) {
        return BaseResponse.success(service.removeById(id));
    }
}
