package com.njydsz.pmis.execution.feign;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * MessageServiceClient 降级工厂
 *
 * <p>消息中心不可用时, 返回成功占位 + 记录 WARN 日志;
 * 避免预警 / 工单通知的失败向上传播到业务主流程.
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class MessageServiceClientFallback implements FallbackFactory<MessageServiceClient> {

    /**
     * 创建降级客户端实例
     *
     * @param cause 触发降级的异常
     * @return 降级后的 MessageServiceClient 实例
     */
    @Override
    public MessageServiceClient create(Throwable cause) {
        log.warn("[MessageClientFallback] 消息中心降级: {}", cause == null ? "?" : cause.getMessage());
        return new MessageServiceClient() {
            @Override
            public Result<MessageResult> send(MessageRequest request) {
                if (request == null) {
                    return Result.ok(MessageResult.fail("UNKNOWN", "降级: 请求为空"));
                }
                log.warn("[MessageClientFallback] 降级 send: bizType={} bizId={} channel={} template={}",
                        request.getBizType(), request.getBizId(),
                        request.getChannel(), request.getTemplateCode());
                MessageResult r = MessageResult.fail(request.getChannel(),
                        "消息中心暂不可用: " + (cause == null ? "unknown" : cause.getMessage()));
                return Result.ok(r);
            }
        };
    }
}
