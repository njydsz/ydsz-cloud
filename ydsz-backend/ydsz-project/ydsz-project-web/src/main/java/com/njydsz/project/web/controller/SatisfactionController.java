package com.njydsz.project.web.controller.aftersales;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.project.domain.dto.SatisfactionCreateDTO;
import com.njydsz.project.domain.entity.SatisfactionDO;
import com.njydsz.project.server.service.SatisfactionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 服务满意度评价 Controller
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "服务满意度评价")
@RestController
@RequestMapping("/api/project/afterSales/satisfaction")
@RequiredArgsConstructor
@Validated
public class SatisfactionController {

    /** 满意度调查服务 */
    private final SatisfactionService service;

    @Operation(summary = "提交评价")
    @AuthApiPermission(apiCodes = "aftersales:satisfaction:submit")
    @Idempotent(key = "satisfaction:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> submit(@Valid @RequestBody SatisfactionCreateDTO dto) {
        return BaseResponse.success(service.submit(dto));
    }

    @Operation(summary = "标记跟进")
    @AuthApiPermission(apiCodes = "aftersales:satisfaction:followUp")
    @Idempotent(key = "satisfaction:markFollowUp", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/followUp")
    public BaseResponse<Void> markFollowUp(@RequestParam String id, @RequestParam(required = false) String note) {
        service.markFollowUp(id, note);
        return BaseResponse.success();
    }

    @Operation(summary = "关闭跟进")
    @AuthApiPermission(apiCodes = "aftersales:satisfaction:followUp")
    @Idempotent(key = "satisfaction:closeFollowUp", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/followUp/close")
    public BaseResponse<Void> closeFollowUp(@RequestParam String id) {
        service.closeFollowUp(id);
        return BaseResponse.success();
    }

    @Operation(summary = "整体满意度均值")
    @AuthApiPermission(apiCodes = "aftersales:satisfaction:list")
    @GetMapping("/overall")
    public BaseResponse<Map<String, Object>> overall() {
        return BaseResponse.success(service.overall());
    }

    @Operation(summary = "等级分布")
    @AuthApiPermission(apiCodes = "aftersales:satisfaction:list")
    @GetMapping("/levelDistribution")
    public BaseResponse<List<Map<String, Object>>> levelDistribution() {
        return BaseResponse.success(service.levelDistribution());
    }

    @Operation(summary = "分页")
    @AuthApiPermission(apiCodes = "aftersales:satisfaction:list")
    @GetMapping("/page")
    public BaseResponse<PageResponse<SatisfactionDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String initiationId,
            @RequestParam(required = false) String keyword) {
        return BaseResponse.success(PageResponse.ofPage(service.page(page, size, level, initiationId, keyword)));
    }
}
