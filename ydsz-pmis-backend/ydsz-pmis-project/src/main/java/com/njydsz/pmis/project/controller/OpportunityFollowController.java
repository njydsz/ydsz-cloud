package com.njydsz.pmis.project.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.project.dto.OpportunityFollowDTO;
import com.njydsz.pmis.project.entity.OpportunityFollowDO;
import com.njydsz.pmis.project.service.OpportunityFollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商机跟进 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "商机跟进")
@RestController
@RequestMapping("/api/v1/project/opportunity/follow")
@RequiredArgsConstructor
public class OpportunityFollowController {

    private final OpportunityFollowService service;

    @Operation(summary = "记录跟进")
    @PostMapping
    public R<Long> record(@Valid @RequestBody OpportunityFollowDTO dto) {
        return R.ok(service.record(dto));
    }

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public R<Page<OpportunityFollowDO>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long opportunityId) {
        return R.ok(service.page(page, size, opportunityId));
    }
}
