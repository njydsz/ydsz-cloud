package com.njydsz.pmis.cronjob.core.alert;

import com.njydsz.pmis.cronjob.entity.job.JobAlertRuleDO;

import java.util.List;

/**
 * 告警通知器接口（P5 告警 + 监控）。
 *
 * <p>每个通道实现该接口，由 {@link AlertDispatcher} 在派发告警时按通道调用。
 * 实现类需通过 {@code @Component} 注册为 Spring Bean，并配合
 * {@code @ConditionalOnMissingBean} 实现可替换性。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AlertNotifier {

    /**
     * 获取该通知器支持的通道。
     *
     * @return 通道枚举值
     */
    AlertChannel supportedChannel();

    /**
     * 派发告警通知。
     *
     * @param context   告警上下文
     * @param rule      匹配到的告警规则
     * @param receivers 该通道的接收人列表（按通道语义解析，如 EMAIL 为邮箱列表）
     * @throws AlertSendException 通知发送失败时抛出，由上层捕获并记录到告警日志
     */
    void notify(AlertContext context, JobAlertRuleDO rule, List<String> receivers) throws AlertSendException;
}
