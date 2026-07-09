package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.service.FlowOfflineAutoForwardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 离线代理自动转发 Controller（P2-5）。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/offline-forward")
@RequiredArgsConstructor
@Tag(name = "离线代理自动转发", description = "离线用户的待办自动转发给代理人")
public class FlowOfflineForwardController {

    private final FlowOfflineAutoForwardService offlineAutoForwardService;

    @Idempotent(key = "flow-offline-forward:auto-forward", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/auto")
    @Operation(summary = "按代理授权规则自动转发已有待办")
    public Result<Integer> autoForward(@RequestParam String authId) {
        return Result.ok(offlineAutoForwardService.autoForwardByAuth(authId));
    }

    @Idempotent(key = "flow-offline-forward:manual-forward", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/manual")
    @Operation(summary = "手动触发离线转发")
    public Result<Integer> manualForward(
            @RequestParam String userId,
            @RequestParam String delegateUserId) {
        String operatorId = SecurityContext.getUserId();
        return Result.ok(offlineAutoForwardService.manualForward(userId, delegateUserId, operatorId));
    }
}
