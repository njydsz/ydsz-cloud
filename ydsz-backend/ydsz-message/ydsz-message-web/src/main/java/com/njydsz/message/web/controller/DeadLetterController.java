package com.njydsz.message.web.controller.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.dto.core.MessageLogQueryDTO;
import com.njydsz.message.domain.entity.core.MsgLogDO;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.server.service.core.MessageLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 死信管理 Controller（P1-4）。
 *
 * <p>提供死信分页查询与手动重发能力：
 * <ul>
 *   <li>{@code GET /page}：分页查询死信（强制 status=DEAD）</li>
 *   <li>{@code POST /{logId}/resend}：手动重发指定死信,重置重试计数并立即重新投递</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Tag(name = "死信管理", description = "死信查询与手动重发")
@RestController
@RequestMapping("/message/deadLetter")
@RequiredArgsConstructor
public class DeadLetterController {

    /** 消息日志服务 */
    private final MessageLogService messageLogService;

    /**
     * 分页查询死信列表。
     *
     * <p>强制 {@code status=DEAD},支持按通道 / 业务类型 / 接收人 / 租户等过滤。
     *
     * @param query 查询参数（status 字段被忽略,固定为 DEAD）
     * @return 死信分页
     */
    @Operation(summary = "分页查询死信列表")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_DEAD_LETTER_VIEW)
    @GetMapping("/page")
    public BaseResponse<Page<MsgLogDO>> page(MessageLogQueryDTO query) {
        if (query == null) {
            query = new MessageLogQueryDTO();
        }
        query.setStatus(MessageStatusEnum.DEAD.name());
        return BaseResponse.success(messageLogService.page(query));
    }

    /**
     * 手动重发死信。
     *
     * <p>仅 DEAD 状态可重发。重置 retryCount / errorMessage / nextRetryAt 后立即重新投递,
     * 投递成功 → SUCCESS,投递失败 → RETRY（进入正常重试调度）。
     *
     * @param logId 死信日志 ID
     * @return 操作结果
     */
    @Operation(summary = "手动重发死信")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_DEAD_LETTER_RESEND)
    @Idempotent(key = "deadLetter:resend", ttlSeconds = 5, message = "请勿重复提交")
    @PostMapping("/{logId}/resend")
    public BaseResponse<Void> resend(@PathVariable String logId) {
        if (logId == null || logId.isBlank()) {
            return BaseResponse.error(BaseResultCode.BAD_REQUEST, "死信日志 ID 不能为空");
        }
        messageLogService.resendDead(logId);
        return BaseResponse.success();
    }
}
