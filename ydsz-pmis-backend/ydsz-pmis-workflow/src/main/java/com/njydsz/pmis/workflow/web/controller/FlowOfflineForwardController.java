package com.njydsz.pmis.workflow.web.controller.delegate;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.server.service.delegate.FlowOfflineAutoForwardService;
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
    @Idempotent(key = "flowOfflineForward:autoForward", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/auto")
    @Operation(summary = "按代理授权规则自动转发已有待办")
    public Result<Integer> autoForward(@RequestParam String authId) {
        return Result.ok(offlineAutoForwardService.autoForwardByAuth(authId));
    }

    /**
     * 手动触发离线转发。
     *
     * @param userId        离线用户 ID
     * @param delegateUserId 代理人 ID
     * @return 成功转发的任务数
     */
    @Idempotent(key = "flowOfflineForward:manualForward", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/manual")
    @Operation(summary = "手动触发离线转发")
    public Result<Integer> manualForward(
            @RequestParam String userId,
            @RequestParam String delegateUserId) {
        String operatorId = SecurityContext.getUserId();
        return Result.ok(offlineAutoForwardService.manualForward(userId, delegateUserId, operatorId));
    }
}
