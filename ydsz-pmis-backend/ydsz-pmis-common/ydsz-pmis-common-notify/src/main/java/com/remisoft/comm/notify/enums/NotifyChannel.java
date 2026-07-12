package com.remisoft.comm.notify.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息通知渠道枚举
 *
 * @author ydsz-pmis-team
 * 
 * @since 1.0.0
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum NotifyChannel {

	EMAIL(1, "邮件"),

	/** 短信渠道。当前版本暂未实现短信渠道，将在后续版本补充。 */
	SMS(2, "短信"),
	WECOM(3, "企业微信"),
	DINGTALK(4, "钉钉"),
	FEISHU(5, "飞书"),
	INSITE(6, "站内信");

	private final int code;

	private final String name;

	public static NotifyChannel fromCode(int code) {
		for (NotifyChannel channel : values()) {
			if (channel.code == code) {
				return channel;
			}
		}
		throw new IllegalArgumentException("未知通知渠道: " + code);
	}
}
