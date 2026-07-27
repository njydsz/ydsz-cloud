package com.njydsz.message.web.controller.core;

import java.util.Map;

import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.core.MessageFeedbackDTO;
import com.njydsz.message.domain.entity.config.MsgFeedback;
import com.njydsz.message.domain.vo.MsgFeedbackVO;
import com.njydsz.message.server.service.core.MessageFeedbackService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * P1-4: 消息质量反馈 Controller。
 *
 * <p>提供用户对消息质量的评分和反馈接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Tag(name = "消息反馈", description = "消息质量评分与用户反馈")
@RestController
@RequestMapping("/message/feedback")
@RequiredArgsConstructor
public class MessageFeedbackController {

    /** 消息质量反馈服务 */
    private final MessageFeedbackService messageFeedbackService;

    /**
     * 提交消息质量反馈。
     *
     * @param dto 反馈请求体
     * @return 统一响应结果，包含反馈记录 ID
     */
    @Operation(summary = "提交消息反馈")
    @Idempotent(key = "ydsz:message:MessageFeedbackController:submitFeedback:lock", ttlSeconds = 5)
    @Audit(module = "消息反馈", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'submitFeedback'")
    @RateLimit(resource = "message.messagefeedback.submitFeedback", threshold = 50)
    @PostMapping
    public BaseResponse<String> submitFeedback(@Valid @RequestBody MessageFeedbackDTO dto) {
        return BaseResponse.success(messageFeedbackService.submitFeedback(dto));
    }

    /**
     * 查询用户和通道的平均评分。
     *
     * @param userId  用户 ID
     * @param channel 通道（可选）
     * @return 统一响应结果，包含用户评分与通道评分
     */
    @Operation(summary = "查询用户平均评分")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/rating")
    public BaseResponse<Map<String, Double>> getAverageRating(@RequestParam String userId,
                                                         @RequestParam(required = false) String channel) {
        double userRating = messageFeedbackService.getAverageRating(userId);
        double channelRating = channel != null
                ? messageFeedbackService.getAverageRatingByChannel(channel) : 0;
        return BaseResponse.success(Map.of(
                "userRating", userRating,
                "channelRating", channelRating));
    }

    /**
     * 分页查询反馈记录。
     *
     * @param page    页码（默认 1）
     * @param size    每页条数（默认 20）
     * @param channel 通道过滤（可选）
     * @param userId  用户 ID 过滤（可选）
     * @return 统一响应结果，包含反馈分页数据
     */
    @Operation(summary = "分页查询反馈记录")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/page")
    public BaseResponse<Page<MsgFeedbackVO>> pageFeedback(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String channel,
                                                      @RequestParam(required = false) String userId) {
        Page<MsgFeedback> result = messageFeedbackService.pageFeedback(page, size, channel, userId);
        Page<MsgFeedbackVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(MessageConverter.INSTANT.feedbackListToVO(result.getRecords()));
        return BaseResponse.success(voPage);
    }

    /**
     * 检查用户是否需要降频推送。
     *
     * @param userId 用户 ID
     * @return 统一响应结果，包含 shouldReduce 标记
     */
    @Operation(summary = "检查用户是否需要降频")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/shouldReduceFreq")
    public BaseResponse<Map<String, Boolean>> shouldReduceFrequency(@RequestParam String userId) {
        return BaseResponse.success(Map.of("shouldReduce", messageFeedbackService.shouldReduceFrequency(userId)));
    }
}
