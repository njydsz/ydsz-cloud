/**
 * 消息通道抽象层：定义统一的消息发送接口与通道路由契约。
 *
 * <p>本包是 PMIS 消息中心（Notification Center）的核心抽象层，向上对
 * {@code MessageService} 提供"按通道类型路由"的能力，向下通过
 * {@code SPI/Spring 注入}加载 {@code channel.impl} 子包下的具体实现，
 * 完成对短信、邮件、推送、企微、钉钉、Webhook 等异构通道的统一封装。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.system.channel.MessageChannel} - 消息通道统一接口
 *       （{@code channelType()} 返回通道标识，{@code send(MessageRequest)} 执行下发）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>开闭原则</b>：新增通道仅需实现 {@code MessageChannel} 并标注 {@code @Component}，
 *       无需修改任何上游调用方</li>
 *   <li><b>通道类型即路由键</b>：{@code channelType()} 返回大写字符串（SMS/EMAIL/PUSH/DINGTALK 等），
 *       与 {@code MessageRequest.channel} 严格匹配</li>
 *   <li><b>结果统一</b>：发送结果使用 {@code common.feign.MessageResult} 抽象，
 *       上层无需关心供应商侧差异</li>
 *   <li><b>容错可降级</b>：实现类内部自行处理通道不可用场景（如 EMAIL 未配置时返回 fail 而非抛异常）</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止在 Controller/Listener 中直接 new 通道实现，必须通过 {@code MessageService} 路由</li>
 *   <li>新通道实现需放置在 {@code channel.impl} 子包中，遵循"实现即注入"约定</li>
 *   <li>通道标识字符串须为大写，与 {@code MessageRequest.channel} 完全一致</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.channel;
