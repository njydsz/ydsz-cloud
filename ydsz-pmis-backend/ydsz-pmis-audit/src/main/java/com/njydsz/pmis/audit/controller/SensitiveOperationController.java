package com.njydsz.pmis.audit.controller;

import com.njydsz.pmis.audit.entity.SensitiveOperationDO;
import com.njydsz.pmis.audit.mapper.SensitiveOperationMapper;
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
 * 敏感操作审计查询
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "敏感操作审计")
@RestController
@RequestMapping("/api/v1/audit/sensitive-op")
@RequiredArgsConstructor
public class SensitiveOperationController {

    private final SensitiveOperationMapper mapper;

    @Operation(summary = "按用户查询敏感操作历史")
    @GetMapping("/by-user")
    public R<List<SensitiveOperationDO>> byUser(@RequestParam Long userId,
                                                @RequestParam(defaultValue = "50") int limit) {
        return R.ok(mapper.selectByUser(userId, Math.min(limit, 200)));
    }
}
