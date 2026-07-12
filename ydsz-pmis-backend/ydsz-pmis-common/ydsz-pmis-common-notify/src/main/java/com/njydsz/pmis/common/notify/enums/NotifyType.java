package com.njydsz.pmis.common.notify.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通知消息类型枚举
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum NotifyType {

	TEXT(1, "纯文本"),
	HTML(2, "富文本"),
	TEMPLATE(3, "模板消息"),
	MARKDOWN(4, "Markdown"),
	CARD(5, "卡片消息");

	private final int code;

	private final String name;
}
