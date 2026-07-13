package com.njydsz.pmis.nextwiki.web.controller;

import org.springframework.web.bind.annotation.*;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.nextwiki.api.dto.NextwikiDTOs;
import com.njydsz.pmis.nextwiki.domain.entity.StorageQuota;
import com.njydsz.pmis.nextwiki.domain.service.QuotaDomainService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 存储配额 REST API
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@Slf4j
@RestController
@RequestMapping("/nextwiki/quota")
@RequiredArgsConstructor
@Tag(name = "存储配额", description = "配额查询、设置、校�?)
public class QuotaController {

    private final QuotaDomainService quotaDomainService;

    @GetMapping("/info")
    @Operation(summary = "查询配额使用情况")
    public BaseResponse<StorageQuota> getQuota(
            @RequestParam(defaultValue = "user") String scopeType,
            @RequestParam String scopeId) {
        return BaseResponse.ok(quotaDomainService.getQuotaInfo(scopeType, scopeId));
    }

    @PostMapping("/set")
    @Operation(summary = "设置配额（管理员�?)
    public BaseResponse<StorageQuota> setQuota(
            @RequestBody NextwikiDTOs.SetQuotaRequest request,
            @RequestHeader("X-User-Id") String userId) {
        StorageQuota quota = quotaDomainService.setQuota(
                request.getScopeType(),
                request.getScopeId(),
                request.getQuotaLimit(),
                request.getFileCountLimit(),
                userId);
        return BaseResponse.ok(quota);
    }
}
