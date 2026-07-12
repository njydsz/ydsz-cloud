package com.njydsz.pmis.common.notify.core;

/**
 * 消息发送结果统一封装
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
public interface NotifySendResult {

	/**
	 * 是否发送成�?
	 */
	boolean isSuccess();

	/**
	 * 获取消息ID
	 */
	String getMessageId();

	/**
	 * 获取错误信息
	 */
	String getErrorMessage();

	/**
	 * 获取发送渠�?
	 */
	String getChannel();

	/**
	 * 获取发送时间戳
	 */
	long getSendTime();

	/**
	 * 创建成功结果
	 */
	static NotifySendResult success(String messageId, String channel) {
		return new DefaultNotifyResult(true, messageId, null, channel, System.currentTimeMillis());
	}

	/**
	 * 创建失败结果
	 */
	static NotifySendResult failure(String errorMessage, String channel) {
		return new DefaultNotifyResult(false, null, errorMessage, channel, System.currentTimeMillis());
	}
}
