package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.entity.FlowNotifyPreferenceDO;
import com.njydsz.pmis.workflow.service.FlowNotifyPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P1-7: 工作流通知偏好 Controller
 *
 * <p>用户查询/更新自己的免打扰时段与通知聚合偏好。
 * 当前登录用户从 {@link SecurityContext} 获取，租户 ID 同理。
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@RestController
@RequestMapping("/workflow/notify-preference")
@RequiredArgsConstructor
public class FlowNotifyPreferenceController {

    private final FlowNotifyPreferenceService preferenceService;

    /**
     * 查询当前用户的通知偏好
     */
    @GetMapping
    public Result<FlowNotifyPreferenceDO> get() {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        String userId = SecurityContext.getUserId();
        return Result.ok(preferenceService.getOrCreate(tenantId, userId));
    }

    /**
     * 保存（新增或更新）当前用户的通知偏好
     */
    @Idempotent(key = "flow-notify-preference:save", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    public Result<String> save(@RequestBody FlowNotifyPreferenceDO preference) {
        String tenantId = SecurityContext.getTenantIdOrDefault("1");
        String userId = SecurityContext.getUserId();
        return Result.ok(preferenceService.save(tenantId, userId, preference));
    }
}
