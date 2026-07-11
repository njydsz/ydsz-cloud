package com.njydsz.pmis.system.controller.audit;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.system.entity.audit.SensitiveOperationDO;
import com.njydsz.pmis.system.mapper.audit.SensitiveOperationMapper;
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
 * 敏感操作审计查询 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "敏感操作审计", description = "敏感操作审计记录查询接口")
@RestController
@RequestMapping("/audit/sensitiveOp")
@RequiredArgsConstructor
@Validated
public class SensitiveOperationController {

    /** 敏感操作审计 Mapper */
    private final SensitiveOperationMapper mapper;

    /**
     * 分页查询敏感操作审计记录
     *
     * @param page   页码
     * @param size   每页大小
     * @param userId 用户 ID（可选）
     * @param opType 操作类型（可选）
     * @return 统一响应结果，包含分页数据
     */
    @Operation(summary = "分页查询")
    @PrePermission("audit:sensitive:view")
    @GetMapping("/page")
    public Result<PageResult<SensitiveOperationDO>> page(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = "用户ID") @RequestParam(required = false) String userId,
            @Parameter(description = "操作类型") @RequestParam(required = false) String opType) {
        Page<SensitiveOperationDO> p = new Page<>(page, size);
        LambdaQueryWrapper<SensitiveOperationDO> w = new LambdaQueryWrapper<>();
        if (userId != null) w.eq(SensitiveOperationDO::getUserId, userId);
        if (StringUtils.hasText(opType)) w.eq(SensitiveOperationDO::getBizType, opType);
        w.orderByDesc(SensitiveOperationDO::getVerifiedAt);
        return Result.ok(PageResult.ofPage(mapper.selectPage(p, w)));
    }

    @Operation(summary = "按用户查询敏感操作历史")
    @PrePermission("audit:sensitive:view")
    @GetMapping("/byUser")
    /**
     * 按用户查询敏感操作历史
     *
     * @param userId 用户 ID
     * @param limit  最大条数
     * @return 统一响应结果，包含敏感操作审计列表
     */
    public Result<List<SensitiveOperationDO>> byUser(
            @Parameter(description = "用户ID") @RequestParam String userId,
            @Parameter(description = "最大条数") @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return Result.ok(mapper.selectByUser(userId, Math.min(limit, 200)));
    }
}
