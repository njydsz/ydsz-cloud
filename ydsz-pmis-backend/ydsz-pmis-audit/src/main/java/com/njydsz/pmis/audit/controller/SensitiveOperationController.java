package com.njydsz.pmis.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.audit.entity.SensitiveOperationDO;
import com.njydsz.pmis.audit.mapper.SensitiveOperationMapper;
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

@Tag(name = "敏感操作审计")
@RestController
@RequestMapping("/api/v1/audit/sensitive-op")
@RequiredArgsConstructor
public class SensitiveOperationController {

    private final SensitiveOperationMapper mapper;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<PageResult<SensitiveOperationDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String opType) {
        Page<SensitiveOperationDO> p = new Page<>(page, size);
        LambdaQueryWrapper<SensitiveOperationDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(SensitiveOperationDO::getUserId, userId);
        if (StringUtils.hasText(opType)) w.eq(SensitiveOperationDO::getBizType, opType);
        w.orderByDesc(SensitiveOperationDO::getVerifiedAt);
        return R.ok(PageResult.ofPage(mapper.selectPage(p, w)));
    }

    @Operation(summary = "按用户查询敏感操作历史")
    @GetMapping("/by-user")
    public R<List<SensitiveOperationDO>> byUser(@RequestParam Long userId,
                                                @RequestParam(defaultValue = "50") int limit) {
        return R.ok(mapper.selectByUser(userId, Math.min(limit, 200)));
    }
}
