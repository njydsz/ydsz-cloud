package com.njydsz.pmis.workflow.controller;

import com.njydsz.pmis.common.annotation.Idempotent;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.security.SecurityContext;
import com.njydsz.pmis.workflow.entity.FlowWebhookSubscriptionDO;
import com.njydsz.pmis.workflow.service.FlowWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * P1-6: 工作流 Webhook 事件订阅 Controller
 *
 * @author ydsz-pmis-team
 * @since 1.6.0
 */
@RestController
@RequestMapping("/workflow/webhook")
@RequiredArgsConstructor
public class FlowWebhookController {

    private final FlowWebhookService webhookService;

    @Idempotent(key = "flow-webhook:create", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public Result<String> create(@RequestBody FlowWebhookSubscriptionDO subscription) {
        if (subscription.getTenantId() == null) {
            subscription.setTenantId(SecurityContext.getTenantIdOrDefault("1"));
        }
        return Result.ok(webhookService.create(subscription));
    }

    @Idempotent(key = "flow-webhook:update", ttlSeconds = 5, message = "请勿重复提交")
    @PutMapping
    public Result<Void> update(@RequestBody FlowWebhookSubscriptionDO subscription) {
        webhookService.update(subscription);
        return Result.ok();
    }

    @Idempotent(key = "flow-webhook:delete", ttlSeconds = 5, message = "请勿重复提交")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        webhookService.delete(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<FlowWebhookSubscriptionDO> getById(@PathVariable String id) {
        return Result.ok(webhookService.getById(id));
    }

    @GetMapping("/list")
    public Result<List<FlowWebhookSubscriptionDO>> listAll() {
        return Result.ok(webhookService.listAll());
    }
}
