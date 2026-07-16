package com.njydsz.pmis.project.web.controller;

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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.lock.annotation.Idempotent;
import com.njydsz.pmis.project.domain.dto.OpportunityFollowDTO;
import com.njydsz.pmis.project.domain.entity.OpportunityFollowDO;
import com.njydsz.pmis.project.server.service.opportunity.OpportunityFollowService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 商机跟进 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "商机跟进")
@RestController
@RequestMapping("/opportunity/follow")
@RequiredArgsConstructor
@Validated
public class OpportunityFollowController {

    /** 商机跟进服务 */
    private final OpportunityFollowService service;

    /**
     * 记录一次商机跟进。
     *
     * @param dto 跟进记录参数
     * @return 跟进记录 ID
     */
    @Operation(summary = "记录跟进")
    @Idempotent(key = "opportunityFollow:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<String> record(@Valid @RequestBody OpportunityFollowDTO dto) {
        return BaseResponse.ok(service.record(dto));
    }

    /**
     * 分页查询商机跟进记录。
     *
     * @param page          页码（从 1 开始）
     * @param size          每页大小
     * @param opportunityId 商机 ID，可空
     * @return 分页结果
     */
    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public BaseResponse<Page<OpportunityFollowDO>> page(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) String opportunityId) {
        return BaseResponse.ok(service.page(page, size, opportunityId));
    }
}
