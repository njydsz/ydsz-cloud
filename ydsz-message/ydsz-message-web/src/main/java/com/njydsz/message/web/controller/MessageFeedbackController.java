package com.njydsz.message.web.controller.core;

import java.util.List;
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
import com.njydsz.common.core.response.PageResponse;
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
 * 消息质量反馈（Feedback）Controller。
 *
 * <p>提供<b>用户对消息质量的评分与反馈</b>能力，
 * 是 P1-4「消息质量闭环」的核心入口。通过收集用户对送达 / 内容 / 时机的主观评分，
 * 驱动发送策略的自动调优（降频、屏蔽、模板优化等）。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/feedback/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>提交反馈</b>：{@code POST /} — 用户对单条消息提交 1-5 星评分 + 文本意见</li>
 *   <li><b>用户平均评分</b>：{@code GET /rating} — 查询某用户 + 某通道的综合平均评分</li>
 *   <li><b>分页查询反馈</b>：{@code GET /page} — 管理后台查看全部反馈记录</li>
 *   <li><b>降频决策</b>：{@code GET /shouldReduceFreq} — 判定某用户是否需要降频推送</li>
 * </ul>
 *
 * <p><b>降频策略：</b>当用户近 N 条消息评分持续低于阈值（默认 2.0），自动标记为「应降频」，
 * 后续发送时由 {@code MessageService.send} 自动按降频策略（减少非必要通知 / 改用低频通道）发送。
 *
 * <p><b>典型场景：</b>
 * <ul>
 *   <li>用户对某条营销短信打 1 星 + 反馈「太多广告」→ 系统识别为低质 → 自动降低后续营销通知频次</li>
 *   <li>客服在管理后台查询「近 7 天评分低于 2.0 的反馈」→ 优化对应模板</li>
 *   <li>运营查看通道维度评分 → 决定是否切换供应商</li>
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有反馈按 {@code tenantId} 隔离，跨租户反馈不可见。
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>写接口（submit）启用 {@link Idempotent} 5s 防重（同一用户对同一消息多次反馈幂等）</li>
 *   <li>写接口（submit）启用 {@link RateLimit} 50 QPS 限流，防止恶意刷评分</li>
 *   <li>写接口（submit）启用 {@link Audit} 审计日志（异步持久化）</li>
 *   <li>读接口（rating / page / shouldReduceFreq）需校验 {@link PermissionCodes#MESSAGE_LOG_VIEW} 权限码</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.core.MessageFeedbackService 消息反馈服务
 * @see com.njydsz.message.domain.entity.config.MsgFeedback 反馈实体
 */
@Tag(name = "消息反馈", description = "消息质量评分与用户反馈")
@RestController
@RequestMapping("/api/v1/message/feedback")
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
    public PageResponse<List<MsgFeedbackVO>> pageFeedback(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size,
                                                      @RequestParam(required = false) String channel,
                                                      @RequestParam(required = false) String userId) {
        Page<MsgFeedback> result = messageFeedbackService.pageFeedback(page, size, channel, userId);
        return PageResponse.success(
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                MessageConverter.INSTANT.feedbackListToVO(result.getRecords()));
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
