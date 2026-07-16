package com.njydsz.pmis.common.notify.preference;

import java.time.LocalTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import com.njydsz.pmis.common.notify.enums.NotifyChannel;
import com.njydsz.pmis.common.notify.enums.NotifyType;

/**
 * 用户通知偏好配置（P3-12）
 *
 * <p>封装用户对各通知渠道和通知类型的偏好设置，支持：
 * <ul>
 *   <li>渠道开关（如关闭短信通知但保留邮件通知）</li>
 *   <li>通知类型开关（如关闭审批提醒但保留告警通知）</li>
 *   <li>免打扰时段配置</li>
 *   <li>聚合策略配置（如多条消息聚合为一条摘要）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class NotifyPreference {

	/** 用户ID */
	private String userId;

	/** 用户语言偏好（如 zh_CN、en_US），用于国际化消息解析 */
	private String language;

	/** 启用的渠道集合 */
	private Set<NotifyChannel> enabledChannels = EnumSet.allOf(NotifyChannel.class);

	/** 启用的通知类型集合 */
	private Set<NotifyType> enabledTypes = EnumSet.allOf(NotifyType.class);

	/** 免打扰开始时间（小时，0-23） */
	private int dndStartHour = -1;

	/** 免打扰结束时间（小时，0-23） */
	private int dndEndHour = -1;

	/** 各渠道-类型的覆盖配置（key=channel, value=允许的type集合） */
	private Map<NotifyChannel, Set<NotifyType>> channelTypeOverrides = new EnumMap<>(NotifyChannel.class);

	/** 是否聚合通知 */
	private boolean aggregateEnabled = false;

	/** 聚合时间窗口（分钟） */
	private int aggregateWindowMinutes = 30;

	public NotifyPreference() {
	}

	public NotifyPreference(String userId) {
		this.userId = userId;
	}

	/**
	 * 判断指定渠道是否启用
	 */
	public boolean isChannelEnabled(NotifyChannel channel) {
		return enabledChannels.contains(channel);
	}

	/**
	 * 判断指定通知类型是否启用
	 */
	public boolean isTypeEnabled(NotifyType type) {
		return enabledTypes.contains(type);
	}

	/**
	 * 判断指定渠道+类型组合是否允许发送
	 */
	public boolean isAllowed(NotifyChannel channel, NotifyType type) {
		if (!isChannelEnabled(channel) || !isTypeEnabled(type)) {
			return false;
		}
		Set<NotifyType> overrides = channelTypeOverrides.get(channel);
		if (overrides != null && !overrides.isEmpty()) {
			return overrides.contains(type);
		}
		return true;
	}

	/**
	 * 判断当前时间是否在免打扰时段
	 */
	public boolean isDoNotDisturb() {
		if (dndStartHour < 0 || dndEndHour < 0) {
			return false;
		}
		int hour = LocalTime.now().getHour();
		if (dndStartHour <= dndEndHour) {
			return hour >= dndStartHour && hour < dndEndHour;
		} else {
			// 跨天（如 22:00 - 08:00）
			return hour >= dndStartHour || hour < dndEndHour;
		}
	}

	/**
	 * 启用/禁用渠道
	 */
	public void setChannelEnabled(NotifyChannel channel, boolean enabled) {
		if (enabled) {
			enabledChannels.add(channel);
		} else {
			enabledChannels.remove(channel);
		}
	}

	/**
	 * 启用/禁用通知类型
	 */
	public void setTypeEnabled(NotifyType type, boolean enabled) {
		if (enabled) {
			enabledTypes.add(type);
		} else {
			enabledTypes.remove(type);
		}
	}

	// ===== Getters / Setters =====

	public String getUserId() { return userId; }
	public void setUserId(String userId) { this.userId = userId; }
	public Set<NotifyChannel> getEnabledChannels() {
		return Collections.unmodifiableSet(enabledChannels);
	}
	public void setEnabledChannels(Set<NotifyChannel> enabledChannels) {
		this.enabledChannels = enabledChannels != null ? EnumSet.copyOf(enabledChannels) : EnumSet.noneOf(NotifyChannel.class);
	}
	public Set<NotifyType> getEnabledTypes() {
		return Collections.unmodifiableSet(enabledTypes);
	}
	public void setEnabledTypes(Set<NotifyType> enabledTypes) {
		this.enabledTypes = enabledTypes != null ? EnumSet.copyOf(enabledTypes) : EnumSet.noneOf(NotifyType.class);
	}
	public int getDndStartHour() { return dndStartHour; }
	public void setDndStartHour(int dndStartHour) { this.dndStartHour = dndStartHour; }
	public int getDndEndHour() { return dndEndHour; }
	public void setDndEndHour(int dndEndHour) { this.dndEndHour = dndEndHour; }
	public Map<NotifyChannel, Set<NotifyType>> getChannelTypeOverrides() { return channelTypeOverrides; }
	public void setChannelTypeOverrides(Map<NotifyChannel, Set<NotifyType>> channelTypeOverrides) {
		this.channelTypeOverrides = channelTypeOverrides;
	}
	public boolean isAggregateEnabled() { return aggregateEnabled; }
	public void setAggregateEnabled(boolean aggregateEnabled) { this.aggregateEnabled = aggregateEnabled; }
	public int getAggregateWindowMinutes() { return aggregateWindowMinutes; }
	public void setAggregateWindowMinutes(int aggregateWindowMinutes) { this.aggregateWindowMinutes = aggregateWindowMinutes; }
	public String getLanguage() { return language; }
	public void setLanguage(String language) { this.language = language; }
}
