/**
 * 消息核心服务层，负责消息发送管道、渠道路由、模板渲染、批量处理、重试调度、SSE 推送等.
 *
 * <p>本模块是消息子系统的核心服务实现层，构建了从消息生产、模板渲染、渠道路由、发送执行到回执收集
 * 的完整异步消息链路。通过责任链模式（{@code SendPipeline}）组织去重、频控、用户偏好、渠道路由、
 * 节流等处理节点，通过 {@code ChannelRouter} 与 {@code ChannelScoreCalculator} 实现多通道智能选路，
 * 并通过 {@code RetryScheduler} / {@code RetryScanner} 保障消息最终送达。</p>
 *
 * <p>核心能力：</p>
 * <ul>
 *   <li>消息发送管道：{@code SendPipeline} / {@code SendPipelineFacade} 组装 {@code SendHandler} 链，
 *       依次执行去重、频控、用户偏好校验、渠道路由、节流等处理</li>
 *   <li>渠道路由：{@code ChannelRouter} 基于 {@code ChannelScoreCalculator} 评分选择最优通道，
 *       支持邮件、短信、App 推送、钉钉、飞书、企微、支付宝小程序、微信小程序等多通道</li>
 *   <li>模板渲染：{@code MessageRenderService} 通过 {@code TemplateEngine} 接口（FreeMarker 实现）
 *       渲染变量模板，支持富媒体卡片与多语言</li>
 *   <li>批量处理：{@code BatchServiceImpl} 承接批量发送任务，通过 {@code BatchProgressPusher} 推送进度</li>
 *   <li>重试调度：{@code RetryScanner} 扫描超时未确认消息触发重试；{@code ScheduledMessageScanner} 处理延时消息</li>
 *   <li>实时推送：{@code SseEmitterService} 管理 SSE 长连接；{@code RealtimePushService} 推送在线通知</li>
 *   <li>消息撤回：{@code RecallService} 与 {@code RecallChannelRouter} 支持多通道撤回</li>
 * </ul>
 *
 * <h3>关键组件</h3>
 *
 * <ul>
 *   <li>{@code MessageServiceImpl} -- 消息服务主入口，对外统一暴露发送、查询、撤回等能力</li>
 *   <li>{@code MessageSendService} / {@code MessageSendTxService} -- 发送核心服务（含事务边界）</li>
 *   <li>{@code MessageRenderService} -- 模板渲染服务</li>
 *   <li>{@code ChannelRouter} / {@code MessageChannel} -- 渠道路由与通道接口</li>
 *   <li>{@code MessageConsumer} / {@code BatchMessageConsumer} -- MQ 消费者</li>
 *   <li>{@code RetryScanner} / {@code ScheduledMessageScanner} -- 重试与定时扫描器</li>
 *   <li>{@code BatchServiceImpl} -- 批量发送服务</li>
 *   <li>{@code SseEmitterService} / {@code RealtimePushService} -- SSE 与实时推送</li>
 *   <li>{@code MessageArchiveServiceImpl} / {@code MessageExpiryCleaner} -- 归档与过期清理</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.message.server;
