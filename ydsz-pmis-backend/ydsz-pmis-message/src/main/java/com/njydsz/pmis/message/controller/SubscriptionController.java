package com.njydsz.pmis.message.controller;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.message.dto.SubscriptionUpsertDTO;
import com.njydsz.pmis.message.entity.MsgSubscriptionDO;
import com.njydsz.pmis.message.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订阅关系 Controller。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Tag(name = "消息订阅", description = "用户主题订阅关系管理")
@RestController
@RequestMapping("/message/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "新增/更新订阅")
    @PostMapping
    public Result<MsgSubscriptionDO> upsert(@RequestBody SubscriptionUpsertDTO dto) {
        // TODO 权限码
        return Result.ok(subscriptionService.upsert(dto));
    }

    @Operation(summary = "查询用户所有订阅")
    @GetMapping("/user/{userId}")
    public Result<List<MsgSubscriptionDO>> listByUser(@PathVariable String userId) {
        // TODO 权限码
        return Result.ok(subscriptionService.listByUser(userId));
    }

    @Operation(summary = "按主题+通道查询订阅")
    @GetMapping("/topic/{topicCode}/{channel}")
    public Result<List<MsgSubscriptionDO>> listByTopic(@PathVariable String topicCode,
                                                       @PathVariable String channel) {
        // TODO 权限码
        return Result.ok(subscriptionService.listByTopic(topicCode, channel));
    }

    @Operation(summary = "退订")
    @PostMapping("/unsubscribe")
    public Result<Void> unsubscribe(@RequestParam String userId,
                                    @RequestParam String topicCode,
                                    @RequestParam String channel) {
        // TODO 权限码
        subscriptionService.unsubscribe(userId, topicCode, channel);
        return Result.ok();
    }
}
