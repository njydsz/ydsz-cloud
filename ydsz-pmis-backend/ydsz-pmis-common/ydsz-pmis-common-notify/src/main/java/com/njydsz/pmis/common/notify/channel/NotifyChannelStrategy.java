package com.njydsz.pmis.common.notify.channel;

import java.util.List;

import com.njydsz.pmis.common.notify.core.NotifySendResult;
import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import com.njydsz.pmis.common.notify.template.TemplateEngine;

/**
 * 通知渠道策略接口。
 *
 * <p>每种通知渠道实现该接口，通过策略模式实现渠道自动分发。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
public interface NotifyChannelStrategy {

    /**
     * 获取支持的渠道类型。
     *
     * @return 通知渠道枚举
     */
    NotifyChannel getChannel();

    /**
     * 发送单条通知。
     *
     * @param receiver 接收者
     * @param title    标题
     * @param content  内容
     * @return 发送结果
     */
    NotifySendResult send(String receiver, String title, String content);

    /**
     * 使用模板发送通知。
     *
     * @param receiver     接收者
     * @param templateCode 模板编码
     * @param templateParams 模板参数
     * @return 发送结果
     */
    NotifySendResult sendTemplate(String receiver, String templateCode, Object templateParams);

    /**
     * 批量发送通知。
     *
     * @param receivers 接收者列表
     * @param title     标题
     * @param content   内容
     * @return 发送结果
     */
    NotifySendResult batchSend(List<String> receivers, String title, String content);

    /**
     * 是否启用该渠道。
     *
     * @return 是否启用
     */
    boolean isEnabled();

    /**
     * 设置模板引擎（可选）。
     *
     * <p>通过此方法注入 {@link TemplateEngine} 实例后，
     * {@link #sendTemplate} 可使用新模板引擎按模板 ID 渲染内容。
     * 未设置时，各实现可使用默认的 {@link TemplateEngine}。
     *
     * @param templateEngine 模板引擎实例
     * @see TemplateEngine
     */
    default void setTemplateEngine(TemplateEngine templateEngine) {
        // 默认空实现，按需覆盖
    }
}
