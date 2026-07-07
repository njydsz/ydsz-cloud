/**
 * 消息通道实现层：提供 SMS/EMAIL/PUSH/DINGTALK/WECHAT_WORK/WEBHOOK 等具体通道实现。
 *
 * <p>本包是 {@code system.channel} 抽象接口的具体落地，每个实现类均以 {@code @Component}
 * 形式被 Spring 容器管理，由 {@code MessageService} 按 {@code channelType()} 自动发现并路由。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code EmailChannel} - 邮件通道，基于 Spring {@code JavaMailSender}，支持纯文本与 HTML 两种格式自动识别</li>
 *   <li>{@code MockSmsChannel} - 短信通道 Mock 实现（开发环境使用，生产替换为阿里云/腾讯云 SDK）</li>
 *   <li>{@code MockPushChannel} - 移动推送通道 Mock 实现（开发环境使用，生产替换为极光/友盟 SDK）</li>
 *   <li>{@code DingTalkChannel} - 钉钉机器人通道，支持群消息 @ 指定人</li>
 *   <li>{@code WechatWorkChannel} - 企业微信应用通道，支持应用消息推送</li>
 *   <li>{@code WebhookChannel} - 通用 Webhook 通道，向第三方系统 HTTP POST JSON 负载</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>实现可插拔</b>：每个实现独立配置（如 {@code JavaMailSender} 可选注入），缺失依赖时安全降级</li>
 *   <li><b>日志可追踪</b>：发送成功/失败均记录 INFO/ERROR 日志，并使用雪花算法生成 {@code traceId}
 *       便于上下游对账</li>
 *   <li><b>异常不外抛</b>：所有异常在通道内部捕获并转为 {@code MessageResult.fail}，
 *       避免影响 MQ 消费链路（消费侧已具备重试 + DLQ 兜底）</li>
 *   <li><b>Mock 与真实并存</b>：开发/测试环境使用 Mock 实现，生产通过配置切换为真实供应商 SDK</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新通道实现必须实现 {@code MessageChannel} 接口并标注 {@code @Component}</li>
 *   <li>通道标识（{@code channelType()}）必须全局唯一，禁止与已有通道重复</li>
 *   <li>禁止在实现中调用其他通道，避免循环依赖</li>
 *   <li>外部依赖（HTTP/SDK）须设置超时与重试策略，遵循"快速失败"原则</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.channel.impl;
