package com.njydsz.pmis.audit.controller;

import com.njydsz.pmis.audit.entity.DataExportAuditDO;
import com.njydsz.pmis.audit.mapper.DataExportAuditMapper;
import com.njydsz.pmis.common.api.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @Operation(summary = "按用户查询导出历史")
    @GetMapping("/by-user")
    public R<List<DataExportAuditDO>> byUser(@RequestParam Long userId,
                                             @RequestParam(defaultValue = "50") int limit) {
        return R.ok(mapper.selectByUser(userId, Math.min(limit, 200)));
    }
}
