package com.njydsz.pmis.message.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.annotation.PrePermission;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.permission.PermissionCodes;
import com.njydsz.pmis.message.dto.MessageFeedbackDTO;
import com.njydsz.pmis.message.entity.MsgFeedbackDO;
import com.njydsz.pmis.message.service.MessageFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * P1-4: 消息质量反馈 Controller。
 *
 * <p>提供用户对消息质量的评分和反馈接口。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Tag(name = "消息反馈", description = "消息质量评分与用户反馈")
@RestController
@RequestMapping("/message/feedback")
@RequiredArgsConstructor
public class MessageFeedbackController {

    private final MessageFeedbackService messageFeedbackService;

    @Operation(summary = "提交消息反馈")
    @PostMapping
    public Result<String> submitFeedback(@RequestBody MessageFeedbackDTO dto) {
        return Result.ok(messageFeedbackService.submitFeedback(dto));
    }

    @Operation(summary = "查询用户平均评分")
    @PrePermission(PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/rating")
    public Result<Map<String, Double>> getAverageRating(@RequestParam String userId,
                                                         @RequestParam(required = false) String channel) {
        double userRating = messageFeedbackService.getAverageRating(userId);
        double channelRating = channel != null
                ? messageFeedbackService.getAverageRatingByChannel(channel) : 0;
        return Result.ok(Map.of(
                "userRating", userRating,
                "channelRating", channelRating));
    }

    @Operation(summary = "分页查询反馈记录")
    @PrePermission(PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/page")
    public Result<Page<MsgFeedbackDO>> pageFeedback(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String channel,
                                                      @RequestParam(required = false) String userId) {
        return Result.ok(messageFeedbackService.pageFeedback(page, size, channel, userId));
    }

    @Operation(summary = "检查用户是否需要降频")
    @PrePermission(PermissionCodes.MESSAGE_LOG_VIEW)
    @GetMapping("/should-reduce-freq")
    public Result<Map<String, Boolean>> shouldReduceFrequency(@RequestParam String userId) {
        return Result.ok(Map.of("shouldReduce", messageFeedbackService.shouldReduceFrequency(userId)));
    }
}
