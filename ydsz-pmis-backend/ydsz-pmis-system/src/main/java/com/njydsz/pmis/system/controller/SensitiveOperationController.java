package com.njydsz.pmis.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.entity.SensitiveOperationDO;
import com.njydsz.pmis.system.mapper.SensitiveOperationMapper;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
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
 * 敏感操作审计查询 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "敏感操作审计")
@RestController
@RequestMapping("/api/v1/audit/sensitive-op")
@RequiredArgsConstructor
@Validated
public class SensitiveOperationController {

    private final SensitiveOperationMapper mapper;

    @Operation(summary = "分页查询")
    @PrePermission("audit:sensitive:view")
    @GetMapping("/page")
    public Result<PageResult<SensitiveOperationDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Max(100) int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String opType) {
        Page<SensitiveOperationDO> p = new Page<>(page, size);
        LambdaQueryWrapper<SensitiveOperationDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(SensitiveOperationDO::getUserId, userId);
        if (StringUtils.hasText(opType)) w.eq(SensitiveOperationDO::getBizType, opType);
        w.orderByDesc(SensitiveOperationDO::getVerifiedAt);
        return Result.ok(PageResult.ofPage(mapper.selectPage(p, w)));
    }

    @Operation(summary = "按用户查询敏感操作历史")
    @PrePermission("audit:sensitive:view")
    @GetMapping("/by-user")
    public Result<List<SensitiveOperationDO>> byUser(@RequestParam Long userId,
                                                @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(mapper.selectByUser(userId, Math.min(limit, 200)));
    }
}
