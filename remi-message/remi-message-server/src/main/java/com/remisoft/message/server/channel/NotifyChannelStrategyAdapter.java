package com.remisoft.message.server.channel;

import java.util.List;
import java.util.Map;

import com.remisoft.common.feign.MessageRequest;
import com.remisoft.common.feign.MessageResult;
import com.remisoft.common.json.type.JsonType;
import com.remisoft.common.notify.channel.NotifyChannelStrategy;
import com.remisoft.common.notify.core.NotifySendResult;
import com.remisoft.common.notify.enums.NotifyChannel;
import com.remisoft.common.notify.template.TemplateEngine;
import com.remisoft.common.json.JsonMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 通道策略适配器：将消息服务的 {@link MessageChannel} 适配为
 * common-notify 的 {@link NotifyChannelStrategy}。
 *
 * <p>通过此适配器，消息服务的所有通道实现（EmailChannel / SmsChannel /
 * DingTalkChannel / WeComAppChannel / FeishuChannel / InAppChannel）
 * 自动注册为 {@link NotifyChannelStrategy} Bean，供 common-notify 的
 * {@code NotifyService} 统一调用，消除两套平行通道体系。
 *
 * <p>仅支持在 {@link NotifyChannel} 枚举中有对应值的通道类型，
 * PUSH / WEBHOOK 等无对应枚举值的通道不会被适配。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class NotifyChannelStrategyAdapter implements NotifyChannelStrategy {

    private final MessageChannel delegate;
    private final NotifyChannel notifyChannel;
    private volatile boolean enabled = true;

    /**
     * 构造适配器。
     *
     * @param delegate      被适配的消息通道
     * @param notifyChannel common-notify 渠道枚举
     */
    public NotifyChannelStrategyAdapter(MessageChannel delegate, NotifyChannel notifyChannel) {
        this.delegate = delegate;
        this.notifyChannel = notifyChannel;
    }

    @Override
    public NotifyChannel getChannel() {
        return notifyChannel;
    }

    @Override
    public NotifySendResult send(String receiver, String title, String content) {
        if (!enabled) {
            return NotifySendResult.failure("通道已禁用", notifyChannel.getName());
        }
        MessageRequest request = new MessageRequest();
        request.setChannel(delegate.channelType());
        request.setReceiver(receiver);
        request.setSubject(title);
        request.setContent(content);
        try {
            MessageResult result = delegate.send(request);
            if (result.isSuccess()) {
                return NotifySendResult.success(result.getTraceId(), notifyChannel.getName());
            }
            return NotifySendResult.failure(result.getErrorMessage(), notifyChannel.getName());
        } catch (Exception e) {
            log.error("[NotifyAdapter] 通道发送异常: channel={} receiver={} err={}",
                    notifyChannel.getName(), receiver, e.getMessage());
            return NotifySendResult.failure(e.getMessage(), notifyChannel.getName());
        }
    }

    @Override
    public NotifySendResult sendTemplate(String receiver, String templateCode, Object templateParams) {
        if (!enabled) {
            return NotifySendResult.failure("通道已禁用", notifyChannel.getName());
        }
        MessageRequest request = new MessageRequest();
        request.setChannel(delegate.channelType());
        request.setReceiver(receiver);
        request.setTemplateCode(templateCode);
        if (templateParams instanceof Map<?, ?> map) {
            Map<String, Object> params = JsonMapper.getDefault().convertValue(
                    map, new JsonType<Map<String, Object>>() {});
            request.setParams(params);
        }
        try {
            MessageResult result = delegate.send(request);
            if (result.isSuccess()) {
                return NotifySendResult.success(result.getTraceId(), notifyChannel.getName());
            }
            return NotifySendResult.failure(result.getErrorMessage(), notifyChannel.getName());
        } catch (Exception e) {
            log.error("[NotifyAdapter] 模板发送异常: channel={} template={} err={}",
                    notifyChannel.getName(), templateCode, e.getMessage());
            return NotifySendResult.failure(e.getMessage(), notifyChannel.getName());
        }
    }

    @Override
    public NotifySendResult batchSend(List<String> receivers, String title, String content) {
        if (!enabled) {
            return NotifySendResult.failure("通道已禁用", notifyChannel.getName());
        }
        int success = 0;
        int failure = 0;
        for (String receiver : receivers) {
            NotifySendResult result = send(receiver, title, content);
            if (result.isSuccess()) {
                success++;
            } else {
                failure++;
            }
        }
        if (failure == 0) {
            return NotifySendResult.success("batch:" + success, notifyChannel.getName());
        }
        return NotifySendResult.failure(
                "批量发送部分失败: 成功" + success + "/" + receivers.size(),
                notifyChannel.getName());
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置通道启用状态。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void setTemplateEngine(TemplateEngine templateEngine) {
        // 适配器委托给 MessageChannel，模板渲染由消息服务内部处理，无需存储
    }
}
