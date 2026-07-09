package com.njydsz.pmis.project.controller;

import com.njydsz.pmis.common.annotation.Idempotent;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.project.dto.SatisfactionCreateDTO;
import com.njydsz.pmis.project.entity.SatisfactionDO;
import com.njydsz.pmis.project.service.SatisfactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 服务满意度评价 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "服务满意度评价")
@RestController
@RequestMapping("/after-sales/satisfaction")
@RequiredArgsConstructor
@Validated
public class SatisfactionController {

    /** 满意度调查服务 */
    private final SatisfactionService service;

    @Operation(summary = "提交评价")
    @PrePermission("aftersales:satisfaction:submit")
    @Idempotent(key = "satisfaction:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> submit(@Valid @RequestBody SatisfactionCreateDTO dto) {
        return Result.ok(service.submit(dto));
    }

    @Operation(summary = "标记跟进")
    @PrePermission("aftersales:satisfaction:follow-up")
    @Idempotent(key = "satisfaction:mark-follow-up", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/follow-up")
    public Result<Void> markFollowUp(@RequestParam String id, @RequestParam(required = false) String note) {
        service.markFollowUp(id, note);
        return Result.ok();
    }

    @Operation(summary = "关闭跟进")
    @PrePermission("aftersales:satisfaction:follow-up")
    @Idempotent(key = "satisfaction:close-follow-up", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/follow-up/close")
    public Result<Void> closeFollowUp(@RequestParam String id) {
        service.closeFollowUp(id);
        return Result.ok();
    }

    @Operation(summary = "整体满意度均值")
    @PrePermission("aftersales:satisfaction:list")
    @GetMapping("/overall")
    public Result<Map<String, Object>> overall() {
        return Result.ok(service.overall());
    }

    @Operation(summary = "等级分布")
    @PrePermission("aftersales:satisfaction:list")
    @GetMapping("/level-distribution")
    public Result<List<Map<String, Object>>> levelDistribution() {
        return Result.ok(service.levelDistribution());
    }

    @Operation(summary = "分页")
    @PrePermission("aftersales:satisfaction:list")
    @GetMapping("/page")
    public Result<PageResult<SatisfactionDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(PageResult.ofPage(service.page(page, size, level, initiationId, keyword)));
    }
}
