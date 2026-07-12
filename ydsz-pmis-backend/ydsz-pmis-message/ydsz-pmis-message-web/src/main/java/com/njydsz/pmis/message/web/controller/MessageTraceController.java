package com.njydsz.pmis.message.web.controller.core;

import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.domain.entity.config.MsgTraceDO;
import com.njydsz.pmis.message.server.service.core.MessageTraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * P0-2: 消息端到端追踪 Controller。
 *
 * <p>提供按 msgId / traceId / bizType+bizId 查询消息完整轨迹的接口。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Tag(name = "消息追踪", description = "消息端到端全链路追踪")
@RestController
@RequestMapping("/message/trace")
@RequiredArgsConstructor
public class MessageTraceController {

    /** 消息追踪服务 */
    private final MessageTraceService messageTraceService;

    /**
     * 按消息 ID 查询完整轨迹。
     *
     * @param msgId 消息 ID
     * @return 统一响应结果，包含轨迹列表
     */
    @Operation(summary = "按消息 ID 查询轨迹")
    @PrePermission(PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/msg/{msgId}")
    public BaseResponse<List<MsgTraceDO>> getByMsgId(@PathVariable String msgId) {
        return BaseResponse.ok(messageTraceService.getTraceByMsgId(msgId));
    }

    /**
     * 按链路追踪 ID 查询完整轨迹。
     *
     * @param traceId 链路追踪 ID
     * @return 统一响应结果，包含轨迹列表
     */
    @Operation(summary = "按链路追踪 ID 查询轨迹")
    @PrePermission(PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/trace/{traceId}")
    public BaseResponse<List<MsgTraceDO>> getByTraceId(@PathVariable String traceId) {
        return BaseResponse.ok(messageTraceService.getTraceByTraceId(traceId));
    }

    /**
     * 按业务类型和单据 ID 查询轨迹。
     *
     * @param bizType 业务类型
     * @param bizId   单据 ID
     * @return 统一响应结果，包含轨迹列表
     */
    @Operation(summary = "按业务类型+单据 ID 查询轨迹")
    @PrePermission(PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/biz")
    public BaseResponse<List<MsgTraceDO>> getByBiz(@RequestParam String bizType,
                                              @RequestParam String bizId) {
        return BaseResponse.ok(messageTraceService.getTraceByBiz(bizType, bizId));
    }
}
