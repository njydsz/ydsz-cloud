package com.njydsz.pmis.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.entity.audit.DataExportAuditDO;
import com.njydsz.pmis.system.mapper.audit.DataExportAuditMapper;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * 数据导出审计查询
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "数据导出审计", description = "数据导出审计记录查询接口")
@RestController
@RequestMapping("/audit/export")
@RequiredArgsConstructor
@Validated
public class DataExportAuditController {

    /** 数据导出审计 Mapper */
    private final DataExportAuditMapper mapper;

    /**
     * 分页查询数据导出审计记录
     *
     * @param page         页码
     * @param size         每页大小
     * @param userId       用户 ID（可选）
     * @param exportModule 导出模块（可选）
     * @param exportAction 导出动作（可选，模糊匹配）
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "分页查询")
    @PrePermission("audit:export:view")
    @GetMapping("/page")
    public Result<PageResult<DataExportAuditDO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "用户ID") @RequestParam(required = false) String userId,
            @Parameter(description = "导出模块") @RequestParam(required = false) String exportModule,
            @Parameter(description = "导出动作") @RequestParam(required = false) String exportAction) {
        Page<DataExportAuditDO> p = new Page<>(page, size);
        LambdaQueryWrapper<DataExportAuditDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(DataExportAuditDO::getUserId, userId);
        if (StringUtils.hasText(exportModule)) w.eq(DataExportAuditDO::getExportModule, exportModule);
        if (StringUtils.hasText(exportAction)) w.like(DataExportAuditDO::getExportAction, exportAction);
        w.orderByDesc(DataExportAuditDO::getExportedAt);
        return Result.ok(PageResult.ofPage(mapper.selectPage(p, w)));
    }

    /**
     * 按用户查询导出历史
     *
     * @param userId 用户 ID
     * @param limit  最大条数
     * @return 统一响应结果，包含导出审计列表
     */
    @Operation(summary = "按用户查询导出历史")
    @PrePermission("audit:export:view")
    @GetMapping("/by-user")
    public Result<List<DataExportAuditDO>> byUser(
            @Parameter(description = "用户ID") @RequestParam String userId,
            @Parameter(description = "最大条数") @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return Result.ok(mapper.selectByUser(userId, Math.min(limit, 200)));
    }
}
