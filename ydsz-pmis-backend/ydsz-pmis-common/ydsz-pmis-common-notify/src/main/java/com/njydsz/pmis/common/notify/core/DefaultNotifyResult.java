package com.njydsz.pmis.common.notify.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 默认的消息发送结果实现
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DefaultNotifyResult implements NotifySendResult {

	private boolean success;

	private String messageId;

	private String errorMessage;

	private String channel;

	private long sendTime;

	@Override
	public boolean isSuccess() {
		return success;
	}
}
