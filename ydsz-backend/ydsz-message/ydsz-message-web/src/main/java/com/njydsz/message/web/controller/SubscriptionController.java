package com.njydsz.message.web.controller.config;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.dto.config.SubscriptionUpsertDTO;
import com.njydsz.message.domain.entity.config.MsgSubscriptionDO;
import com.njydsz.message.server.service.config.SubscriptionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 订阅关系 Controller。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "消息订阅", description = "用户主题订阅关系管理")
@RestController
@RequestMapping("/message/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    /** 订阅关系服务 */
    private final SubscriptionService subscriptionService;

    /**
     * 新增或更新用户订阅关系。
     *
     * @param dto 订阅保存请求体
     * @return 统一响应结果，包含订阅记录
     */
    @Operation(summary = "新增/更新订阅")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_SUBSCRIPTION_UPDATE)
    @Idempotent(key = "subscription:upsert", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping
    public BaseResponse<MsgSubscriptionDO> upsert(@Valid @RequestBody SubscriptionUpsertDTO dto) {
        return BaseResponse.success(subscriptionService.upsert(dto));
    }

    /**
     * 查询用户全部订阅关系。
     *
     * @param userId 用户 ID
     * @return 统一响应结果，包含订阅列表
     */
    @Operation(summary = "查询用户所有订阅")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_SUBSCRIPTION_LIST)
    @GetMapping("/user/{userId}")
    public BaseResponse<List<MsgSubscriptionDO>> listByUser(@PathVariable String userId) {
        return BaseResponse.success(subscriptionService.listByUser(userId));
    }

    /**
     * 按主题和通道查询订阅列表。
     *
     * @param topicCode 主题编码
     * @param channel   通道（SMS/EMAIL/PUSH 等）
     * @return 统一响应结果，包含订阅列表
     */
    @Operation(summary = "按主题+通道查询订阅")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_SUBSCRIPTION_LIST)
    @GetMapping("/topic/{topicCode}/{channel}")
    public BaseResponse<List<MsgSubscriptionDO>> listByTopic(@PathVariable String topicCode,
                                                       @PathVariable String channel) {
        return BaseResponse.success(subscriptionService.listByTopic(topicCode, channel));
    }

    /**
     * 退订指定主题和通道。
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   通道
     * @return 统一响应结果
     */
    @Operation(summary = "退订")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_SUBSCRIPTION_DELETE)
    @Idempotent(key = "subscription:unsubscribe", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/unsubscribe")
    public BaseResponse<Void> unsubscribe(@RequestParam String userId,
                                    @RequestParam String topicCode,
                                    @RequestParam String channel) {
        subscriptionService.unsubscribe(userId, topicCode, channel);
        return BaseResponse.success();
    }
}
