package com.njydsz.pmis.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.audit.entity.DataExportAuditDO;
import com.njydsz.pmis.audit.mapper.DataExportAuditMapper;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.R;
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

    private final DataExportAuditMapper mapper;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<PageResult<DataExportAuditDO>> page(
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
        return R.ok(PageResult.ofPage(mapper.selectPage(p, w)));
    }

    @Operation(summary = "按用户查询导出历史")
    @GetMapping("/by-user")
    public R<List<DataExportAuditDO>> byUser(@RequestParam Long userId,
                                             @RequestParam(defaultValue = "50") int limit) {
        return R.ok(mapper.selectByUser(userId, Math.min(limit, 200)));
    }
}
