package com.njydsz.message.web.controller.config;

import org.springframework.web.bind.annotation.GetMapping;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.message.domain.converter.MessageConverter;
import com.njydsz.message.domain.dto.config.UnsubscribeQueryDTO;
import com.njydsz.message.domain.entity.config.MsgSubscription;
import com.njydsz.message.domain.vo.MsgSubscriptionVO;
import com.njydsz.message.server.service.config.UnsubscribeService;
import com.njydsz.message.server.token.UnsubscribeTokenPayload;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;

/**
 * 退订中心（Unsubscribe）Controller。
 *
 * <p>提供<b>基于 HMAC 签名 token 的一键退订</b>能力（P1-5），对标 RFC 8058 List-Unsubscribe-Post。
 * 适用于邮件 / 短信等无登录态场景的退订：用户在邮件底部点击退订链接即可完成退订，
 * 无需登录管理后台。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/unsubscribe/**}
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li><b>token 一键退订</b>：{@code POST /oneClick} — 无需登录态，token 校验通过后立即执行退订</li>
 *   <li><b>token 预览</b>：{@code GET /preview} — 预览 token 内容（供退订确认页渲染，不执行退订）</li>
 *   <li><b>退订记录查询</b>：{@code GET /page} — 管理后台分页查询已退订记录</li>
 *   <li><b>恢复订阅</b>：{@code POST /resubscribe} — 管理后台或用户自助恢复订阅</li>
 * </ul>
 *
 * <p><b>token 格式：</b>由 {@link com.njydsz.message.server.token.UnsubscribeTokenService} 生成，
 * 含 HMAC 签名（防伪造）+ payload（userId + topicCode + channel + expireAt）。
 * 典型示例（URL Safe Base64）：{@code eyJ1IjoiMTAwMSIsInQiOiJPUkRfVVBEQVRFIiwiYyI6IkVNQUlMIiwiZSI6MTcwMDAwMDAwMH0=.signature}.
 *
 * <p><b>安全特性：</b>
 * <ul>
 *   <li>token 含 HMAC-SHA256 签名，密钥从 Nacos / 配置中心加载，防止伪造</li>
 *   <li>token 含 {@code expireAt}，默认有效期 30 天，过期后失效</li>
 *   <li>token 一次性使用（使用后从 Redis 标记失效，防止 token 重放）</li>
 *   <li>写接口（oneClick / resubscribe）启用 {@link Idempotent} 5s 防重</li>
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流（防爆破）</li>
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化），便于合规审计</li>
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#MESSAGE_UNSUBSCRIBE_ACT} 权限码</li>
 * </ul>
 *
 * <p><b>典型场景：</b>
 * <ul>
 *   <li>邮件退订：用户点击邮件 footer 中的「取消订阅」链接 → 跳转退订确认页 → 确认后调用 {@code /oneClick}</li>
 *   <li>短信退订：短信中插入退订短链 → 用户点击 → 调用 {@code /oneClick}</li>
 *   <li>管理后台恢复：客服收到用户反馈后通过 {@code /resubscribe} 恢复订阅</li>
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有退订按 {@code tenantId} 隔离，跨租户退订不可见。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.config.UnsubscribeService 退订服务
 * @see com.njydsz.message.server.token.UnsubscribeTokenPayload 退订 token 载荷
 */
@Slf4j
@Tag(name = "退订中心", description = "token 一键退订与退订管理")
@RestController
@RequestMapping("/api/v1/message/unsubscribe")
@RequiredArgsConstructor
public class UnsubscribeController {

    /** 退订服务 */
    private final UnsubscribeService unsubscribeService;

    /**
     * token 一键退订（无需登录态）。
     *
     * <p>对应邮件 footer 中的退订链接 / SMS 短链。token 校验通过后立即执行退订，
     * 幂等：重复点击不会报错。
     *
     * @param token 退订 token
     * @return 退订后的订阅记录
     */
    @Operation(summary = "token 一键退订")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_UNSUBSCRIBE_ACT)
    @Idempotent(key = "ydsz:message:UnsubscribeController:oneClick:lock", ttlSeconds = 5)
    @Audit(module = "退订管理", type = AuditType.OPERATION, action = AuditAction.DELETE, content = "'oneClick'")
    @RateLimit(resource = "message.unsubscribe.oneClick", threshold = 50)
    @PostMapping("/oneClick")
    public BaseResponse<MsgSubscriptionVO> oneClick(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            return BaseResponse.error(BaseResultCode.BAD_REQUEST, "退订 token 不能为空");
        }
        return BaseResponse.success(MessageConverter.INSTANT.entityToVO(unsubscribeService.unsubscribeByToken(token)));
    }

    /**
     * 预览 token 内容（不执行退订）。
     *
     * <p>供退订确认页渲染：先展示 "您即将退订 [主题] 的 [通道] 通知"，
     * 用户确认后再调用 {@code /one-click} 执行退订。
     *
     * @param token 退订 token
     * @return token 载荷
     */
    @Operation(summary = "预览退订 token")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_UNSUBSCRIBE_ACT)
    @GetMapping("/preview")
    public BaseResponse<UnsubscribeTokenPayload> preview(@RequestParam String token) {
        if (token == null || token.isBlank()) {
            return BaseResponse.error(BaseResultCode.BAD_REQUEST, "退订 token 不能为空");
        }
        return BaseResponse.success(unsubscribeService.previewToken(token));
    }

    /**
     * 分页查询已退订记录（管理后台）。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询已退订记录")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_UNSUBSCRIBE_VIEW)
    @GetMapping("/page")
    public BaseResponse<PageResponse<MsgSubscriptionVO>> page(UnsubscribeQueryDTO query) {
        PageResponse<MsgSubscription> page = unsubscribeService.pageUnsubscribed(query);
        PageResponse<MsgSubscriptionVO> voPage = PageResponse.success(
                page.getTotal(), page.getPageNum(), page.getPageSize(),
                MessageConverter.INSTANT.subscriptionListToVO(page.getRecords()));
        return BaseResponse.success(voPage);
    }

    /**
     * 恢复订阅（管理后台 / 用户自助）。
     *
     * @param userId    用户 ID
     * @param topicCode 主题编码
     * @param channel   通道
     * @return 操作结果
     */
    @Operation(summary = "恢复订阅")
    @AuthApiPermission(apiCodes = PermissionCodes.MESSAGE_UNSUBSCRIBE_ACT)
    @Idempotent(key = "ydsz:message:UnsubscribeController:resubscribe:lock", ttlSeconds = 5)
    @Audit(module = "退订管理", type = AuditType.OPERATION, action = AuditAction.CREATE, content = "'resubscribe'")
    @RateLimit(resource = "message.unsubscribe.resubscribe", threshold = 50)
    @PostMapping("/resubscribe")
    public BaseResponse<Void> resubscribe(@RequestParam String userId,
                                    @RequestParam String topicCode,
                                    @RequestParam String channel) {
        if (userId == null || userId.isBlank()
                || topicCode == null || topicCode.isBlank()
                || channel == null || channel.isBlank()) {
            return BaseResponse.error(BaseResultCode.BAD_REQUEST, "用户 ID、主题编码与通道不能为空");
        }
        unsubscribeService.resubscribe(userId, topicCode, channel);
        return BaseResponse.success();
    }
}
