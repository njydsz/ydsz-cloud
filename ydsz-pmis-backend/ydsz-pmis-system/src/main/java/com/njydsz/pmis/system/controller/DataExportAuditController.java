package com.njydsz.pmis.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.audit.entity.DataExportAuditDO;
import com.njydsz.pmis.audit.mapper.DataExportAuditMapper;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据导出审计查询
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "数据导出审计")
@RestController
@RequestMapping("/api/v1/audit/export")
@RequiredArgsConstructor
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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String exportModule,
            @RequestParam(required = false) String exportAction) {
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
    public Result<List<DataExportAuditDO>> byUser(@RequestParam Long userId,
                                             @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(mapper.selectByUser(userId, Math.min(limit, 200)));
    }
}
