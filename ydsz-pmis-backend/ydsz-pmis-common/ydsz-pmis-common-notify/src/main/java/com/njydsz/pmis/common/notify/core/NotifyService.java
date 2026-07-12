package com.njydsz.pmis.common.notify.core;

import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import java.util.List;
import java.util.concurrent.CompletableFuture;
/**
 * 统一消息通知服务接口
 * <p>支持邮件、短信、站内信、企业微信、钉钉、飞书等全渠道消息发送</p>
 *
 * @author ydsz-pmis-team
 * 
 * @since 1.0.0
 * @since 1.0.0
 */
public interface NotifyService {

	/**
	 * 发送通知消息到指定渠道
	 *
	 * @param channel  通知渠道
	 * @param receiver 接收者标识（如邮箱地址、手机号、用户ID等）
	 * @param title    消息标题
	 * @param content  消息内容
	 * @return 发送结果
	 */
	NotifySendResult send(NotifyChannel channel, String receiver, String title, String content);

	/**
	 * 发送通知消息到指定渠道（带模板参数）
	 *
	 * @param channel        通知渠道
	 * @param receiver       接收者标识
	 * @param templateCode   模板编码
	 * @param templateParams 模板参数
	 * @return 发送结果
	 */
	NotifySendResult sendTemplate(NotifyChannel channel, String receiver, String templateCode, Object templateParams);

	/**
	 * 批量发送通知消息到多个接收者（串行模式）
	 *
	 * @param channel   通知渠道
	 * @param receivers 接收者标识列表
	 * @param title     消息标题
	 * @param content   消息内容
	 * @return 发送结果
	 */
	NotifySendResult batchSend(NotifyChannel channel, List<String> receivers, String title, String content);

	/**
	 * 并行批量发送通知消息到多个接收者
	 *
	 * <p>使用虚拟线程池并行发送，显著提升大批量发送场景的吞吐量。
	 * 所有接收者的发送任务并行执行，总耗时约等于最慢的单条发送耗时。
	 *
	 * @param channel   通知渠道
	 * @param receivers 接收者标识列表
	 * @param title     消息标题
	 * @param content   消息内容
	 * @return 异步发送结果，包含成功/失败统计
	 */
	CompletableFuture<NotifySendResult> parallelBatchSend(NotifyChannel channel, List<String> receivers, String title, String content);
}
