package com.njydsz.workflow.web.controller.delegate;

import org.springframework.web.bind.annotation.*;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.common.auth.context.AuthContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.workflow.server.service.FlowOfflineAutoForwardService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 离线代理自动转发 Controller（P2-5）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/workflow/offlineForward")
@RequiredArgsConstructor
@Tag(name = "离线代理自动转发", description = "离线用户的待办自动转发给代理人")
public class FlowOfflineForwardController {

    /** 离线代理自动转发服务，负责离线用户待办的自动/手动转发 */
    private final FlowOfflineAutoForwardService offlineAutoForwardService;

    /**
     * 按代理授权规则自动转发已有待办。
     *
     * @param authId 代理授权记录 ID
     * @return 成功转发的任务数
     */
    @Idempotent(key = "ydsz:workflow:FlowOfflineForwardController:autoForward:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowofflineforward.autoForward", threshold = 50)
    @PostMapping("/auto")
    @Operation(summary = "按代理授权规则自动转发已有待办")
    public BaseResponse<Integer> autoForward(@RequestParam String authId) {
        return BaseResponse.success(offlineAutoForwardService.autoForwardByAuth(authId));
    }

    /**
     * 手动触发离线转发。
     *
     * @param userId        离线用户 ID
     * @param delegateUserId 代理人 ID
     * @return 成功转发的任务数
     */
    @Idempotent(key = "ydsz:workflow:FlowOfflineForwardController:manualForward:lock", ttlSeconds = 5)
    @RateLimit(resource = "workflow.flowofflineforward.manualForward", threshold = 50)
    @PostMapping("/manual")
    @Operation(summary = "手动触发离线转发")
    public BaseResponse<Integer> manualForward(
            @RequestParam String userId,
            @RequestParam String delegateUserId) {
        String operatorId = AuthContext.getUserId();
        return BaseResponse.success(offlineAutoForwardService.manualForward(userId, delegateUserId, operatorId));
    }
}
